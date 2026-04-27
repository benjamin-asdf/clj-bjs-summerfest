(ns summerfest.config)

(def base-path
  "Path under which the app is mounted (e.g. \"/summerfest\"). Empty string when
  served from the root."
  (let [v (or (System/getenv "BASE_PATH") "")]
    (cond
      (or (= v "") (= v "/")) ""
      (.startsWith ^String v "/") (clojure.string/replace v #"/+$" "")
      :else (str "/" (clojure.string/replace v #"/+$" "")))))

(defn u
  "Prefix a path with the configured base-path. Pass paths starting with /."
  [path]
  (str base-path path))

(defn strip-base-path
  "Remove the base-path prefix from a URI string. Used by middleware."
  [uri]
  (if (and (seq base-path) (.startsWith ^String uri base-path))
    (let [stripped (subs uri (count base-path))]
      (if (or (= stripped "") (not (.startsWith ^String stripped "/")))
        (str "/" stripped)
        stripped))
    uri))

(defn wrap-base-path
  "Ring middleware that strips the base-path prefix from :uri so internal routes
  can be defined as if mounted at /."
  [handler]
  (fn [req]
    (handler (update req :uri strip-base-path))))
