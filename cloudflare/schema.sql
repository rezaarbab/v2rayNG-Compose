-- ARBAB Panel — D1 schema
-- Run:  wrangler d1 execute arbab-db --file=schema.sql --remote
--
-- NOTE: this file is idempotent - every statement is IF NOT EXISTS / OR IGNORE, so it can be
--       re-run any number of times. It used to end with ALTER TABLE statements that failed with
--       "duplicate column name" on databases that already had them, and wrangler --file stops at
--       the first error, silently skipping everything after it. Those ALTERs now live in the
--       worker's ensureSchema(), which runs each statement on its own and ignores that error.

CREATE TABLE IF NOT EXISTS settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at INTEGER
);

CREATE TABLE IF NOT EXISTS devices (
  id         TEXT PRIMARY KEY,
  model      TEXT DEFAULT '',
  app_ver    TEXT DEFAULT '',
  first_seen INTEGER,
  last_seen  INTEGER,
  ip         TEXT DEFAULT '',
  connected  INTEGER DEFAULT 0,
  info       TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS events (
  device TEXT,
  type   TEXT,
  ts     INTEGER
);
CREATE INDEX IF NOT EXISTS idx_events_ts ON events (ts);

CREATE TABLE IF NOT EXISTS servers (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  name     TEXT,
  address  TEXT,
  port     INTEGER DEFAULT 443,
  link     TEXT DEFAULT '',
  category TEXT DEFAULT '',
  ord      INTEGER DEFAULT 99,
  active   INTEGER DEFAULT 1,
  msg      TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS popup_seen (
  device TEXT,
  popup  INTEGER,
  PRIMARY KEY (device, popup)
);

CREATE TABLE IF NOT EXISTS notifs (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  title  TEXT,
  body   TEXT,
  ts     INTEGER,
  active INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS acks (
  device TEXT,
  popup  TEXT,
  kind   TEXT,
  ts     INTEGER,
  PRIMARY KEY (device, popup, kind)
);

CREATE TABLE IF NOT EXISTS blob_sessions (
  device       TEXT PRIMARY KEY,
  key          TEXT,
  exp          INTEGER,
  rot          INTEGER DEFAULT 0,
  last_payload TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS blob_tok (
  device TEXT PRIMARY KEY,
  ts     INTEGER DEFAULT 0
);

INSERT OR IGNORE INTO popup_seen (device, popup) VALUES ('__init__', 1);

-- admin-delivered files (worker ensureSchema also creates these; schema.sql was missing them)
CREATE TABLE IF NOT EXISTS payload_files (
  id     INTEGER PRIMARY KEY AUTOINCREMENT,
  name   TEXT,
  mime   TEXT DEFAULT '',
  size   INTEGER DEFAULT 0,
  title  TEXT DEFAULT '',
  b64    TEXT,
  active INTEGER DEFAULT 1,
  ts     INTEGER
);

CREATE TABLE IF NOT EXISTS payload_seen (
  device TEXT,
  pid    INTEGER,
  ts     INTEGER,
  PRIMARY KEY (device, pid)
);
