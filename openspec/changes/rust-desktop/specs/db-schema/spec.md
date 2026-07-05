## ADDED Requirements

### Requirement: Schema v1 tables
The system SHALL define SQLite schema version 1 with the following tables, documented in
`doc/schema.md` as the single source of truth. Both Rust and Kotlin implementations SHALL apply
the same SQL independently.

```sql
CREATE TABLE db_info (
    id             INTEGER PRIMARY KEY CHECK (id = 1),
    name           TEXT    NOT NULL,
    description    TEXT,
    default_language TEXT  NOT NULL DEFAULT 'en',
    version        INTEGER NOT NULL,
    last_modified  INTEGER NOT NULL   -- Unix epoch i64 seconds
);

CREATE TABLE words (
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

CREATE TABLE word_meanings (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    meaning TEXT    NOT NULL,
    UNIQUE(word_id, meaning)
);

CREATE TABLE word_forms (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    label   TEXT    NOT NULL,
    value   TEXT    NOT NULL
);

CREATE TABLE sentences (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id     INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence    TEXT    NOT NULL,
    translation TEXT
);
```

#### Scenario: Fresh database creation
- **WHEN** the app opens and no database file exists
- **THEN** the system creates `easyvocabook.db` with all v1 tables, sets `db_info.version = 1`,
  and sets `db_info.last_modified = 0`

Note: `last_modified` is intentionally seeded as `0` (not the current time). Any real remote DB
will have `last_modified > 0`, so the first sync on a new machine will always download from remote
rather than overwriting it. See `specs/cloud-sync/spec.md` § Latest-wins conflict resolution.

#### Scenario: Version matches current
- **WHEN** the app opens an existing database with `db_info.version = 1`
- **THEN** the database is opened normally without any migration

### Requirement: Foreign key enforcement
The system SHALL enable `PRAGMA foreign_keys = ON` on every database connection before any other
statement is executed.

#### Scenario: Deleting a word cascades to sub-tables
- **WHEN** a word row is deleted
- **THEN** all associated `word_meanings`, `word_forms`, and `sentences` rows are deleted automatically

### Requirement: db_info single-row constraint
The `db_info` table SHALL enforce a single row via `CHECK (id = 1)`. Any attempt to insert a
second row SHALL be rejected by SQLite.

#### Scenario: Attempting a second db_info row
- **WHEN** an INSERT into `db_info` with any id other than 1 is attempted
- **THEN** SQLite returns a constraint error

### Requirement: Indexes for common queries
The system SHALL create indexes to support efficient filtering and join operations:

```sql
CREATE INDEX idx_words_language_reading ON words(language, reading);
CREATE INDEX idx_word_meanings_word_id  ON word_meanings(word_id);
CREATE INDEX idx_word_forms_word_id     ON word_forms(word_id);
CREATE INDEX idx_sentences_word_id      ON sentences(word_id);
```

#### Scenario: Index creation on schema init
- **WHEN** schema v1 is created for the first time
- **THEN** all four indexes are present in the database

### Requirement: DB version migration guard
The system SHALL check `db_info.version` on open and enforce upgrade/downgrade policies.

#### Scenario: DB version is newer than app supports
- **WHEN** the app opens a database with `db_info.version > 1`
- **THEN** the app refuses to open it and shows an error: "Please update the app to open this file"

#### Scenario: DB version is older than current (future)
- **WHEN** the app opens a database with `db_info.version < current`
- **THEN** the app runs sequential migration SQL from the installed version to the current version

### Requirement: Timestamps as Unix epoch i64
All date/time columns (`last_modified`, `created_at`, `practiced_at`) SHALL store values as
Unix epoch seconds (i64). No timezone information is stored.

#### Scenario: Creating a word sets created_at
- **WHEN** a new word is inserted
- **THEN** `created_at` is set to the current Unix epoch second; `practiced_at` is NULL

### Requirement: part_of_speech stored as language-neutral key
`words.part_of_speech` SHALL store a language-neutral ASCII key (e.g., `noun`, `verb`, `i-adj`),
not a localized display string.

#### Scenario: Japanese word with i-adj part of speech
- **WHEN** a Japanese word with type 「い形容詞」is saved
- **THEN** `part_of_speech` contains the string `i-adj`, not 「い形容詞」
