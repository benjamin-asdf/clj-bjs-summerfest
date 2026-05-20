(ns summerfest.core
  (:require [org.httpkit.server :as hk]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [summerfest.routes :as routes]
            [summerfest.party :as party]
            [summerfest.config :as config]
            [summerfest.db :as db]))

(defonce server (atom nil))
(defonce nrepl-server (atom nil))

(defn- derive-session-key
  "Read SESSION_SECRET and derive a 16-byte AES-128 key from it via SHA-256.
   Fails fast if the env var is missing or too short — refuses to fall back
   to a hardcoded default."
  []
  (let [s (System/getenv "SESSION_SECRET")]
    (when (or (nil? s) (< (count s) 16))
      (throw (ex-info "SESSION_SECRET must be set (>= 16 chars). Generate one with `openssl rand -base64 32` and put it in the server's .env." {})))
    (let [md (java.security.MessageDigest/getInstance "SHA-256")
          digest (.digest md (.getBytes s "UTF-8"))]
      (java.util.Arrays/copyOf digest 16))))

(def ^:private session-key (derive-session-key))

(def ^:private wrapped-routes
  (-> routes/app
      (wrap-defaults
       (-> site-defaults
           (assoc-in [:session :store] (cookie-store {:key session-key}))
           (assoc-in [:session :cookie-attrs :same-site] :lax)
           (assoc-in [:session :cookie-attrs :max-age] (* 60 60 24 30))
           (assoc-in [:security :anti-forgery] false)))
      config/wrap-base-path))

(def ^:private ws-path (config/u "/party/ws"))

(defn app [req]
  ;; WebSocket endpoint bypasses Ring middleware (needs raw http-kit channel)
  (if (= (:uri req) ws-path)
    (party/ws-handler req)
    (wrapped-routes req)))

(defn start-nrepl! [port]
  (require 'nrepl.server)
  (let [start-fn (resolve 'nrepl.server/start-server)
        s (start-fn :bind "127.0.0.1" :port port)]
    (reset! nrepl-server s)
    (println (str "nREPL listening on 127.0.0.1:" port))
    s))

(defn start! [& {:keys [port ip nrepl-port]
                 :or {port 3030
                      ip (or (System/getenv "BIND_IP") "0.0.0.0")}}]
  (db/migrate!)
  (reset! server (hk/run-server app {:port port :ip ip}))
  (println (str "Summer Fest running on http://" ip ":" port
                (when (seq config/base-path) (str " (base " config/base-path ")"))))
  (when nrepl-port
    (start-nrepl! nrepl-port)))

(defn stop! []
  (when-let [s @server]
    (s :timeout 100)
    (reset! server nil)
    (println "Server stopped."))
  (when-let [n @nrepl-server]
    (when-let [stop-fn (resolve 'nrepl.server/stop-server)]
      (stop-fn n))
    (reset! nrepl-server nil)))

(defn -main [& _args]
  (let [port (parse-long (or (System/getenv "PORT") "3000"))
        nrepl-port (some-> (System/getenv "NREPL_PORT") parse-long)]
    (start! :port port :nrepl-port nrepl-port)
    @(promise)))

(comment

  (stop!)
  (start!))
