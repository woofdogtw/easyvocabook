# db-schema Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.

## Requirements

### Requirement: Schema tables
The system SHALL define the SQLite schema documented in `doc/schema.md` as the single source of
truth. Both Rust and Kotlin implementations SHALL apply the same SQL independently.

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
    transitivity   TEXT,                -- 'intransitive' | 'transitive' | 'ambitransitive'
    verb_group     TEXT,                -- 'godan' | 'ichidan' | 'irregular'
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
    value   TEXT    NOT NULL,
    reading TEXT
);

CREATE TABLE sentences (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id     INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
    sentence    TEXT    NOT NULL,
    translation TEXT
);
```

`transitivity` and `verb_group` describe the verb itself rather than one of its inflections,
which is why they are columns on `words` and not `word_forms` rows. Both are `NULL` for
non-verbs and for languages that do not distinguish them.

#### Scenario: Fresh database creation
- **WHEN** the app opens and no database file exists
- **THEN** the system creates `easyvocabook.db` with all tables shown above, sets
  `db_info.version` to the current schema version, and sets `db_info.last_modified = 0`

Note: `last_modified` is intentionally seeded as `0` (not the current time). Any real remote DB
will have `last_modified > 0`, so the first sync on a new machine will always download from remote
rather than overwriting it. See `specs/cloud-sync/spec.md` § Latest-wins conflict resolution.

#### Scenario: Version matches current
- **WHEN** the app opens an existing database whose `db_info.version` equals the current schema version
- **THEN** the database is opened normally without any migration

#### Scenario: Verb columns are null for non-verbs
- **WHEN** a noun or an English word is stored
- **THEN** its `transitivity` and `verb_group` are `NULL`

### Requirement: Foreign key enforcement
The system SHALL enable `PRAGMA foreign_keys = ON` on every database connection before any other
statement is executed. Neither `rusqlite` (Rust) nor Android's `SQLiteDatabase` (Kotlin) enables
foreign keys by default, so this PRAGMA must be applied explicitly on every connection open.

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
- **WHEN** the schema is created for the first time
- **THEN** all four indexes are present in the database

### Requirement: DB version migration guard
The system SHALL treat `db_info.version` as the sole authority on a database's schema version,
and SHALL check it on open to enforce upgrade/downgrade policies. Implementations SHALL NOT rely
on any storage-engine-specific version counter (such as SQLite's `PRAGMA user_version`) to decide
whether a migration is required, because files produced by the other platform do not maintain it.

No migration steps exist while the schema is still at its first version. The guard, the version
authority rule, and the write-back that records a completed migration SHALL nevertheless remain
in place, so that the first migration added after release has a tested path to run through.

#### Scenario: DB version is newer than app supports
- **WHEN** the app opens a database whose `db_info.version` is greater than the version the app supports
- **THEN** the app refuses to open it and shows an error: "Please update the app to open this file"

#### Scenario: DB version is older than current
- **WHEN** the app opens a database whose `db_info.version` is less than the current schema version
- **THEN** the app runs sequential migration SQL from the installed version to the current version

#### Scenario: Engine version counter disagrees with db_info
- **WHEN** the app opens a database whose engine-level version counter disagrees with
  `db_info.version` (as happens for a file created by the other platform)
- **THEN** the decision to migrate is based on `db_info.version` alone

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

### Requirement: Android DB file path
On Android, the database file SHALL be located at `filesDir/easyvocabook.db`
(the app's internal storage directory — no external storage permission required). The filename
`easyvocabook.db` is fixed and identical across all platforms, which is required for cloud sync
(the remote file has the same name on every platform).

#### Scenario: Android DB created in filesDir
- **WHEN** the Android app opens for the first time
- **THEN** the database is created at `context.filesDir/easyvocabook.db` with all tables of the
  current schema version and `db_info.last_modified = 0`
