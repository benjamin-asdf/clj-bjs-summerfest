(ns summerfest.routes
  (:require [summerfest.db :as db]
            [summerfest.views :as views]
            [summerfest.sse :as sse]
            [summerfest.sheets :as sheets]
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

;; --- Helpers ---

(defn- get-locale [req]
  (or (get-in req [:session :locale])
      i18n/default-locale))

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
  (if-let [token (get-in req [:query-params "token"])]
    (if-let [user (db/get-user-by-token token)]
      (-> (resp/redirect (u "/"))
          (assoc :session (merge (:session req) {:user-id (:id user)})))
      (html-response (views/invalid-token-page (get-locale req))))
    (html-response (views/login-page (get-locale req)))))

(defn set-locale-handler [req]
  (let [locale (keyword (get-in req [:query-params "locale"] "de"))
        locale (if (#{:de :en} locale) locale :de)
        referer (get-in req [:headers "referer"] (u "/"))]
    (-> (resp/redirect referer)
        (assoc :session (merge (:session req) {:locale locale})))))

(defn home-handler [req]
  (let [user (:user req)
        rsvp (db/get-rsvp (:id user))]
    (html-response (views/home-page (ctx req) rsvp))))

(defn contact-handler [req]
  (html-response (views/contact-page (ctx req))))

(defn impressum-handler [req]
  (html-response (views/impressum-page (ctx req))))

(defn directions-handler [req]
  (html-response (views/directions-page (ctx req))))

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

(defn rsvp-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        attending (:attending body)
        rsvp (db/get-rsvp (:id user))
        info (or (:additional-info rsvp) "")]
    (when (some? attending)
      (db/upsert-rsvp! (:id user) attending info)
      (future (sheets/sync-rsvp! (:name user) (:group-size user) attending info)))
    (let [updated-rsvp (db/get-rsvp (:id user))]
      (sse/sse-response
       (sse/patch-elements (views/rsvp-fragment locale user updated-rsvp))))))

(defn rsvp-info-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        info (or (:additionalInfo body) "")
        rsvp (db/get-rsvp (:id user))
        attending (if rsvp (:attending rsvp) true)]
    (db/upsert-rsvp! (:id user) attending info)
    (future (sheets/sync-rsvp! (:name user) (:group-size user) attending info))
    (let [updated-rsvp (db/get-rsvp (:id user))]
      (sse/sse-response
       (sse/patch-elements (views/rsvp-fragment locale user updated-rsvp))))))

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
            dest (io/file upload-dir new-name)]
        (io/copy (:tempfile file) dest)
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
                           :newDisplayName (db/effective-name updated)})))))

;; --- Upload file serving ---

(defn serve-upload [req]
  (let [filename (get-in req [:path-params :filename])
        file (io/file upload-dir filename)]
    (if (.exists file)
      (-> (resp/response file)
          (resp/content-type (condp #(clojure.string/ends-with? %2 %1) filename
                               ".jpg" "image/jpeg"
                               ".jpeg" "image/jpeg"
                               ".png" "image/png"
                               ".gif" "image/gif"
                               ".webp" "image/webp"
                               "application/octet-stream")))
      (resp/not-found "Not found"))))

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
     ["/directions" {:get directions-handler :middleware [require-auth]}]
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
     ["/uploads/:filename" {:get serve-upload}]])
   (reitit/routes
    (reitit/create-resource-handler {:path "/"})
    (reitit/create-default-handler))))
