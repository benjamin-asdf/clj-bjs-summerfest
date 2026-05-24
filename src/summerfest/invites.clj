(ns summerfest.invites
  "Read the 'Invites' tab and mint magic-link tokens for primary+secondary user
   pairs. Rows live in pairs: each primary row is immediately followed by a
   secondary row whose name is often blank — the admin fills it once the
   primary confirms 'wir kommen zu zweit'.

   Sheet layout (tab 'Invites'):
     A: Name      (primary written by admin; secondary often blank)
     B: Language  ('de' or 'en')
     C: RSVP      (yes/no/maybe/blank — managed elsewhere)
     D: Token     (raw UUID, written by us)
     E: Info      (free-form note submitted with the RSVP)

   Re-runnable: if the primary's Token cell is empty we mint a fresh pair. If a
   secondary row's name is filled in later, the DB display name is updated to
   match on the next run."
  (:require [clojure.string :as str]
            [summerfest.db :as db]
            [summerfest.sheets :as sheets]))

(def ^:private invites-tab "Invites")
(def ^:private gaeste-tab "Gaeste")

(def ^:private headers ["Name" "Language" "RSVP" "Token" "Info"])

;; Gaeste tab is the master guest list. We only read column A (Gast): every
;; guest gets a primary + blank-secondary pair in Invites regardless of the
;; Personen value, since a primary can opt into a +1 from the web at any time.
(def ^:private gaeste-col-name 0)

(defn- cell [row idx]
  (-> (get row idx) (or "") str str/trim))

(defn- row-fields [row]
  {:name  (cell row 0)
   :lang  (cell row 1)
   :rsvp  (cell row 2)
   :token (cell row 3)
   :info  (cell row 4)})

(defn- effective-lang
  "Use the row's language if set, else fall back (typically the primary's lang
   for a blank secondary), else 'de'."
  [lang fallback]
  (or (not-empty lang) (not-empty fallback) "de"))

(defn- write-row! [row-number {:keys [name lang rsvp token info]}]
  (sheets/update-row! invites-tab row-number
                      [name lang rsvp token (or info "")]))

