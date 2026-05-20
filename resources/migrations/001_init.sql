CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  display_name TEXT,
  name_confirmed BOOLEAN NOT NULL DEFAULT false,
  token UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  parent_user_id INT NULL REFERENCES users(id) ON DELETE CASCADE,
  group_size INT NOT NULL DEFAULT 2,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS users_parent_idx ON users (parent_user_id);

CREATE TABLE IF NOT EXISTS rsvps (
  user_id INT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  attending TEXT NOT NULL CHECK (attending IN ('yes','yes_plus_one','maybe','no')),
  additional_info TEXT NOT NULL DEFAULT '',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS photos (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users(id),
  filename TEXT NOT NULL,
  original_name TEXT,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users(id),
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
