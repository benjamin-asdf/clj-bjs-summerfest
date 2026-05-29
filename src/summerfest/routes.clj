(ns summerfest.routes
  (:require [summerfest.db :as db]
            [summerfest.views :as views]
            [summerfest.sse :as sse]
            [summerfest.sheets :as sheets]
            [summerfest.invites :as invites]
            [summerfest.i18n :as i18n]
            [summerfest.config :refer [u]]
            [reitit.ring :as reitit]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [org.httpkit.server :as hk])
  (:import [java.util UUID]))

(def ^:private upload-dir
  (or (System/getenv "UPLOAD_DIR") "uploads"))

(def ^:private max-photos
  (parse-long (or (System/getenv "MAX_PHOTOS") "1000")))

(def ^:private thumb-max-dim 480)

(defn- thumb-rel-path
  "Path of the JPEG thumbnail for an upload, relative to upload-dir."
  [filename]
  (str "thumbs/" (clojure.string/replace filename #"\.[^.]+$" ".jpg")))

(defn- generate-thumbnail!
  "Read src-file, write a JPEG thumbnail at thumb-file scaled so the longest
   side is at most max-dim. Returns true on success, false if src couldn't be
   decoded."
  [src-file thumb-file max-dim]
  (if-let [src (javax.imageio.ImageIO/read src-file)]
    (let [w (.getWidth src)
          h (.getHeight src)
          scale (min 1.0 (/ (double max-dim) (max w h)))
          tw (max 1 (int (* w scale)))
          th (max 1 (int (* h scale)))
          dst (java.awt.image.BufferedImage. tw th java.awt.image.BufferedImage/TYPE_INT_RGB)
          g (.createGraphics dst)]
      (try
        (.setRenderingHint g
                           java.awt.RenderingHints/KEY_INTERPOLATION
                           java.awt.RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.setRenderingHint g
                           java.awt.RenderingHints/KEY_RENDERING
                           java.awt.RenderingHints/VALUE_RENDER_QUALITY)
        (.drawImage g src 0 0 tw th nil)
        (finally (.dispose g)))
      (io/make-parents thumb-file)
      (javax.imageio.ImageIO/write dst "jpg" thumb-file)
      true)
    false))

(defn backfill-thumbnails!
  "REPL helper: ensure every photo row has a thumbnail on disk.
   Returns counts: {:ok :skipped :missing-source :failed}."
  []
  (reduce
   (fn [acc {:keys [filename]}]
     (let [src (io/file upload-dir filename)
           thumb (io/file upload-dir (thumb-rel-path filename))]
       (cond
         (.exists thumb) (update acc :skipped inc)
         (not (.exists src)) (update acc :missing-source inc)
         :else
         (try
           (if (generate-thumbnail! src thumb thumb-max-dim)
             (update acc :ok inc)
             (update acc :failed inc))
           (catch Exception e
             (println "thumb fail" filename (.getMessage e))
             (update acc :failed inc))))))
   {:ok 0 :skipped 0 :missing-source 0 :failed 0}
   (db/get-all-photos)))

;; --- Helpers ---

(defn- get-locale [req]
  (or (get-in req [:session :locale])
      i18n/default-locale))

(defn- parse-locale-param
  "Returns :de/:en if the query param maps to a known locale, else nil."
  [req]
  (when-let [v (get-in req [:query-params "lang"])]
    (#{:de :en} (keyword v))))

(defn- current-user [req]
  (when-let [uid (get-in req [:session :user-id])]
    (db/get-user-by-id uid)))

(defn- ctx [req]
  {:user (:user req)
   :locale (get-locale req)})

(defn- require-auth [handler]
  (fn [req]
    (if-let [user (current-user req)]
      (handler (assoc req :user user))
      (resp/redirect (u "/login")))))

(defn- html-response [body]
  (-> (resp/response body)
      (resp/content-type "text/html; charset=utf-8")))

(defn- parse-json-body [req]
  (try
    (when-let [body (:body req)]
      (let [s (if (string? body) body (slurp body))]
        (when (seq s) (json/parse-string s true))))
    (catch Exception _ nil)))

;; --- Chat SSE Stream ---

(defonce chat-clients (atom {})) ;; {channel {:user-id id, :locale locale}}

(defn- safe-send! [ch payload]
  (try
    (hk/send! ch payload false)
    (catch Exception _
      (swap! chat-clients dissoc ch))))

(defn- chat-initial-payload [user-id locale]
  (let [messages (db/get-recent-messages user-id)
        pinned (db/get-pinned-messages user-id)
        oldest-id (some-> (first messages) :id)
        more? (and oldest-id (db/older-exists? oldest-id))]
    (str (sse/patch-elements (views/load-older-html locale oldest-id more?))
         (sse/patch-elements (views/chat-messages-html locale messages user-id))
         (sse/patch-elements (views/pinned-messages-html locale pinned user-id)))))

(defn- broadcast-new-message! [msg-id]
  (doseq [[ch {:keys [user-id locale]}] @chat-clients]
    (when-let [msg (db/get-message user-id msg-id)]
      (safe-send! ch
                  (str (sse/patch-remove "#chat-empty")
                       (sse/patch-elements
                        (views/single-msg-html locale msg user-id)
                        {:selector "#chat-messages" :mode :append}))))))

(defn- broadcast-message-changed! [msg-id]
  (doseq [[ch {:keys [user-id locale]}] @chat-clients]
    (when-let [msg (db/get-message user-id msg-id)]
      (safe-send! ch (sse/patch-elements (views/single-msg-html locale msg user-id))))))

(defn- broadcast-pin-changed! [msg-id]
  (doseq [[ch {:keys [user-id locale]}] @chat-clients]
    (when-let [msg (db/get-message user-id msg-id)]
      (let [pinned (db/get-pinned-messages user-id)]
        (safe-send! ch
                    (str (sse/patch-elements (views/single-msg-html locale msg user-id))
                         (sse/patch-elements (views/pinned-messages-html locale pinned user-id))))))))

(defn- broadcast-display-name! []
  ;; Display-name change touches every message by that user — fall back to a
  ;; full re-render. Rare event, so the cost is acceptable.
  (doseq [[ch {:keys [user-id locale]}] @chat-clients]
    (safe-send! ch (chat-initial-payload user-id locale))))

(defn chat-stream-handler [req]
  (let [user (:user req)
        locale (get-locale req)]
    (hk/with-channel req ch
      (swap! chat-clients assoc ch {:user-id (:id user) :locale locale})
      (hk/on-close ch (fn [_] (swap! chat-clients dissoc ch)))
      (hk/send! ch {:status 200
                    :headers {"Content-Type" "text/event-stream"
                              "Cache-Control" "no-cache"
                              "X-Accel-Buffering" "no"}
                    :body (chat-initial-payload (:id user) locale)}
                false))))

;; --- Page Handlers ---

(defn login-handler [req]
  (let [lang (parse-locale-param req)
        base-session (cond-> (or (:session req) {})
                       lang (assoc :locale lang))
        page-locale (or lang (get-locale req))]
    (if-let [token (get-in req [:query-params "token"])]
      (if-let [user (db/get-user-by-token token)]
        (-> (resp/redirect (u "/"))
            (assoc :session (assoc base-session :user-id (:id user))))
        (-> (html-response (views/invalid-token-page page-locale))
            (assoc :session base-session)))
      (-> (html-response (views/login-page page-locale))
          (assoc :session base-session)))))

(defn set-locale-handler [req]
  (let [locale (keyword (get-in req [:query-params "locale"] "de"))
        locale (if (#{:de :en} locale) locale :de)
        referer (get-in req [:headers "referer"] (u "/"))]
    (-> (resp/redirect referer)
        (assoc :session (merge (:session req) {:locale locale})))))

(defn- secondary-info
  "When `user` is a primary, return {:user secondary :token-url \"...\"} or nil if no
   secondary minted yet. The token-url is what the primary forwards to their +1."
  [req user]
  (when (db/primary? user)
    (when-let [sec (db/get-secondary-user-of (:id user))]
      (let [base-url (str (name (:scheme req))
                          "://"
                          (get-in req [:headers "host"])
                          (u "/login?token=") (:token sec))]
        {:user sec :token-url base-url}))))

(defn home-handler [req]
  (let [user (:user req)]
    (if (:name-confirmed user)
      (html-response (views/home-page (ctx req)
                                      (db/get-rsvp (:id user))
                                      (secondary-info req user)))
      (html-response (views/welcome-page (ctx req))))))

(defn contact-handler [req]
  (html-response (views/contact-page (ctx req))))

(defn impressum-handler [req]
  (html-response (views/impressum-page (ctx req))))

(defn- photo-file-renderable? [photo]
  (let [f (io/file upload-dir (:filename photo))]
    (and (.exists f) (pos? (.length f)))))

(defn gallery-handler [req]
  (let [photos (filter photo-file-renderable? (db/get-all-photos))
        full? (>= (db/photo-count) max-photos)]
    (html-response (views/gallery-page (ctx req) photos full?))))

(defn chat-handler [req]
  (let [uid (get-in req [:user :id])
        messages (db/get-recent-messages uid)
        pinned (db/get-pinned-messages uid)
        oldest-id (some-> (first messages) :id)
        more? (and oldest-id (db/older-exists? oldest-id))]
    (html-response (views/chat-page (ctx req) messages pinned oldest-id more?))))

;; --- API Handlers ---

(defn- valid-attending-for [user value]
  (and (db/attending-values value)
       (or (db/primary? user) (not= "yes_plus_one" value))))

(defn- ensure-secondary!
  "Mint a secondary user for a primary if one doesn't yet exist. No-op for
   secondary users or for primaries who already have one. Returns the secondary
   user (existing or freshly minted). On a fresh mint, fires off a best-effort
   write-back to the Invites sheet so the secondary's token lands in the
   pre-allocated row below the primary."
  [user]
  (when (db/primary? user)
    (or (db/get-secondary-user-of (:id user))
        (let [sec (db/create-secondary-user! (:id user))]
          (future
            (try (invites/write-back-secondary! user sec)
                 (catch Exception e
                   (println "Invites write-back failed:" (.getMessage e)))))
          sec))))

(defn- sync-rsvp-async!
  "Best-effort fire-and-forget mirror of the user's RSVP into the Invites tab
   (column C + column E of their row, matched by token)."
  [user attending info]
  (future
    (try (invites/sync-rsvp-cell! user attending info)
         (catch Exception e
           (println "Invites RSVP sync failed:" (.getMessage e))))))

(defn rsvp-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        attending (:attending body)
        rsvp (db/get-rsvp (:id user))
        info (or (:additional-info rsvp) "")]
    (when (valid-attending-for user attending)
      (when (= "yes_plus_one" attending) (ensure-secondary! user))
      (db/upsert-rsvp! (:id user) attending info)
      (sync-rsvp-async! user attending info))
    (let [updated-rsvp (db/get-rsvp (:id user))
          secondary (secondary-info req user)]
      (sse/sse-response
       (sse/patch-elements (views/rsvp-fragment locale user updated-rsvp secondary))))))

(defn rsvp-info-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        raw-info (or (:additionalInfo body) "")
        info (subs raw-info 0 (min 500 (count raw-info)))
        rsvp (db/get-rsvp (:id user))
        attending (or (:attending rsvp) "yes")]
    (db/upsert-rsvp! (:id user) attending info)
    (sync-rsvp-async! user attending info)
    (let [updated-rsvp (db/get-rsvp (:id user))
          secondary (secondary-info req user)]
      (sse/sse-response
       (sse/patch-elements (views/rsvp-fragment locale user updated-rsvp secondary))
       (sse/patch-signals {:savedInfo true})))))

(defn- valid-upload? [file]
  (and (map? file)
       (seq (:filename file))
       (pos? (or (:size file) 0))
       (re-find #"\.[A-Za-z0-9]{1,8}$" (:filename file))))

(defn photo-upload-handler [req]
  (let [user (:user req)
        file (get-in req [:multipart-params "photo"])]
    (when (and (valid-upload? file)
               (< (db/photo-count) max-photos))
      (let [ext (clojure.string/lower-case
                 (last (clojure.string/split (:filename file) #"\.")))
            new-name (str (UUID/randomUUID) "." ext)
            dest (io/file upload-dir new-name)
            thumb (io/file upload-dir (thumb-rel-path new-name))]
        (io/copy (:tempfile file) dest)
        (try
          (generate-thumbnail! dest thumb thumb-max-dim)
          (catch Exception e
            (println "Thumbnail generation failed for" new-name (.getMessage e))))
        (db/save-photo! (:id user) new-name (:filename file))))
    (resp/redirect (u "/gallery"))))

(defn chat-send-handler [req]
  (let [user (:user req)
        body (or (parse-json-body req) {})
        message (clojure.string/trim (or (:chatMsg body) ""))]
    (when (seq message)
      (let [{:keys [id]} (db/save-message! (:id user) message)]
        (broadcast-new-message! id)))
    (sse/sse-response
     (sse/patch-signals {:chatMsg ""}))))

(defn chat-like-handler [req]
  (let [user (:user req)
        msg-id (some-> (get-in req [:query-params "id"]) parse-long)]
    (when msg-id
      (db/toggle-like! (:id user) msg-id)
      (broadcast-message-changed! msg-id))
    (sse/sse-response)))

(defn chat-pin-handler [req]
  (let [user (:user req)
        msg-id (some-> (get-in req [:query-params "id"]) parse-long)]
    (when msg-id
      (db/toggle-pin! (:id user) msg-id)
      (broadcast-pin-changed! msg-id))
    (sse/sse-response)))

(defn chat-older-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        before-id (some-> (get-in req [:query-params "before"]) parse-long)
        msgs (when before-id (db/get-older-messages (:id user) before-id))
        new-oldest (some-> (first msgs) :id)
        more? (and new-oldest (db/older-exists? new-oldest))
        msgs-html (clojure.string/join (map #(views/single-msg-html locale % (:id user)) msgs))]
    (sse/sse-response
     (when (seq msgs)
       (sse/patch-elements msgs-html {:selector "#chat-messages" :mode :prepend}))
     (sse/patch-elements (views/load-older-html locale new-oldest more?)))))

(defn display-name-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        raw (or (:newDisplayName body) "")
        new-name (let [t (clojure.string/trim raw)]
                   (when (seq t) (subs t 0 (min 30 (count t)))))]
    (db/update-display-name! (:id user) new-name)
    (let [updated (db/get-user-by-id (:id user))]
      (broadcast-display-name!)
      (sse/sse-response
       (sse/patch-elements (views/nav-user-html locale updated))
       (sse/patch-signals {:showNameEdit false
                           :newDisplayName (db/effective-name updated)
                           :savedDisplayName true})))))

(defn secondary-display-name-handler
  "Primary edits their +1's display name from the RSVP panel. Updates the
   secondary's display_name in the DB (which also flips `name_confirmed` on
   the secondary, so subsequent sheet Display Name syncs no longer override),
   mirrors to column F of the secondary's row in Invites, broadcasts to chat
   (author labels), and re-renders the RSVP card so the panel's signal stays
   in sync. No-op for non-primaries and for primaries with no secondary
   minted yet."
  [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        raw (or (:secondaryDisplayName body) "")
        new-name (let [t (clojure.string/trim raw)]
                   (when (seq t) (subs t 0 (min 30 (count t)))))]
    (when (db/primary? user)
      (when-let [sec (db/get-secondary-user-of (:id user))]
        (db/update-display-name! (:id sec) new-name)
        (let [updated (db/get-user-by-id (:id sec))]
          (future
            (try (invites/write-back-secondary-name! updated)
                 (catch Exception e
                   (println "Invites secondary-name write-back failed:"
                            (.getMessage e))))))
        (broadcast-display-name!)))
    (let [updated-rsvp (db/get-rsvp (:id user))
          secondary (secondary-info req user)
          sec-name (or (:display-name (:user secondary)) "")]
      (sse/sse-response
       (sse/patch-elements (views/rsvp-fragment locale user updated-rsvp secondary))
       (sse/patch-signals {:secondaryDisplayName sec-name
                           :savedSecondaryName true})))))

(defn welcome-confirm-handler
  "First-visit display-name confirmation. Plain form POST so we get a clean
   redirect to the home page once the user confirms (or just keeps the random
   name by hitting save with it unchanged)."
  [req]
  (let [user (:user req)
        raw (or (get-in req [:form-params "displayName"]) "")
        new-name (let [t (clojure.string/trim raw)]
                   (when (seq t) (subs t 0 (min 30 (count t)))))]
    (db/update-display-name! (:id user) new-name)
    (resp/redirect (u "/") :see-other)))

;; --- Upload file serving ---

(defn- mime-of [filename]
  (condp #(clojure.string/ends-with? %2 %1) filename
    ".jpg" "image/jpeg"
    ".jpeg" "image/jpeg"
    ".png" "image/png"
    ".gif" "image/gif"
    ".webp" "image/webp"
    "application/octet-stream"))

(defn- with-immutable-cache [resp]
  ;; Filenames are content-addressed (UUID + ext), so the bytes never change.
  (resp/header resp "Cache-Control" "public, max-age=31536000, immutable"))

(defn serve-upload [req]
  (let [filename (get-in req [:path-params :filename])
        file (io/file upload-dir filename)]
    (if (.exists file)
      (-> (resp/response file)
          (resp/content-type (mime-of filename))
          with-immutable-cache)
      (resp/not-found "Not found"))))

(defn serve-thumb
  "Serves the JPEG thumbnail for an upload. Generates lazily on first hit
   for older photos that pre-date the thumbnail feature; falls back to the
   full image if thumb generation fails."
  [req]
  (let [filename (get-in req [:path-params :filename])
        thumb (io/file upload-dir (thumb-rel-path filename))
        full (io/file upload-dir filename)]
    (when (and (not (.exists thumb)) (.exists full))
      (try (generate-thumbnail! full thumb thumb-max-dim)
           (catch Exception _ nil)))
    (cond
      (.exists thumb)
      (-> (resp/response thumb)
          (resp/content-type "image/jpeg")
          with-immutable-cache)

      (.exists full)
      (-> (resp/response full)
          (resp/content-type (mime-of filename))
          with-immutable-cache)

      :else (resp/not-found "Not found"))))

;; --- Router ---

;; --- Party Game Handlers ---

(defn party-hub-handler [req]
  (html-response (views/party-hub-page (ctx req))))

(defn party-bump-handler [req]
  (html-response (views/party-bump-page (get-locale req))))

(defn party-room-handler [req]
  (html-response (views/party-room-page (get-locale req))))

(defn party-radar-handler [req]
  (html-response (views/party-radar-page (get-locale req))))

;; --- Router ---

(def app
  (reitit/ring-handler
   (reitit/router
    [["/login" {:get login-handler}]
     ["/impressum" {:get impressum-handler}]
     ["/set-locale" {:get set-locale-handler}]
     ["/" {:get home-handler :middleware [require-auth]}]
     ["/contact" {:get contact-handler :middleware [require-auth]}]
     ["/gallery" {:get gallery-handler :middleware [require-auth]}]
     ["/chat" {:get chat-handler :middleware [require-auth]}]
     ["/party" {:get party-hub-handler :middleware [require-auth]}]
     ["/party/bump" {:get party-bump-handler}]
     ["/party/room" {:get party-room-handler}]
     ["/party/radar" {:get party-radar-handler}]
     ["/api/rsvp" {:post rsvp-handler :middleware [require-auth]}]
     ["/api/rsvp/info" {:post rsvp-info-handler :middleware [require-auth]}]
     ["/api/photo" {:post photo-upload-handler :middleware [require-auth]}]
     ["/api/chat/stream" {:get chat-stream-handler :middleware [require-auth]}]
     ["/api/chat/send" {:post chat-send-handler :middleware [require-auth]}]
     ["/api/chat/like" {:post chat-like-handler :middleware [require-auth]}]
     ["/api/chat/pin" {:post chat-pin-handler :middleware [require-auth]}]
     ["/api/chat/older" {:get chat-older-handler :middleware [require-auth]}]
     ["/api/profile/display-name" {:post display-name-handler :middleware [require-auth]}]
     ["/api/profile/secondary-display-name" {:post secondary-display-name-handler :middleware [require-auth]}]
     ["/api/profile/welcome" {:post welcome-confirm-handler :middleware [require-auth]}]
     ["/uploads/:filename" {:get serve-upload}]
     ["/thumbs/:filename" {:get serve-thumb}]])
   (reitit/routes
    (reitit/create-resource-handler {:path "/"})
    (reitit/create-default-handler))))
