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
  (doseq [f ["migrations/001_init.sql"
             "migrations/002_chat_likes_pins.sql"
             "migrations/003_display_name.sql"]]
    (jdbc/execute! @ds [(slurp (io/resource f))]))
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

(defn update-display-name!
  "Set or clear the user's display name. Empty/blank clears it (falls back to original name)."
  [user-id display-name]
  (let [trimmed (some-> display-name clojure.string/trim)
        value (when (seq trimmed) trimmed)]
    (q1 "UPDATE users SET display_name = ? WHERE id = ? RETURNING *"
        value user-id)))

(defn effective-name
  "Display name if set, else the original name."
  [user]
  (or (:display-name user) (:name user)))

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
  (q "SELECT p.*, COALESCE(u.display_name, u.name) AS user_name
      FROM photos p JOIN users u ON p.user_id = u.id
      ORDER BY p.uploaded_at DESC"))

(defn photo-count []
  (or (:n (q1 "SELECT COUNT(*)::int AS n FROM photos")) 0))

(defn delete-photo! [id]
  (q1 "DELETE FROM photos WHERE id = ?" id))

(defn delete-orphan-photos!
  "Delete photo rows whose file is missing or zero-bytes in upload-dir.
   Returns a vector of {:id, :filename, :reason} for what was removed.
   Call from REPL after deploying."
  [upload-dir]
  (let [dir (io/file upload-dir)
        rows (q "SELECT id, filename FROM photos")
        bad (for [{:keys [id filename]} rows
                  :let [f (io/file dir filename)
                        reason (cond
                                 (not (.exists f)) :missing
                                 (zero? (.length f)) :empty
                                 (not (re-find #"\.[A-Za-z0-9]{1,8}$" (or filename "")))
                                 :no-ext)]
                  :when reason]
              {:id id :filename filename :reason reason})]
    (doseq [{:keys [id filename]} bad]
      (delete-photo! id)
      (let [f (io/file dir filename)]
        (when (and (.exists f) (zero? (.length f)))
          (.delete f))))
    (vec bad)))

;; Chat

(defn save-message! [user-id message]
  (q1 "INSERT INTO chat_messages (user_id, message) VALUES (?, ?) RETURNING *"
      user-id message))

(defn seed-chat-messages!
  "REPL helper: insert n fake chat messages spread across all users for visual
   testing of the chat list. Messages are timestamped in ascending order so
   recent ones appear last."
  [n]
  (let [user-ids (mapv :id (list-users))
        samples ["Hey everyone! 👋"
                 "Looking forward to it"
                 "What's the weather forecast?"
                 "Can I bring a salad?"
                 "We'll be there by 6"
                 "Just got off work, on my way!"
                 "Is parking easy to find?"
                 "Bringing the kids — hope that's ok"
                 "Anyone want to carpool?"
                 "🎉🎉🎉"
                 "This is going to be amazing"
                 "Love the playlist"
                 "Save me a piece of cake!"
                 "Running 10 min late"
                 "Where do I put presents?"
                 "Such a fun afternoon"
                 "Best fest ever"
                 "Thanks for hosting"
                 "Found a great parking spot"
                 "Has anyone tried the punch yet?"]
        now (System/currentTimeMillis)]
    (assert (seq user-ids) "Need at least one user before seeding messages.")
    (dotimes [i n]
      (let [uid (nth user-ids (mod i (count user-ids)))
            base (rand-nth samples)
            msg (str base " (#" (inc i) ")")
            ts (java.sql.Timestamp. (- now (* (- n i) 1000)))]
        (q1 "INSERT INTO chat_messages (user_id, message, created_at) VALUES (?, ?, ?)"
            uid msg ts)))
    n))

(def chat-page-size 1000)

(defn get-recent-messages
  ([current-user-id] (get-recent-messages current-user-id chat-page-size))
  ([current-user-id limit]
   (reverse
    (q "SELECT m.*, COALESCE(u.display_name, u.name) AS user_name,
               (SELECT COUNT(*) FROM chat_likes cl WHERE cl.message_id = m.id) as like_count,
               EXISTS(SELECT 1 FROM chat_likes cl WHERE cl.message_id = m.id AND cl.user_id = ?) as liked_by_me,
               EXISTS(SELECT 1 FROM chat_pins cp WHERE cp.message_id = m.id) as pinned
        FROM chat_messages m JOIN users u ON m.user_id = u.id
        ORDER BY m.created_at DESC LIMIT ?" current-user-id limit))))

(defn get-older-messages
  "Messages strictly older than `before-id`, oldest-first, up to `limit`."
  ([current-user-id before-id] (get-older-messages current-user-id before-id chat-page-size))
  ([current-user-id before-id limit]
   (reverse
    (q "SELECT m.*, COALESCE(u.display_name, u.name) AS user_name,
               (SELECT COUNT(*) FROM chat_likes cl WHERE cl.message_id = m.id) as like_count,
               EXISTS(SELECT 1 FROM chat_likes cl WHERE cl.message_id = m.id AND cl.user_id = ?) as liked_by_me,
               EXISTS(SELECT 1 FROM chat_pins cp WHERE cp.message_id = m.id) as pinned
        FROM chat_messages m JOIN users u ON m.user_id = u.id
        WHERE m.id < ?
        ORDER BY m.created_at DESC LIMIT ?" current-user-id before-id limit))))

(defn older-exists?
  "Is there at least one message older than `before-id`?"
  [before-id]
  (boolean (q1 "SELECT 1 FROM chat_messages WHERE id < ? LIMIT 1" before-id)))

(defn get-message
  "Single message by id, with the same shape (like_count, liked_by_me, pinned, user_name)
   as `get-recent-messages` rows. `current-user-id` parameterizes liked_by_me."
  [current-user-id msg-id]
  (q1 "SELECT m.*, COALESCE(u.display_name, u.name) AS user_name,
              (SELECT COUNT(*) FROM chat_likes cl WHERE cl.message_id = m.id) as like_count,
              EXISTS(SELECT 1 FROM chat_likes cl WHERE cl.message_id = m.id AND cl.user_id = ?) as liked_by_me,
              EXISTS(SELECT 1 FROM chat_pins cp WHERE cp.message_id = m.id) as pinned
       FROM chat_messages m JOIN users u ON m.user_id = u.id
       WHERE m.id = ?" current-user-id msg-id))

(defn get-pinned-messages [current-user-id]
  (q "SELECT m.*, COALESCE(u.display_name, u.name) AS user_name, cp.pinned_at,
             (SELECT COUNT(*) FROM chat_likes cl WHERE cl.message_id = m.id) as like_count,
             EXISTS(SELECT 1 FROM chat_likes cl WHERE cl.message_id = m.id AND cl.user_id = ?) as liked_by_me
      FROM chat_pins cp
      JOIN chat_messages m ON cp.message_id = m.id
      JOIN users u ON m.user_id = u.id
      ORDER BY cp.pinned_at DESC" current-user-id))

;; Likes

(defn toggle-like! [user-id message-id]
  (let [existing (q1 "SELECT 1 FROM chat_likes WHERE user_id = ? AND message_id = ?" user-id message-id)]
    (if existing
      (do (q1 "DELETE FROM chat_likes WHERE user_id = ? AND message_id = ?" user-id message-id) false)
      (do (q1 "INSERT INTO chat_likes (user_id, message_id) VALUES (?, ?)" user-id message-id) true))))

;; Pins

(defn toggle-pin! [user-id message-id]
  (let [existing (q1 "SELECT 1 FROM chat_pins WHERE message_id = ?" message-id)]
    (if existing
      (do (q1 "DELETE FROM chat_pins WHERE message_id = ?" message-id) false)
      (do (q1 "INSERT INTO chat_pins (message_id, pinned_by) VALUES (?, ?)" message-id user-id) true))))
