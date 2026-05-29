(ns summerfest.invites
  "Read the 'Invites' tab and mint magic-link tokens for primary+secondary user
   pairs. Rows live in pairs: each primary row is immediately followed by a
   secondary row whose name is often blank — the admin fills the Display Name
   column once the primary confirms 'wir kommen zu zweit'.

   Sheet layout (tab 'Invites'), columns managed by the code:
     A: Name         (primary written by admin from Gaeste; secondary usually blank)
     B: Language     ('de' or 'en')
     C: RSVP         (yes/no/maybe/blank — written by the web on submit)
     D: Token        (raw UUID, written by us)
     E: Info         (free-form note submitted with the RSVP)
     F: Display Name (admin-supplied override / +1 name; synced into the DB
                     until the user picks their own name via the welcome page)

   Columns past F are not touched by the code — the admin can add their own
   columns (Notes, Phone, etc.) and they survive all sync operations.

   Re-runnable: if the primary's Token cell is empty we mint a fresh pair. On
   subsequent runs, Display Name is pushed into the DB for any user who hasn't
   confirmed their own name yet."
  (:require [clojure.string :as str]
            [summerfest.db :as db]
            [summerfest.sheets :as sheets]))

(def ^:private invites-tab "Invites")
(def ^:private gaeste-tab "Gaeste")

(def ^:private headers ["Name" "Language" "RSVP" "Token" "Info" "Display Name"])
(def ^:private managed-cols (count headers))
(def ^:private last-col (str (char (+ (dec (int \A)) managed-cols))))

;; Gaeste tab is the master guest list. We only read column A (Gast): every
;; guest gets a primary + blank-secondary pair in Invites regardless of the
;; Personen value, since a primary can opt into a +1 from the web at any time.
(def ^:private gaeste-col-name 0)

(defn- cell [row idx]
  (-> (get row idx) (or "") str str/trim))

(defn- row-fields [row]
  {:name         (cell row 0)
   :lang         (cell row 1)
   :rsvp         (cell row 2)
   :token        (cell row 3)
   :info         (cell row 4)
   :display-name (cell row 5)})

(defn- effective-lang
  "Use the row's language if set, else fall back (typically the primary's lang
   for a blank secondary), else 'de'."
  [lang fallback]
  (or (not-empty lang) (not-empty fallback) "de"))

(defn- write-row! [row-number {:keys [name lang rsvp token info display-name]}]
  (sheets/update-row! invites-tab row-number
                      [name lang rsvp token (or info "") (or display-name "")]))

