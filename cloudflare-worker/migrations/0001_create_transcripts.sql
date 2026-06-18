CREATE TABLE IF NOT EXISTS transcripts (
  id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  text TEXT NOT NULL,
  source_duration_seconds INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transcripts_uid_created_at
  ON transcripts (uid, created_at DESC);
