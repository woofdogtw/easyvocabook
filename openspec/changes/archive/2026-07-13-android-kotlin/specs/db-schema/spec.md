## MODIFIED Requirements

### Requirement: Foreign key enforcement
The system SHALL enable `PRAGMA foreign_keys = ON` on every database connection before any other
statement is executed. Neither `rusqlite` (Rust) nor Android's `SQLiteDatabase` (Kotlin) enables
foreign keys by default, so this PRAGMA must be applied explicitly on every connection open.

#### Scenario: Deleting a word cascades to sub-tables
- **WHEN** a word row is deleted
- **THEN** all associated `word_meanings`, `word_forms`, and `sentences` rows are deleted automatically

## ADDED Requirements

### Requirement: Android DB file path
On Android, the database file SHALL be located at `filesDir/easyvocabook.db`
(the app's internal storage directory — no external storage permission required). The filename
`easyvocabook.db` is fixed and identical across all platforms, which is required for cloud sync
(the remote file has the same name on every platform).

#### Scenario: Android DB created in filesDir
- **WHEN** the Android app opens for the first time
- **THEN** the database is created at `context.filesDir/easyvocabook.db` with all v1 tables and `db_info.last_modified = 0`