(defn- normalize-row
  "Pad/truncate a row to exactly `managed-cols` cells so the batch PUT covers
   A..F cleanly. Columns past F are not represented in memory; they're left
   alone on the sheet because the PUT range stops at F."
  [row]
  (mapv #(or % "") (take managed-cols (concat row (repeat "")))))

(defn sync-from-sheet!
  "Walk the Invites tab in (primary, secondary) pairs.

   For each pair where the primary's Token cell is empty: mint a primary user,
   mint a +1 secondary user, stage both tokens for write. If the Display Name
   column is set on either row, that name is applied to the freshly-minted
   user (column A on the secondary row is honored as a legacy fallback when
   Display Name is empty).

   For pairs already minted: the Display Name column is pushed into the DB
   for any user who hasn't confirmed their own name yet (via the welcome page
   or nav edit). Once a user confirms, the sheet stops winning.

   All sheet edits are flushed in a single PUT at the end — minting per-row
   would blow past Sheets' 60-write-per-minute quota on a full guest list.
   The PUT range is A:F, so admin-managed columns past F are left alone.

   Returns {:minted N :name-updates N :skipped N}."
  []
  (sheets/ensure-headers! invites-tab headers)
  (let [rows (vec (or (sheets/fetch-tab invites-tab) []))
        data (vec (drop 1 rows))
        pairs (partition-all 2 data)
        new-data (atom (mapv normalize-row data))
        set-row! (fn [idx vals] (swap! new-data assoc idx (normalize-row vals)))
        stats (atom {:minted 0 :name-updates 0 :skipped 0})
        sync-display!
        (fn [token sheet-name]
          (when (and (seq token) (seq sheet-name))
            (when-let [user (db/get-user-by-token token)]
              (when (and (not (:name-confirmed user))
                         (not= (:display-name user) sheet-name))
                (db/set-display-name-from-sheet! (:id user) sheet-name)
                (swap! stats update :name-updates inc)))))]
    (doseq [[i pair] (map-indexed vector pairs)]
      (let [p (row-fields (first pair))
            s (row-fields (second pair))
            p-idx (* 2 i)
            s-idx (inc p-idx)
            p-lang (effective-lang (:lang p) nil)
            s-lang (effective-lang (:lang s) p-lang)
            ;; Display Name column preferred; column A on the secondary row is
            ;; honored as a legacy fallback (older sheets put the +1's name
            ;; there before the Display Name column existed).
            s-display (or (not-empty (:display-name s)) (not-empty (:name s)))]
        (cond
          (and (seq (:name p)) (empty? (:token p)))
          (let [primary   (db/create-user! :name (:name p))
                secondary (db/create-secondary-user! (:id primary))]
            (when (seq (:display-name p))
              (db/set-display-name-from-sheet! (:id primary) (:display-name p)))
            (when (seq s-display)
              (db/set-display-name-from-sheet! (:id secondary) s-display))
            (set-row! p-idx [(:name p) p-lang (:rsvp p) (str (:token primary)) (:info p) (:display-name p)])
            (set-row! s-idx [(:name s) s-lang (:rsvp s) (str (:token secondary)) (:info s) (:display-name s)])
            (swap! stats update :minted inc))

          (or (seq (:token p)) (seq (:token s)))
          (do
            (sync-display! (:token p) (:display-name p))
            (sync-display! (:token s) s-display)
            (swap! stats update :skipped inc))

          :else
          (swap! stats update :skipped inc))))
    (when (seq @new-data)
      (sheets/update-range! (str invites-tab "!A2:" last-col (+ 1 (count @new-data))) @new-data))
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
   primary's name. When matched, both rows' tokens, info, and Display Name are
   carried over, plus column A on the secondary (the +1 flow may have written
   one in pre-Display-Name). RSVP cells are always blanked — the live site
   owns RSVP state. When the primary's name shifts, the whole pair resets.

   The PUT only spans A:F, so admin-managed columns past F are left in place.
   (They will, however, no longer line up with their original guest if the
   layout shifts — the admin should re-check those columns after a regen.)

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
                           p-disp (if same? (cell old-p 5) "")
                           s-disp (if same? (cell old-s 5) "")
                           s-name (if same? (cell old-s 0) (:name new-s))]
                       (when (seq p-tok) (swap! kept inc))
                       (when (seq s-tok) (swap! kept inc))
                       [[(:name new-p) "de" "" p-tok p-info p-disp]
                        [s-name        "de" "" s-tok s-info s-disp]]))
        out (vec (mapcat merge-pair (range) new-pairs))
        ;; pad with empty rows so any trailing leftovers from the old layout
        ;; get cleared in a single PUT
        existing-count (count (mapcat identity existing-pairs))
        pad (max 0 (- existing-count (count out)))
        empty-row (vec (repeat managed-cols ""))
        padded (into out (repeat pad empty-row))
        end-row (+ 1 (count padded))]
    (when (seq padded)
      (sheets/update-range! (str invites-tab "!A2:" last-col end-row) padded))
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
                         [[name "de" "" (str (:token primary)) "" ""]
                          [""   "de" "" (str (:token secondary)) "" ""]])
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
   writes the new secondary's token + Display Name into the row directly
   below (column A stays blank — secondaries don't come from Gaeste, so the
   admin's view of the +1's name lives in column F). No-op when sheets isn't
   configured or when the primary isn't found in the sheet (e.g. user minted
   via REPL, outside the invites pairing)."
  [primary secondary]
  (when-let [p-row (row-of-user primary)]
    (let [display (or (:display-name secondary) (:name secondary))]
      (write-row! (inc p-row)
                  {:name ""
                   :lang "de"
                   :rsvp ""
                   :token (str (:token secondary))
                   :info ""
                   :display-name display}))))

