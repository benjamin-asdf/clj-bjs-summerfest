(ns summerfest.core
  (:require [org.httpkit.server :as hk]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [summerfest.routes :as routes]
            [summerfest.db :as db]))

(defonce server (atom nil))

(def session-secret
  (or (System/getenv "SESSION_SECRET") "summerfest2026!!"))

(def app
  (-> routes/app
      (wrap-defaults
       (-> site-defaults
           (assoc-in [:session :store] (cookie-store {:key (.getBytes session-secret)}))
           (assoc-in [:session :cookie-attrs :same-site] :lax)
           (assoc-in [:session :cookie-attrs :max-age] (* 60 60 24 30))
           (assoc-in [:security :anti-forgery] false)))))

(defn start! [& {:keys [port] :or {port 3000}}]
  (db/migrate!)
  (reset! server (hk/run-server app {:port port}))
  (println (str "Summer Fest running on http://localhost:" port)))

(defn stop! []
  (when-let [s @server]
    (s :timeout 100)
    (reset! server nil)
    (println "Server stopped.")))

(defn -main [& _args]
  (let [port (parse-long (or (System/getenv "PORT") "3000"))]
    (start! :port port)))
