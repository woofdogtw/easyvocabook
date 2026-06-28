## ADDED Requirements

### Requirement: DbTableBase interface
`DbTableBase` is the shared interface that abstracts all read and write operations over the
vocabulary data. Both `DbTableSQLite` and `DbTableMemory` SHALL implement this interface.

The interface SHALL expose the following operations:
- **get_book_info**: return the database metadata record (`BookInfo`)
- **update_book_info**: write database metadata
- **list_words(filter)**: return all fully-aggregated word entries matching the given `WordFilter`
  (language code + optional text search); each entry includes its `word_meanings`, `word_forms`,
  and `sentences`
- **get_word(id)**: return a single fully-aggregated word entry by ID, or nothing if not found
- **create_word(data)**: insert a new word with all sub-records; return the new word ID
- **update_word(id, data)**: replace the word and all sub-records
- **delete_word(id)**: remove the word and all sub-records
- **clear_practice_stats**: reset all practice counters

`WordEntry` is a fully-aggregated record containing the word row plus its `word_meanings`,
`word_forms`, and `sentences` sub-records.

#### Scenario: Both implementations satisfy the same interface
- **WHEN** `DbTableSQLite` and `DbTableMemory` are compiled
- **THEN** both implement `DbTableBase` with no missing methods

### Requirement: DbTableSQLite implementation
`DbTableSQLite` is the SQLite-backed source of truth. It implements `DbTableBase` by executing SQL
against the database file. Blocking DB operations SHALL NOT block the UI runtime; the caller is
responsible for dispatching them off the UI thread.

`DbTableSQLite` SHALL:
- Enable `PRAGMA foreign_keys = ON` immediately after opening the connection
- Run schema creation or migration SQL on first open
- Wrap multi-step writes (e.g., insert word + sub-records) in a single transaction

#### Scenario: Write word with sub-records atomically
- **WHEN** `create_word` is called with meanings, forms, and sentences
- **THEN** all rows are inserted in a single transaction; if any step fails, all are rolled back

#### Scenario: SQL tests use an isolated database
- **WHEN** a `DbTableSQLite` test runs
- **THEN** it uses an isolated SQLite file that does not affect any real data and is cleaned up after the test

### Requirement: DbTableMemory implementation
`DbTableMemory` SHALL hold all word entries in memory and implement `DbTableBase` by operating
on that in-memory collection.

`DbTableMemory` SHALL support:
- Loading all entries from `DbTableSQLite` on app startup (full-aggregate load)
- Incremental update after each write (add/update/remove the affected entry in-memory)
- Filter and search over the in-memory collection without any SQL

#### Scenario: Memory filter returns matching words
- **WHEN** `list_words` is called with `language = "ja"` on a `DbTableMemory` containing mixed EN and JA entries
- **THEN** only JA entries are returned

#### Scenario: Text search covers all meanings
- **WHEN** `list_words` is called with a text filter matching a secondary meaning in `word_meanings`
- **THEN** the word is included in results

### Requirement: Full-aggregate cache load on startup
On application startup, the system SHALL load ALL words with their complete sub-records
(word_meanings, word_forms, sentences) into `DbTableMemory` in a single pass before rendering
any UI.

#### Scenario: Startup with 1000 words
- **WHEN** the database contains 1000 words each with meanings, forms, and sentences
- **THEN** all 1000 fully-aggregated entries are in `DbTableMemory` before the first frame is rendered

### Requirement: Write-through cache update
Every write operation (create, update, delete) SHALL first commit to `DbTableSQLite`, then
update the corresponding entry in `DbTableMemory`. The UI SHALL never need to reload from SQL
after a write.

#### Scenario: Add a word, immediately visible in list
- **WHEN** a new word is saved via `create_word`
- **THEN** the word appears in the next `list_words` call on `DbTableMemory` without an explicit reload

### Requirement: clear_practice_stats resets all counters
`clear_practice_stats` SHALL reset `practice_count = 0`, `correct_count = 0`, and
`practiced_at = NULL` for every word in the database, and SHALL update `db_info.last_modified`
to the current Unix epoch second.

#### Scenario: Stats cleared and last_modified bumped
- **WHEN** `clear_practice_stats` is called
- **THEN** all words have `practice_count = 0`, `correct_count = 0`, `practiced_at = NULL`
  and `db_info.last_modified` is updated

### Requirement: word_meanings deduplication
Inserting a duplicate meaning for the same word (same `word_id` + `meaning`) SHALL be silently
ignored (`INSERT OR IGNORE`). The `UNIQUE(word_id, meaning)` constraint in the schema enforces this.

#### Scenario: Duplicate meaning ignored
- **WHEN** the same meaning string is inserted twice for the same word
- **THEN** only one row exists in `word_meanings` and no error is raised

### Requirement: word_forms label vocabulary
The system SHALL define a canonical vocabulary of `word_forms` labels shared across all
implementations. Labels are language-specific:

**English labels**: `singular`, `plural`, `base_form`, `past_tense`, `past_participle`, `gerund`,
`comparative`, `superlative`, `phonetic`, `collocation`

**Japanese labels**: `masu_form`, `ta_form`, `te_form`, `nai_form`, `dictionary_form`, `kanji`,
`hiragana`, `pitch_accent`, `counter`, `particle`, `transitive_pair`, `origin`

UI SHOULD suggest these labels in the word-edit dialog based on `language` + `part_of_speech`.
Custom labels (outside this list) SHALL be accepted without error.

#### Scenario: English verb word_forms suggestions
- **WHEN** a word is added with `language = "en"` and `part_of_speech = "verb"`
- **THEN** the edit dialog suggests `base_form`, `past_tense`, `past_participle`, `gerund`

#### Scenario: Custom label accepted
- **WHEN** a user saves a word_form with label `my_custom_label`
- **THEN** it is stored without error