(defn write-back-secondary-name!
  "Update only column F (Display Name) on the secondary user's row in Invites.
   Used when the primary edits their +1's display name from the RSVP panel.
   Empty string clears the cell. No-op when sheets isn't configured or the
   secondary isn't in the sheet."
  [secondary]
  (when-let [row (row-of-user secondary)]
    (sheets/update-range! (str invites-tab "!" last-col row)
                          [[(or (:display-name secondary) "")]])))

(def ^:private default-public-base-url
  (or (System/getenv "PUBLIC_BASE_URL")
      "https://benjamin-schwerdtner.de/summerfest"))

(defn- read-extra-cols
  "Read `tsv-path` if it exists and return {name -> \"<TAB>extras...\"} so the
   admin's hand-maintained columns past the +1 Link (e.g. a `send?` flag)
   survive a regen. Matching is by the Name column verbatim — renamed rows
   lose their annotation."
  [tsv-path]
  (let [f (clojure.java.io/file tsv-path)]
    (when (.exists f)
      (->> (str/split-lines (slurp f))
           (drop 1)
           (keep (fn [line]
                   (let [cells (str/split line #"\t" -1)]
                     (when (and (>= (count cells) 4) (seq (first cells)))
                       [(first cells)
                        (str "\t" (str/join "\t" (drop 3 cells)))]))))
           (into {})))))

(defn export-invites-tsv!
  "Read the current Invites tab and write a TSV at `tsv-path` with one row per
   (primary, secondary) pair that has both tokens minted. Columns:
     Name<TAB>Primary Link<TAB>+1 Link[<TAB>extras...]
   `base-url` is prefixed onto each `/login?token=…` URL. Any extra columns
   present in the existing file at `tsv-path` (e.g. a hand-maintained `send?`
   flag) are carried over by Name match. Returns the number of pairs written.
   No-op (and returns nil) when sheets isn't configured."
  ([] (export-invites-tsv! "invites.tsv" default-public-base-url))
  ([tsv-path] (export-invites-tsv! tsv-path default-public-base-url))
  ([tsv-path base-url]
   (when-let [rows (sheets/fetch-tab invites-tab)]
     (let [extras (read-extra-cols tsv-path)
           pairs (partition-all 2 (drop 1 rows))
           link (fn [tok lang]
                  (str base-url "/login?token=" tok
                       ;; "de" is the default, so we leave it implicit; any
                       ;; other set value (e.g. "en") rides along on the URL
                       ;; so the link auto-flips the language toggle.
                       (when (and (seq lang) (not= lang "de"))
                         (str "&lang=" lang))))
           lines (for [[p s] pairs
                       :let [p-tok (cell p 3)
                             s-tok (cell s 3)
                             p-lang (effective-lang (cell p 1) nil)
                             s-lang (effective-lang (cell s 1) p-lang)
                             name (cell p 0)]
                       :when (and (seq p-tok) (seq s-tok))]
                   (str name "\t" (link p-tok p-lang) "\t" (link s-tok s-lang)
                        (get extras name "")))
           out (str "Name\tPrimary Link\t+1 Link\n"
                    (str/join "\n" lines))]
       (spit tsv-path out)
       (count lines)))))

(defn mint-and-export!
  "Convenience entry point: mint any unminted pairs from the Invites sheet
   (writing tokens back to column D), then dump the current state to
   `invites.tsv` in the working dir. Returns {:sync … :exported N}."
  []
  (let [sync-stats (sync-from-sheet!)
        exported (export-invites-tsv!)]
    {:sync sync-stats :exported exported}))
