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

(defn fetch-sheet!
  "Fetch all rows from the sheet. Returns a vector of row vectors.
   Optional range param defaults to the whole sheet."
  ([] (fetch-sheet! nil))
  ([range]
   (when-let [token (get-access-token)]
     (let [range-part (or range "Sheet1")
           url (str "https://sheets.googleapis.com/v4/spreadsheets/"
                    sheets-id "/values/" range-part)
           resp (http/get url
                          {:headers {"Authorization" (str "Bearer " token)}
                           :as :json})]
       (get-in resp [:body :values])))))

(defn sync-rsvp!
  "Sync an RSVP to Google Sheets. Silently skips if not configured."
  [user-name group-size attending additional-info]
  (when-let [token (get-access-token)]
    (try
      (let [url (str "https://sheets.googleapis.com/v4/spreadsheets/"
                     sheets-id "/values/A:E:append")
            row [user-name
                 (str group-size)
                 (if attending "Yes" "No")
                 (or additional-info "")
                 (.toString (java.time.Instant/now))]]
        (http/post url
                   {:headers {"Authorization" (str "Bearer " token)
                              "Content-Type" "application/json"}
                    :query-params {"valueInputOption" "RAW"
                                   "insertDataOption" "INSERT_ROWS"}
                    :body (json/generate-string {:values [row]})}))
      (catch Exception e
        (println "Google Sheets sync error:" (.getMessage e))))))
