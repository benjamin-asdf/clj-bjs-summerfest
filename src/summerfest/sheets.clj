(ns summerfest.sheets
  (:require [clj-http.client :as http]
            [cheshire.core :as json])
  (:import [com.google.auth.oauth2 ServiceAccountCredentials]
           [java.io FileInputStream]))

;; Google Sheets integration using service account auth.
;; Configure via environment variables:
;;   GOOGLE_SHEETS_ID       - the spreadsheet ID
;;   GOOGLE_SERVICE_KEY_JSON - path to decrypted service account key JSON file

(def ^:private sheets-id
  (or (System/getenv "GOOGLE_SHEETS_ID")
      "1NSnQagymFIDxLSXD2naspnyURWtR03yHkt33G5Ksfhc"))

(def ^:private key-path (System/getenv "GOOGLE_SERVICE_KEY_JSON"))

(def ^:private scopes ["https://www.googleapis.com/auth/spreadsheets"])

(defn- get-access-token []
  (when key-path
    (let [creds (-> (FileInputStream. ^String key-path)
                    (ServiceAccountCredentials/fromStream)
                    (.createScoped scopes))]
      (.refresh creds)
      (.getTokenValue (.getAccessToken creds)))))

(defn- api-url [path]
  (str "https://sheets.googleapis.com/v4/spreadsheets/" sheets-id path))

(defn- auth-headers [token]
  {"Authorization" (str "Bearer " token)
   "Content-Type" "application/json"})

(defn- token-or-throw []
  (or (get-access-token)
      (throw (ex-info "Google Sheets is not configured. Set GOOGLE_SERVICE_KEY_JSON to the path of the service account key." {}))))

(defn- col-letter
  "Map 1->A, 2->B, ..., 26->Z. Sufficient for our tabs which top out at column D."
  [n]
  (assert (<= 1 n 26) "col-letter only supports A..Z")
  (char (+ (dec (int \A)) n)))

(defn fetch-range
  "Read the rows in an A1 range (e.g. \"Invites!A:D\"). Returns a vector of row
   vectors (empty when the range has no values) or nil if Sheets is not
   configured."
  [a1-range]
  (when-let [token (get-access-token)]
    (let [resp (http/get (api-url (str "/values/" a1-range))
                         {:headers {"Authorization" (str "Bearer " token)}
                          :as :json})]
      (get-in resp [:body :values] []))))

(defn fetch-tab
  "Convenience: read all rows from a named tab."
  [tab-name]
  (fetch-range tab-name))

(defn fetch-sheet!
  "Legacy: read all rows. Defaults to \"Sheet1\". Kept for back-compat."
  ([] (fetch-sheet! nil))
  ([range]
   (fetch-range (or range "Sheet1"))))

(defn update-range!
  "Overwrite an A1 range with the given rows. Returns the API response body."
  [a1-range rows]
  (let [token (token-or-throw)]
    (-> (http/put (api-url (str "/values/" a1-range))
                  {:headers (auth-headers token)
                   :query-params {"valueInputOption" "RAW"}
                   :body (json/generate-string {:values rows})
                   :as :json})
        :body)))

(defn update-row!
  "Write `row-vals` into `tab-name` at the given 1-based row number, starting at
   column A. Sized to len(row-vals)."
  [tab-name row-number row-vals]
  (let [end (col-letter (count row-vals))
        range (str tab-name "!A" row-number ":" end row-number)]
    (update-range! range [row-vals])))

(defn append-rows!
  "Append rows to the bottom of a tab. Returns the API response body."
  [tab-name rows]
  (when (seq rows)
    (let [token (token-or-throw)]
      (-> (http/post (api-url (str "/values/" tab-name "!A:Z:append"))
                     {:headers (auth-headers token)
                      :query-params {"valueInputOption" "RAW"
                                     "insertDataOption" "INSERT_ROWS"}
                      :body (json/generate-string {:values rows})
                      :as :json})
          :body))))

(defn ensure-tab!
  "If the workbook doesn't have a tab named `tab-name`, create it. No-op
   otherwise. Returns truthy when a new tab was added."
  [tab-name]
  (let [token (token-or-throw)
        sheets (-> (http/get (api-url "?fields=sheets.properties.title")
                             {:headers {"Authorization" (str "Bearer " token)}
                              :as :json})
                   :body :sheets)
        existing (set (map #(get-in % [:properties :title]) sheets))]
    (when-not (existing tab-name)
      (http/post (api-url ":batchUpdate")
                 {:headers (auth-headers token)
                  :body (json/generate-string
                         {:requests [{:addSheet {:properties {:title tab-name}}}]})
                  :as :json})
      true)))

(defn ensure-headers!
  "Write `headers` into columns A..N of row 1 in `tab-name` if those cells
   aren't already an exact match (N = (count headers)). Columns past N are
   left untouched so the admin can add their own columns alongside the ones
   the code manages. Creates the tab if missing."
  [tab-name headers]
  (ensure-tab! tab-name)
  (let [n (count headers)
        existing (fetch-tab tab-name)
        first-row (or (some-> existing first vec) [])
        managed (vec (take n (concat first-row (repeat ""))))]
    (when (not= managed (mapv str headers))
      (update-range! (str tab-name "!A1") [headers]))))

