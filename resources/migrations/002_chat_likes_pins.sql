CREATE TABLE IF NOT EXISTS chat_likes (
  user_id INT NOT NULL REFERENCES users(id),
  message_id INT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, message_id)
);

CREATE TABLE IF NOT EXISTS chat_pins (
  message_id INT PRIMARY KEY REFERENCES chat_messages(id) ON DELETE CASCADE,
  pinned_by INT NOT NULL REFERENCES users(id),
  pinned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
