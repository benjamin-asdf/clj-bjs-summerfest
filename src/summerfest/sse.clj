(ns summerfest.sse
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn- sse-event [event-type data-lines]
  (clojure.core/str "event: " event-type "\n"
                    (->> data-lines
                         (map #(clojure.core/str "data: " %))
                         (str/join "\n"))
                    "\n\n"))

(defn patch-elements
  "Build a datastar-patch-elements SSE event from pre-rendered HTML strings.
   Each line of HTML gets its own 'data: elements ...' line."
  [& html-strings]
  (let [html (apply clojure.core/str html-strings)
        lines (str/split-lines html)]
    (sse-event "datastar-patch-elements"
               (map #(clojure.core/str "elements " %) lines))))

(defn patch-signals
  "Build a datastar-patch-signals SSE event from a map."
  [signals-map]
  (sse-event "datastar-patch-signals"
             [(clojure.core/str "signals " (json/generate-string signals-map))]))

(defn sse-response
  "Ring response with SSE content type. Pass SSE event strings."
  [& events]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"}
   :body (apply clojure.core/str events)})
