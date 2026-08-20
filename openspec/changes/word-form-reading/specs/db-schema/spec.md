## RENAMED Requirements

- FROM: `### Requirement: Schema v1 tables`
- TO: `### Requirement: Schema tables`

## MODIFIED Requirements

### Requirement: Schema tables
The system SHALL define the SQLite schema documented in `doc/schema.md` as the single source of
truth. Both Rust and Kotlin implementations SHALL apply the same SQL independently. The block
below reflects the current schema version; the v1→v2 delta is specified in
§ Schema v2 migration — word_forms.reading.

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

#### Scenario: DB version is newer than app supports
- **WHEN** the app opens a database whose `db_info.version` is greater than the version the app supports
- **THEN** the app refuses to open it and shows an error: "Please update the app to open this file"

#### Scenario: DB version is older than current (future)
- **WHEN** the app opens a database whose `db_info.version` is less than the current schema version
- **THEN** the app runs sequential migration SQL from the installed version to the current version

#### Scenario: Engine version counter disagrees with db_info
- **WHEN** the app opens a database whose `db_info.version` is 1 but whose engine-level version
  counter reports a different value (as happens for a file created by the other platform)
- **THEN** the v1→v2 migration is still applied, based on `db_info.version`

### Requirement: Android DB file path
On Android, the database file SHALL be located at `filesDir/easyvocabook.db`
(the app's internal storage directory — no external storage permission required). The filename
`easyvocabook.db` is fixed and identical across all platforms, which is required for cloud sync
(the remote file has the same name on every platform).

#### Scenario: Android DB created in filesDir
- **WHEN** the Android app opens for the first time
- **THEN** the database is created at `context.filesDir/easyvocabook.db` with all tables of the
  current schema version and `db_info.last_modified = 0`

## ADDED Requirements

### Requirement: Schema v2 migration — word_forms.reading
The system SHALL migrate a version 1 database to version 2 by adding a nullable `reading`
column to `word_forms`, without altering or deleting any existing row. Both implementations
SHALL apply the same statement:

```sql
ALTER TABLE word_forms ADD COLUMN reading TEXT;
```

On completion the migration SHALL set `db_info.version = 2`, so that the migration runs exactly
once and the file advertises its true version to the other platform. Migration steps SHALL be
guarded by the installed version, and re-opening an already-migrated database SHALL NOT re-run
them. Existing `word_forms` rows SHALL retain their `label` and `value` and receive
`reading = NULL`. Rows whose label was previously used to carry a reading (for example
`hiragana` or `phonetic`) SHALL NOT be rewritten, relabelled, or merged into the new column.

#### Scenario: v1 database opened by a v2 app
- **WHEN** the app opens a database with `db_info.version = 1`
- **THEN** the `reading` column is added to `word_forms`, every existing row keeps its `label`
  and `value` with `reading = NULL`, and `db_info.version` becomes 2

#### Scenario: Migration runs only once
- **WHEN** a migrated database is opened again
- **THEN** no migration statement is re-executed and the open succeeds without a duplicate-column error

#### Scenario: Legacy reading-carrying rows are left intact
- **WHEN** a v1 database contains a `word_forms` row with label `hiragana`
- **THEN** after migration that row still exists unchanged as an ordinary word form

#### Scenario: Older database received from sync is migrated on open
- **WHEN** an updated app replaces its local file with a downloaded `db_info.version = 1` database
- **AND** the database is opened after the sync completes
- **THEN** the v1→v2 migration is applied to the downloaded file before it is used
