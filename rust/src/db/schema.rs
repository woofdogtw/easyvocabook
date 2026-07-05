use rusqlite::{Connection, Result};

pub const CURRENT_VERSION: i64 = 1;

const CREATE_V1: &str = "
CREATE TABLE IF NOT EXISTS db_info (
    id               INTEGER PRIMARY KEY CHECK (id = 1),
    name             TEXT    NOT NULL,
    description      TEXT,
    default_language TEXT    NOT NULL DEFAULT 'en',
    version          INTEGER NOT NULL,
    last_modified    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS words (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    word           TEXT    NOT NULL,
    reading        TEXT,
    meaning        TEXT    NOT NULL,
    part_of_speech TEXT,
    note           TEXT,
    language       TEXT    NOT NULL,
    practice_count INTEGER NOT NULL DEFAULT 0,
    correct_count  INTEGER NOT NULL DEFAULT 0,
    created_at     INTEGER NOT NULL,
    practiced_at   INTEGER
);

CREATE TABLE IF NOT EXISTS word_meanings (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    meaning TEXT    NOT NULL,
    UNIQUE(word_id, meaning)
);

CREATE TABLE IF NOT EXISTS word_forms (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    label   TEXT    NOT NULL,
    value   TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS sentences (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id     INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence    TEXT    NOT NULL,
    translation TEXT
);

CREATE INDEX IF NOT EXISTS idx_words_language_reading ON words(language, reading);
CREATE INDEX IF NOT EXISTS idx_word_meanings_word_id  ON word_meanings(word_id);
CREATE INDEX IF NOT EXISTS idx_word_forms_word_id     ON word_forms(word_id);
CREATE INDEX IF NOT EXISTS idx_sentences_word_id      ON sentences(word_id);
";

/// Create schema v1 if it does not exist yet. Called on every fresh DB file.
pub fn create_schema(conn: &Connection) -> Result<()> {
    conn.execute_batch(CREATE_V1)
}

/// Seed the single db_info row on a brand-new database.
/// last_modified starts at 0 so a fresh local DB is always older than any remote,
/// ensuring the first sync downloads rather than overwrites remote data.
pub fn seed_db_info(conn: &Connection, name: &str) -> Result<()> {
    conn.execute(
        "INSERT OR IGNORE INTO db_info (id, name, version, last_modified) VALUES (1, ?1, ?2, 0)",
        rusqlite::params![name, CURRENT_VERSION],
    )?;
    Ok(())
}

/// Run all migrations from `installed_version` up to `CURRENT_VERSION`.
/// Returns an error if `installed_version > CURRENT_VERSION`.
pub fn migrate(_conn: &Connection, installed_version: i64) -> Result<()> {
    if installed_version > CURRENT_VERSION {
        return Err(rusqlite::Error::InvalidQuery);
    }
    // No migrations yet beyond v1 — add numbered steps here as schema evolves.
    // e.g.: if installed_version < 2 { conn.execute_batch(MIGRATE_V1_TO_V2)?; }
    Ok(())
}

pub fn now_epoch() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64
}
