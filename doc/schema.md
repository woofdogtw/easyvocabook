# EasyVocaBook Database Schema

## Overview

Single SQLite file: `easyvocabook.db`  
Platform path: `{data_local_dir}/easyvocabook/easyvocabook.db`  
Current version: **2**

All date/time values are **Unix epoch seconds** stored as `INTEGER` (`i64`). No timezone info is stored.

## Version policy

`db_info.version` is the **only** authority on a file's schema version.

- `db_info.version` is checked on every open.
- `version > CURRENT_VERSION` → refuse to open; show "Please update the app to open this file".
- `version < CURRENT_VERSION` → run sequential migration SQL.
- `version == CURRENT_VERSION` → open normally.

**`PRAGMA user_version` is not maintained by either platform and MUST NOT be branched on.**
The Rust desktop app never writes it, so every file it produces reports `user_version = 0`.
On Android this matters: `SQLiteOpenHelper` routes `onCreate`/`onUpgrade` off that pragma, so a
synced desktop file would be sent to `onCreate` — whose statements are all
`CREATE TABLE IF NOT EXISTS` and therefore no-ops — and would silently skip its migration.
Android must run its migration from `db_info.version` instead (see below).

## Migration strategy

Each schema version adds a numbered migration step. On open the code runs all migrations from the
installed version up to `CURRENT_VERSION` in order. Migrations are additive-first (new columns
with defaults, new tables) to stay compatible with the Android implementation.

Two rules keep a migration from running twice:

1. Each step is guarded by the installed version, and the migration writes
   `db_info.version = CURRENT_VERSION` when it finishes. Without the write-back the step would
   re-run on the next open — the Rust `migrate()` is called on every open — and fail.
2. Each step is additionally idempotent (for example, checking whether a column already exists
   before adding it), because Android can reach the same step from two code paths in one open.

## Migrations

### v1 → v2 — `word_forms.reading`

Adds an optional per-form reading. Purely additive: no existing row is rewritten, relabelled, or
deleted. Rows whose label was previously used to carry a reading (such as `hiragana` or
`phonetic`) are left exactly as they are.

```sql
ALTER TABLE word_forms ADD COLUMN reading TEXT;
UPDATE db_info SET version = 2 WHERE id = 1;
```

## Tables

### db_info

Single-row metadata table. The `CHECK (id = 1)` constraint enforces at most one row.

```sql
CREATE TABLE db_info (
    id               INTEGER PRIMARY KEY CHECK (id = 1),
    name             TEXT    NOT NULL,
    description      TEXT,
    default_language TEXT    NOT NULL DEFAULT 'en',
    version          INTEGER NOT NULL,
    last_modified    INTEGER NOT NULL   -- Unix epoch seconds
);
```

### words

One row per vocabulary entry.

```sql
CREATE TABLE words (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    word           TEXT    NOT NULL,
    reading        TEXT,                -- kana / IPA / pronunciation
    meaning        TEXT    NOT NULL,    -- primary (display) meaning
    part_of_speech TEXT,               -- language-neutral key: noun, verb, i-adj, …
    note           TEXT,
    language       TEXT    NOT NULL,   -- ISO code: en, ja, …
    practice_count INTEGER NOT NULL DEFAULT 0,
    correct_count  INTEGER NOT NULL DEFAULT 0,
    created_at     INTEGER NOT NULL,   -- Unix epoch seconds
    practiced_at   INTEGER            -- NULL until first attempt
);
```

### word_meanings

Additional meanings for a word (0..N). The primary meaning lives in `words.meaning`.
The `UNIQUE(word_id, meaning)` constraint silently deduplicates on `INSERT OR IGNORE`.

```sql
CREATE TABLE word_meanings (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    meaning TEXT    NOT NULL,
    UNIQUE(word_id, meaning)
);
```

### word_forms

Conjugation/inflection data for a word (0..N). Labels are language-specific constants
(see `db::labels`). Custom labels outside the canonical set are accepted without error.
Each form carries its own optional `reading`, so every form — not just the base word — can
record a pronunciation. Value and reading are trimmed on save; a reading that is empty after
trimming is stored as `NULL`.

```sql
CREATE TABLE word_forms (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    label   TEXT    NOT NULL,
    value   TEXT    NOT NULL,
    reading TEXT                 -- kana / pronunciation for this form (v2+)
);
```

### sentences

Example sentences for a word (0..N).

```sql
CREATE TABLE sentences (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id     INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence    TEXT    NOT NULL,
    translation TEXT
);
```

## Indexes

```sql
CREATE INDEX idx_words_language_reading ON words(language, reading);
CREATE INDEX idx_word_meanings_word_id  ON word_meanings(word_id);
CREATE INDEX idx_word_forms_word_id     ON word_forms(word_id);
CREATE INDEX idx_sentences_word_id      ON sentences(word_id);
```

## Foreign key enforcement

`PRAGMA foreign_keys = ON` is executed on **every** connection immediately after opening,
before any other statement. `rusqlite` does not enable this by default.

## Sync notes

`db_info.last_modified` is bumped (to current Unix epoch second) on every write that changes
vocabulary data: create/update/delete word, clear practice stats.

Sync uses a **latest-wins** model: compare the local `db_info.last_modified` against the
remote `db_info.last_modified` (read from inside the downloaded remote file — never from
filesystem mtime or cloud storage metadata). Whichever is larger wins; the other side is
overwritten. A fresh install seeds `last_modified = 0`, ensuring the first sync always
downloads the remote copy.
