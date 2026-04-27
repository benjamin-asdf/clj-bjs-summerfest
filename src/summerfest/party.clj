(ns summerfest.party
  "WebSocket-based party games: bump-to-pair, room-code chain, compass radar.
   All modes share the same ball-passing mechanic between connected phones."
  (:require [org.httpkit.server :as hk]
            [cheshire.core :as json])
  (:import [java.util UUID]))

(defonce rooms (atom {}))

(defn- gen-id [] (subs (str (UUID/randomUUID)) 0 8))

(defn- gen-room-code []
  (let [chars "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"]
    (apply str (repeatedly 4 #(nth chars (rand-int (count chars)))))))

(defn- send! [ch msg]
  (when ch
    (try (hk/send! ch (json/generate-string msg))
         (catch Exception _ nil))))

(defn- broadcast! [room-id msg & {:keys [exclude]}]
  (doseq [[pid {:keys [ws]}] (get-in @rooms [room-id :players])
          :when (and ws (not= pid exclude))]
    (send! ws msg)))

(defn- broadcast-state! [room-id]
  (doseq [[pid {:keys [ws]}] (get-in @rooms [room-id :players])
          :when ws]
    (let [room (get @rooms room-id)]
      (send! ws {:type "room-state"
                 :myId pid
                 :mode (name (:mode room))
                 :players (into {}
                                (map (fn [[id p]]
                                       [id {:name (:name p)
                                            :position (:position p)
                                            :compass (:compass p)}]))
                                (:players room))
                 :chain (:chain room)
                 :ballHolder (:ball-holder room)}))))

(defn- update-chain! [room-id]
  (let [room (get @rooms room-id)]
    (case (:mode room)
      :room
      (let [chain (->> (:players room)
                       (sort-by (fn [[_ p]] (or (:position p) 999)))
                       (mapv first))]
        (swap! rooms assoc-in [room-id :chain] chain))

      :radar
      (let [chain (->> (:players room)
                       (sort-by (fn [[_ p]] (or (:compass p) 999)))
                       (mapv first))]
        (swap! rooms assoc-in [room-id :chain] chain))

      :bump nil)))

;; --- Message handlers ---

(defn- handle-join [room-id player-id ch data]
  (let [player-name (or (:name data) "Guest")
        pos (count (get-in @rooms [room-id :players]))]
    (swap! rooms update-in [room-id :players player-id]
           merge {:ws ch :name player-name :position pos :compass 0})
    (update-chain! room-id)
    (when (nil? (get-in @rooms [room-id :ball-holder]))
      (swap! rooms assoc-in [room-id :ball-holder] player-id))
    (broadcast-state! room-id)))

(defn- handle-sensor [room-id player-id data]
  (when-let [compass (:compass data)]
    (swap! rooms assoc-in [room-id :players player-id :compass] compass)
    (when (= :radar (get-in @rooms [room-id :mode]))
      (update-chain! room-id)
      ;; Broadcast compass updates to all for radar display
      (broadcast! room-id {:type "compass-update"
                           :playerId player-id
                           :compass compass
                           :chain (get-in @rooms [room-id :chain])}))))

(defn- handle-bump [room-id player-id data]
  (let [ts (:timestamp data)]
    (swap! rooms assoc-in [room-id :players player-id :last-bump] ts)
    (let [room (get @rooms room-id)
          match (->> (:players room)
                     (filter (fn [[id p]]
                               (and (not= id player-id)
                                    (:last-bump p)
                                    (< (abs (- ts (:last-bump p))) 600))))
                     first)]
      (when match
        (let [[match-id _] match
              chain (get-in @rooms [room-id :chain] [])
              pid-in? (some #{player-id} chain)
              mid-in? (some #{match-id} chain)
              new-chain (cond
                          (and pid-in? mid-in?) chain
                          pid-in?
                          (let [idx (.indexOf ^java.util.List (vec chain) player-id)]
                            (vec (concat (subvec chain 0 (inc idx))
                                         [match-id]
                                         (subvec chain (inc idx)))))
                          mid-in?
                          (let [idx (.indexOf ^java.util.List (vec chain) match-id)]
                            (vec (concat (subvec chain 0 idx)
                                         [player-id]
                                         (subvec chain idx))))
                          :else [player-id match-id])]
          (swap! rooms assoc-in [room-id :chain] new-chain)
          ;; Clear bump timestamps
          (swap! rooms assoc-in [room-id :players player-id :last-bump] nil)
          (swap! rooms assoc-in [room-id :players match-id :last-bump] nil)
          ;; Notify of successful bump
          (when-let [ws1 (get-in @rooms [room-id :players player-id :ws])]
            (send! ws1 {:type "bump-success" :pairedWith match-id}))
          (when-let [ws2 (get-in @rooms [room-id :players match-id :ws])]
            (send! ws2 {:type "bump-success" :pairedWith player-id}))
          (broadcast-state! room-id))))))

(defn- handle-ball-exit [room-id player-id data]
  (let [room (get @rooms room-id)
        chain (:chain room)
        n (count chain)]
    (when (and (pos? n) (= player-id (:ball-holder room)))
      (let [edge (:edge data)
            idx (.indexOf ^java.util.List (vec chain) player-id)
            next-idx (case edge
                       "right" (mod (inc idx) n)
                       "left" (mod (dec idx) n)
                       nil)]
        (when next-idx
          (let [next-id (nth chain next-idx)]
            (swap! rooms assoc-in [room-id :ball-holder] next-id)
            (when-let [ws (get-in @rooms [room-id :players next-id :ws])]
              (send! ws {:type "ball-enter"
                         :fromEdge (if (= edge "right") "left" "right")
                         :y (:y data)
                         :vx (- (or (:vx data) 3))
                         :vy (:vy data)}))
            (broadcast! room-id {:type "ball-holder" :holderId next-id})))))))

(defn- handle-reorder [room-id _player-id data]
  (when-let [new-chain (:chain data)]
    (when (vector? new-chain)
      (swap! rooms assoc-in [room-id :chain] new-chain)
      (broadcast-state! room-id))))

(defn- handle-message [room-id player-id ch raw]
  (try
    (let [data (json/parse-string raw true)]
      (case (:type data)
        "join" (handle-join room-id player-id ch data)
        "sensor" (handle-sensor room-id player-id data)
        "bump" (handle-bump room-id player-id data)
        "ball-exit" (handle-ball-exit room-id player-id data)
        "reorder" (handle-reorder room-id player-id data)
        nil))
    (catch Exception e
      (println "Party WS error:" (.getMessage e)))))

(defn- handle-disconnect [room-id player-id]
  (swap! rooms update-in [room-id :players] dissoc player-id)
  (swap! rooms update-in [room-id :chain]
         (fn [c] (vec (remove #{player-id} c))))
  (when (= player-id (get-in @rooms [room-id :ball-holder]))
    (swap! rooms assoc-in [room-id :ball-holder]
           (first (get-in @rooms [room-id :chain]))))
  (if (empty? (get-in @rooms [room-id :players]))
    (swap! rooms dissoc room-id)
    (broadcast-state! room-id)))

;; --- Public WebSocket handler ---

(defn ws-handler [req]
  (let [params (:query-params req)
        room-id (get params "room" (gen-room-code))
        mode (keyword (get params "mode" "room"))
        player-id (gen-id)]
    (swap! rooms update room-id
           (fn [r] (or r {:mode mode :players {} :chain [] :ball-holder nil})))
    (hk/with-channel req ch
      (send! ch {:type "welcome" :playerId player-id :roomId room-id})
      (hk/on-receive ch (fn [raw] (handle-message room-id player-id ch raw)))
      (hk/on-close ch (fn [_] (handle-disconnect room-id player-id))))))
