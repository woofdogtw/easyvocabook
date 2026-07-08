## ADDED Requirements

### Requirement: Android DbTableBase interface
On Android (Kotlin), the system SHALL define a `DbTableBase` Kotlin interface that abstracts all
read and write operations over the vocabulary data. Both `DbTableSQLite` and `DbTableMemory`
SHALL implement this interface, mirroring the same contract as the Rust `DbTableBase` trait.

The interface SHALL expose the same operations as the Rust counterpart:
- `getBookInfo()`: return the database metadata record (`BookInfo`)
- `updateBookInfo(data)`: write database metadata
- `listWords(filter)`: return all fully-aggregated word entries matching the given `WordFilter`
- `getWord(id)`: return a single fully-aggregated word entry by ID, or null if not found
- `createWord(data)`: insert a new word with all sub-records; return the new word ID
- `updateWord(id, data)`: replace the word and all sub-records
- `deleteWord(id)`: remove the word and all sub-records
- `clearPracticeStats()`: reset all practice counters

`WordEntry` is a fully-aggregated data class containing the word row plus its `word_meanings`,
`word_forms`, and `sentences` sub-records.

#### Scenario: Both Android implementations satisfy the same interface
- **WHEN** `DbTableSQLite` and `DbTableMemory` are compiled on Android
- **THEN** both implement `DbTableBase` with no missing methods and the Kotlin compiler accepts them

### Requirement: Android DbTableSQLite implementation
On Android, `DbTableSQLite` SHALL implement `DbTableBase` using `android.database.sqlite.SQLiteDatabase`
directly (not Room). It SHALL:
- Execute `PRAGMA foreign_keys = ON` immediately after opening the connection
- Run schema creation or migration SQL on first open (same SQL as desktop, applied independently)
- Wrap multi-step writes in a single transaction
- Use `JOURNAL_MODE_DELETE` (not WAL) to allow atomic whole-file replacement during sync
- Dispatch all DB operations via `withContext(Dispatchers.IO)` so the main thread is never blocked

Unit tests SHALL use a temporary SQLite file created in `context.cacheDir` per test and deleted after.

#### Scenario: Android write word with sub-records atomically
- **WHEN** `createWord()` is called with meanings, forms, and sentences
- **THEN** all rows are inserted in a single transaction; if any step fails, all are rolled back

#### Scenario: Android SQLite tests use isolated files
- **WHEN** a `DbTableSQLite` unit test runs on Android
- **THEN** it creates a temporary file in `getCacheDir()` that does not affect any real data and is deleted after the test

#### Scenario: Android DELETE journal mode allows file replacement
- **WHEN** the sync downloads a new database and replaces `filesDir/easyvocabook.db`
- **THEN** no WAL or SHM sidecar files are present that would need to be handled separately

### Requirement: Android DbTableMemory implementation
On Android, `DbTableMemory` SHALL implement `DbTableBase` by operating on an in-memory collection
of `WordEntry` objects. It SHALL support:
- Loading all entries from `DbTableSQLite` on app startup (full-aggregate load)
- Incremental update after each write (add/update/remove the affected entry in-memory)
- Filter and text search over the in-memory collection without any SQL

The load on startup SHALL occur in `withContext(Dispatchers.IO)` before the ViewModel emits the
first `UiState` to Compose.

#### Scenario: Android memory filter returns matching words
- **WHEN** `listWords()` is called with `language = "ja"` on a `DbTableMemory` containing mixed EN and JA entries
- **THEN** only JA entries are returned, with no SQL query executed

#### Scenario: Android text search covers all meanings
- **WHEN** `listWords()` is called with a text filter matching a secondary meaning in `word_meanings`
- **THEN** the word is included in results

### Requirement: Android write-through cache update
Every write operation on Android (createWord, updateWord, deleteWord, clearPracticeStats) SHALL
first commit to `DbTableSQLite` via `withContext(Dispatchers.IO)`, then update the corresponding
entry in `DbTableMemory`. The ViewModel SHALL emit an updated `StateFlow<UiState>` to trigger
Compose recomposition. The UI SHALL never need to reload from SQL after a write.

#### Scenario: Android — add word, immediately visible in list
- **WHEN** a new word is saved via `createWord` on Android
- **THEN** the word appears in the next `listWords` call on `DbTableMemory` without an explicit SQL reload, and the Compose UI recomposes to show it
