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
   Optional opts: `:selector` (target CSS selector) and `:mode`
   (one of :morph :inner :outer :prepend :append :before :after).
   Defaults to morph against ids embedded in the HTML."
  ([html] (patch-elements html nil))
  ([html {:keys [selector mode]}]
   (let [lines (str/split-lines (clojure.core/str (or html "")))
         data (cond->> (map #(clojure.core/str "elements " %) lines)
                mode (cons (clojure.core/str "mode " (name mode)))
                selector (cons (clojure.core/str "selector " selector)))]
     (sse-event "datastar-patch-elements" data))))

(defn patch-remove
  "Remove DOM elements matching `selector`."
  [selector]
  (sse-event "datastar-patch-elements"
             [(clojure.core/str "selector " selector)
              "mode remove"]))

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
