CREATE TABLE IF NOT EXISTS waitlist (
  email TEXT PRIMARY KEY,
  platform TEXT NOT NULL,
  created_at TEXT NOT NULL
);
