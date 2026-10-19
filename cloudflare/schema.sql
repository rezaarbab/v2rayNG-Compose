-- ARBAB Panel — D1 schema (god-db)
-- Run:  wrangler d1 execute god-db --file=schema.sql --remote

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
  connected  INTEGER DEFAULT 0
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
  active   INTEGER DEFAULT 1
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

INSERT OR IGNORE INTO popup_seen (device, popup) VALUES ('__init__', 1);
