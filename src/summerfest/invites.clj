(ns summerfest.invites
  "Generate magic-link invites by reading an 'Invites' tab in the workbook,
   minting one primary user + one +1 user per row, and writing the resulting
   links back into the sheet.

   Sheet layout (tab name: \"Invites\"):
     A: Invite Name       (admin fills for primary; auto-filled for +1)
     B: Type              (\"primary\" or \"+1\", written by us)
     C: Parent            (for +1 rows: the primary's invite name)
     D: Magic Link        (written by us)

   Workflow: admin types names into column A, one row per primary invitee, then
   runs `(sync-from-sheet! {:base-url \"https://host/path\"})`. Rows that already
   have a Magic Link are skipped, so re-running is safe."
  (:require [clojure.string :as str]
            [summerfest.db :as db]
            [summerfest.sheets :as sheets]))

(def ^:private invites-tab "Invites")

(def ^:private headers ["Invite Name" "Type" "Parent" "Magic Link"])

(defn- magic-link
  "Build the full login URL for a token. `base-url` is the host+base-path
   prefix without a trailing slash."
  [base-url token]
  (str base-url "/login?token=" token))

(defn- col-value [row idx]
  (some-> (get row idx) str))

(defn- needs-mint?
  "Process rows that have a name and an empty link, and are either marked
   primary or have no type yet. +1 rows (whether minted by us or manually
   added) are left alone — they're handled when their primary is processed."
  [row]
  (let [name (col-value row 0)
        type (some-> (col-value row 1) str/trim str/lower-case)
        link (col-value row 3)]
    (and (some-> name str/trim seq)
         (or (str/blank? type) (= "primary" type))
         (str/blank? link))))

(defn sync-from-sheet!
  "Read the Invites tab and mint a primary + one +1 for every row that has a
   Name but no Magic Link yet. Writes the primary's row back in place and
   appends the +1 as a new row, with the parent column linking the two.

   Required `:base-url` — the public host+base-path of the app, no trailing
   slash. Example: \"https://benni.example.com/summerfest\".

   Returns {:minted N :skipped N} (N counts row pairs)."
  [{:keys [base-url]}]
  (assert (and (string? base-url) (seq base-url)) ":base-url is required")
  (sheets/ensure-headers! invites-tab headers)
  (let [rows (or (sheets/fetch-tab invites-tab) [])
        data-rows (drop 1 rows)
        process? (mapv needs-mint? data-rows)
        to-append (atom [])]
    (doseq [[idx row] (map-indexed vector data-rows)
            :when (nth process? idx)]
      (let [sheet-row (+ 2 idx) ;; 1-based, plus header row
            invite-name (str/trim (col-value row 0))
            primary (db/create-user! :name invite-name)
            secondary (db/create-secondary-user! (:id primary))
            primary-link (magic-link base-url (:token primary))
            secondary-link (magic-link base-url (:token secondary))]
        (sheets/update-row! invites-tab sheet-row
                            [invite-name "primary" "" primary-link])
        (swap! to-append conj
               [(:name secondary) "+1" invite-name secondary-link])))
    (sheets/append-rows! invites-tab @to-append)
    {:minted (count @to-append)
     :skipped (- (count data-rows) (count @to-append))}))
