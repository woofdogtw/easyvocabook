## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Schema v2 migration — word_forms.reading
**Reason**: The schema is being rewritten at version 1 rather than versioned forward. The
`reading` column it added now appears directly in the version 1 DDL, so there is nothing left to
migrate. Version numbers are held in reserve until the app is released and real users have
databases worth upgrading.

**Migration**: Existing databases are discarded and rebuilt from a regenerated seed. There is a
single user, whose practice statistics are accepted as lost. Any surviving version 2 file must
be deleted — on the desktop, on Android via clearing app data, and on the cloud remote —
because the version guard refuses to open a file newer than the app supports.
