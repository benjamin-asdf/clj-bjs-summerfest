(ns summerfest.sse
  (:require [cheshire.core :as json]))

(defn- sse-event [event-type data-lines]
  (str "event: " event-type "\n"
       (->> data-lines
            (map #(str "data: " %))
            (clojure.string/join "\n"))
       "\n\n"))

(defn merge-fragments
  "Build a datastar-merge-fragments SSE event from pre-rendered HTML strings."
  [& html-strings]
  (let [html (apply str html-strings)]
    (sse-event "datastar-merge-fragments" [(str "fragments " html)])))

(defn merge-signals
  "Build a datastar-merge-signals SSE event from a map."
  [signals-map]
  (sse-event "datastar-merge-signals"
             [(str "signals " (json/generate-string signals-map))]))

(defn execute-script
  "Build a datastar-execute-script SSE event."
  [script]
  (sse-event "datastar-execute-script" [(str "script " script)]))

(defn sse-response
  "Ring response with SSE content type. Pass SSE event strings."
  [& events]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"}
   :body (apply str events)})
