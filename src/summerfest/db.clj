(ns summerfest.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [clojure.java.io :as io]))

(def db-spec
  {:dbtype "postgresql"
   :dbname (or (System/getenv "DB_NAME") "summerfest")
   :host (or (System/getenv "DB_HOST") "localhost")
   :port (parse-long (or (System/getenv "DB_PORT") "5432"))
   :user (or (System/getenv "DB_USER") "summerfest")
   :password (or (System/getenv "DB_PASSWORD") "summerfest")})

(defonce ds (delay (jdbc/get-datasource db-spec)))

(def opts {:builder-fn rs/as-unqualified-kebab-maps})

(defn q [sql & params]
  (jdbc/execute! @ds (into [sql] params) opts))

(defn q1 [sql & params]
  (jdbc/execute-one! @ds (into [sql] params) opts))

;; Migrations

(defn migrate! []
  (let [sql (slurp (io/resource "migrations/001_init.sql"))]
    (jdbc/execute! @ds [sql]))
  (println "Migrations applied."))

;; Users

(defn create-user!
  "Create a user with a magic link token. Returns the user map with token.
   Usage from REPL: (create-user! \"Alice & Bob\") or (create-user! \"Solo Sam\" :group-size 1)"
  [name & {:keys [group-size] :or {group-size 2}}]
  (q1 "INSERT INTO users (name, group_size) VALUES (?, ?) RETURNING *" name group-size))

(defn get-user-by-token [token]
  (q1 "SELECT * FROM users WHERE token = ?::uuid" (str token)))

(defn get-user-by-id [id]
  (q1 "SELECT * FROM users WHERE id = ?" id))

(defn list-users []
  (q "SELECT id, name, token, group_size, created_at FROM users ORDER BY created_at"))

;; RSVP

(defn upsert-rsvp! [user-id attending additional-info]
  (q1 "INSERT INTO rsvps (user_id, attending, additional_info, updated_at)
       VALUES (?, ?, ?, now())
       ON CONFLICT (user_id) DO UPDATE
       SET attending = EXCLUDED.attending, additional_info = EXCLUDED.additional_info, updated_at = now()
       RETURNING *"
      user-id attending (or additional-info "")))

(defn get-rsvp [user-id]
  (q1 "SELECT * FROM rsvps WHERE user_id = ?" user-id))

(defn get-all-rsvps []
  (q "SELECT u.name, u.group_size, r.attending, r.additional_info, r.updated_at
      FROM rsvps r JOIN users u ON r.user_id = u.id
      ORDER BY r.updated_at DESC"))

;; Photos

(defn save-photo! [user-id filename original-name]
  (q1 "INSERT INTO photos (user_id, filename, original_name) VALUES (?, ?, ?) RETURNING *"
      user-id filename original-name))

(defn get-all-photos []
  (q "SELECT p.*, u.name as user_name
      FROM photos p JOIN users u ON p.user_id = u.id
      ORDER BY p.uploaded_at DESC"))

;; Chat

(defn save-message! [user-id message]
  (q1 "INSERT INTO chat_messages (user_id, message) VALUES (?, ?) RETURNING *"
      user-id message))

(defn get-recent-messages
  ([] (get-recent-messages 100))
  ([limit]
   (reverse
    (q "SELECT m.*, u.name as user_name
         FROM chat_messages m JOIN users u ON m.user_id = u.id
         ORDER BY m.created_at DESC LIMIT ?" limit))))

(defn get-messages-after [after-id]
  (q "SELECT m.*, u.name as user_name
      FROM chat_messages m JOIN users u ON m.user_id = u.id
      WHERE m.id > ? ORDER BY m.created_at ASC" after-id))
