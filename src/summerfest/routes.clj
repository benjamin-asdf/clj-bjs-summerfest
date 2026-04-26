(ns summerfest.routes
  (:require [summerfest.db :as db]
            [summerfest.views :as views]
            [summerfest.sse :as sse]
            [summerfest.sheets :as sheets]
            [summerfest.i18n :as i18n]
            [reitit.ring :as reitit]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.util UUID]))

(def ^:private upload-dir
  (or (System/getenv "UPLOAD_DIR") "uploads"))

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
      (resp/redirect "/login"))))

(defn- html-response [body]
  (-> (resp/response body)
      (resp/content-type "text/html; charset=utf-8")))

(defn- parse-json-body [req]
  (try
    (when-let [body (:body req)]
      (let [s (if (string? body) body (slurp body))]
        (when (seq s) (json/parse-string s true))))
    (catch Exception _ nil)))

;; --- Page Handlers ---

(defn login-handler [req]
  (if-let [token (get-in req [:query-params "token"])]
    (if-let [user (db/get-user-by-token token)]
      (-> (resp/redirect "/")
          (assoc :session (merge (:session req) {:user-id (:id user)})))
      (html-response (views/invalid-token-page (get-locale req))))
    (html-response (views/login-page (get-locale req)))))

(defn set-locale-handler [req]
  (let [locale (keyword (get-in req [:query-params "locale"] "de"))
        locale (if (#{:de :en} locale) locale :de)
        referer (get-in req [:headers "referer"] "/")]
    (-> (resp/redirect referer)
        (assoc :session (merge (:session req) {:locale locale})))))

(defn home-handler [req]
  (let [user (:user req)
        rsvp (db/get-rsvp (:id user))]
    (html-response (views/home-page (ctx req) rsvp))))

(defn contact-handler [req]
  (html-response (views/contact-page (ctx req))))

(defn directions-handler [req]
  (html-response (views/directions-page (ctx req))))

(defn gallery-handler [req]
  (let [photos (db/get-all-photos)]
    (html-response (views/gallery-page (ctx req) photos))))

(defn chat-handler [req]
  (let [messages (db/get-recent-messages)]
    (html-response (views/chat-page (ctx req) messages))))

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
       (sse/merge-fragments (views/rsvp-fragment locale user updated-rsvp))))))

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
       (sse/merge-fragments (views/rsvp-fragment locale user updated-rsvp))))))

(defn photo-upload-handler [req]
  (let [user (:user req)
        file (get-in req [:multipart-params "photo"])]
    (if file
      (let [ext (last (clojure.string/split (:filename file) #"\."))
            new-name (str (UUID/randomUUID) "." ext)
            dest (io/file upload-dir new-name)]
        (io/copy (:tempfile file) dest)
        (db/save-photo! (:id user) new-name (:filename file))
        (resp/redirect "/gallery"))
      (resp/redirect "/gallery"))))

(defn chat-send-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        body (or (parse-json-body req) {})
        message (clojure.string/trim (or (:chatMsg body) ""))]
    (when (seq message)
      (db/save-message! (:id user) message))
    (let [messages (db/get-recent-messages)]
      (sse/sse-response
       (sse/merge-fragments (views/chat-messages-html locale messages (:id user)))
       (sse/merge-signals {:chatMsg ""})))))

(defn chat-messages-html-handler [req]
  (let [user (:user req)
        locale (get-locale req)
        messages (db/get-recent-messages)]
    (-> (resp/response (views/chat-messages-html locale messages (:id user)))
        (resp/content-type "text/html; charset=utf-8"))))

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

(def app
  (reitit/ring-handler
   (reitit/router
    [["/login" {:get login-handler}]
     ["/set-locale" {:get set-locale-handler}]
     ["/" {:get home-handler :middleware [require-auth]}]
     ["/contact" {:get contact-handler :middleware [require-auth]}]
     ["/directions" {:get directions-handler :middleware [require-auth]}]
     ["/gallery" {:get gallery-handler :middleware [require-auth]}]
     ["/chat" {:get chat-handler :middleware [require-auth]}]
     ["/api/rsvp" {:post rsvp-handler :middleware [require-auth]}]
     ["/api/rsvp/info" {:post rsvp-info-handler :middleware [require-auth]}]
     ["/api/photo" {:post photo-upload-handler :middleware [require-auth]}]
     ["/api/chat/send" {:post chat-send-handler :middleware [require-auth]}]
     ["/api/chat/messages-html" {:get chat-messages-html-handler :middleware [require-auth]}]
     ["/uploads/:filename" {:get serve-upload}]])
   (reitit/routes
    (reitit/create-resource-handler {:path "/"})
    (reitit/create-default-handler))))