(defn- normalize-row
  "Pad/truncate a row to exactly 5 cells so a batch PUT covers A:E cleanly."
  [row]
  (mapv #(or % "") (take 5 (concat row (repeat "")))))

(defn sync-from-sheet!
  "Walk the Invites tab in (primary, secondary) pairs.

   For each pair where the primary's Token cell is empty: mint a primary user,
   mint a +1 secondary user, stage both tokens for write. If the secondary row
   already has a name, that name is stored as the secondary's display name.

   For pairs already minted: if the secondary row's name has been filled in or
   changed in the sheet, the DB display name is synced to match.

   All sheet edits are flushed in a single PUT at the end — minting per-row
   would blow past Sheets' 60-write-per-minute quota on a full guest list.

   Returns {:minted N :name-updates N :skipped N}."
  []
  (sheets/ensure-headers! invites-tab headers)
  (let [rows (vec (or (sheets/fetch-tab invites-tab) []))
        data (vec (drop 1 rows))
        pairs (partition-all 2 data)
        new-data (atom (mapv normalize-row data))
        set-row! (fn [idx vals] (swap! new-data assoc idx (normalize-row vals)))
        stats (atom {:minted 0 :name-updates 0 :skipped 0})]
    (doseq [[i pair] (map-indexed vector pairs)]
      (let [p (row-fields (first pair))
            s (row-fields (second pair))
            p-idx (* 2 i)
            s-idx (inc p-idx)
            p-lang (effective-lang (:lang p) nil)
            s-lang (effective-lang (:lang s) p-lang)]
        (cond
          (and (seq (:name p)) (empty? (:token p)))
          (let [primary   (db/create-user! :name (:name p))
                secondary (db/create-secondary-user! (:id primary))]
            (when (seq (:name s))
              (db/update-display-name! (:id secondary) (:name s)))
            (set-row! p-idx [(:name p) p-lang (:rsvp p) (str (:token primary)) (:info p)])
            (set-row! s-idx [(:name s) s-lang (:rsvp s) (str (:token secondary)) (:info s)])
            (swap! stats update :minted inc))

          (and (seq (:token s)) (seq (:name s)))
          (let [user (db/get-user-by-token (:token s))]
            (if (and user (not= (db/effective-name user) (:name s)))
              (do (db/update-display-name! (:id user) (:name s))
                  (swap! stats update :name-updates inc))
              (swap! stats update :skipped inc)))

          :else
          (swap! stats update :skipped inc))))
    (when (seq @new-data)
      (sheets/update-range! (str invites-tab "!A2:E" (+ 1 (count @new-data))) @new-data))
    @stats))

(defn- gaeste->invites-rows
  "Expand one Gaeste row into a primary + blank-secondary pair. RSVP column is
   left blank — the live site is the source of truth for RSVP state, and
   `sheets/sync-rsvp!` writes it back when a user submits the form."
  [row]
  [{:name (cell row gaeste-col-name)}
   {:name ""}])

(defn regenerate-from-gaeste!
  "Rewrite the Invites tab from the Gaeste tab. Every Gaeste row with a
   non-blank name produces a primary row immediately followed by a blank-name
   secondary row — the secondary slot stays reserved even for Personen=1 so
   the web-side +1 flow has a row to write back into.

   Preservation is pair-aware: the (primary, secondary) pair at position N in
   the new layout is matched against the old pair at the same position by the
   primary's name. When matched, both rows' tokens are carried over, and the
   secondary's display name is kept (the +1 flow may have written one in).
   RSVP cells are always blanked — the live site owns RSVP state and writes
   it back via `sheets/sync-rsvp!`. When the primary's name shifts, the whole
   pair resets.

   Returns {:wrote N :tokens-kept K}."
  []
  (sheets/ensure-headers! invites-tab headers)
  (let [existing-pairs (vec (partition-all 2 (drop 1 (or (sheets/fetch-tab invites-tab) []))))
        gaeste (vec (drop 1 (or (sheets/fetch-tab gaeste-tab) [])))
        new-pairs (->> gaeste
                       (filter #(seq (cell % gaeste-col-name)))
                       (map gaeste->invites-rows)
                       vec)
        kept (atom 0)
        merge-pair (fn [idx [new-p new-s]]
                     (let [[old-p old-s] (get existing-pairs idx)
                           same? (= (:name new-p) (cell old-p 0))
                           p-tok (if same? (cell old-p 3) "")
                           s-tok (if same? (cell old-s 3) "")
                           p-info (if same? (cell old-p 4) "")
                           s-info (if same? (cell old-s 4) "")
                           s-name (if same? (cell old-s 0) (:name new-s))]
                       (when (seq p-tok) (swap! kept inc))
                       (when (seq s-tok) (swap! kept inc))
                       [[(:name new-p) "de" "" p-tok p-info]
                        [s-name        "de" "" s-tok s-info]]))
        out (vec (mapcat merge-pair (range) new-pairs))
        ;; pad with empty rows so any trailing leftovers from the old layout
        ;; get cleared in a single PUT
        existing-count (count (mapcat identity existing-pairs))
        pad (max 0 (- existing-count (count out)))
        padded (into out (repeat pad ["" "" "" "" ""]))
        end-row (+ 1 (count padded))]
    (when (seq padded)
      (sheets/update-range! (str invites-tab "!A2:E" end-row) padded))
    {:wrote (count out) :tokens-kept @kept}))

(defn create-and-invite!
  "Mint a fresh primary with the given display name plus a +1 secondary, and
   append both rows to the bottom of the Invites tab with tokens pre-filled.
   For inviting someone who isn't on the Gaeste sheet — Gaeste-sourced guests
   come through `regenerate-from-gaeste!` + `sync-from-sheet!`.
   Returns {:primary u :secondary u}."
  [name]
  (let [primary (db/create-user! :name name)
        secondary (db/create-secondary-user! (:id primary))]
    (sheets/ensure-headers! invites-tab headers)
    (sheets/append-rows! invites-tab
                         [[name "de" "" (str (:token primary)) ""]
                          [""   "de" "" (str (:token secondary)) ""]])
    {:primary primary :secondary secondary}))

(defn- row-of-user
  "Find the 1-based sheet row for `user` in the Invites tab by matching the
   user's token against column D. Returns nil when sheets isn't configured or
   the user isn't present (e.g. minted outside the invites flow)."
  [user]
  (when-let [rows (sheets/fetch-tab invites-tab)]
    (let [user-token (str (:token user))
          idx (->> (map-indexed vector rows)
                   (some (fn [[i r]] (when (= user-token (cell r 3)) i))))]
      (when idx (inc idx)))))

(defn sync-rsvp-cell!
  "Mirror a user's RSVP into the Invites tab. Updates column C (RSVP) and
   column E (Info) of the row whose column D matches the user's token; column
   D (Token) is left untouched. No-op when sheets isn't configured or when
   the user isn't in the sheet."
  [user attending info]
  (when-let [sheet-row (row-of-user user)]
    (sheets/update-range! (str invites-tab "!C" sheet-row)
                          [[(or attending "")]])
    (sheets/update-range! (str invites-tab "!E" sheet-row)
                          [[(or info "")]])))

(defn write-back-secondary!
  "Fill the secondary slot in Invites after the web-side +1 flow mints a
   secondary user. Looks up the primary's row by its token in column D and
   writes the new secondary's display name + token into the row directly
   below. No-op when sheets isn't configured or when the primary isn't found
   in the sheet (e.g. user minted via REPL, outside the invites pairing)."
  [primary secondary]
  (when-let [p-row (row-of-user primary)]
    (let [display (or (:display-name secondary) (:name secondary))]
      (write-row! (inc p-row)
                  {:name display :lang "de" :rsvp ""
                   :token (str (:token secondary))}))))

(defn write-back-secondary-name!
  "Update only column A (Name) on the secondary user's row in Invites. Used
   when the primary edits their +1's display name from the RSVP panel. Empty
   string clears the cell. No-op when sheets isn't configured or the secondary
   isn't in the sheet."
  [secondary]
  (when-let [row (row-of-user secondary)]
    (sheets/update-range! (str invites-tab "!A" row)
                          [[(or (:display-name secondary) "")]])))
