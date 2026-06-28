## 1. Project Scaffold (Rust/PC)

- [ ] 1.1 Create `rust/` directory with `Cargo.toml`: single crate `easyvocabook`, edition 2024, add all dependencies (iced/tokio, rusqlite, dirs, serde/toml, suppaftp, russh, russh-sftp, keyring, webbrowser, oauth2, tempfile dev-dep)
- [ ] 1.2 Create module skeleton: `main.rs`, `db/mod.rs`, `quiz/mod.rs`, `ui/mod.rs`, `network/mod.rs`, `config/mod.rs`
- [ ] 1.3 Verify `cargo build` succeeds with empty module stubs

## 2. Schema & Documentation (Rust/PC)

- [ ] 2.1 Write `doc/schema.md`: full v1 SQL for all tables and indexes, migration strategy, timestamp convention
- [ ] 2.2 Implement `db::schema` module: `CURRENT_VERSION` const, `create_schema()` function executing all v1 CREATE TABLE + CREATE INDEX statements
- [ ] 2.3 Implement DB open logic: resolve platform path via `dirs::data_local_dir()`, create directory if missing, open connection, `PRAGMA foreign_keys = ON`, call `create_schema()` or run migrations

## 3. Domain Types (Rust/PC)

- [ ] 3.1 Define `BookInfo`, `WordEntry`, `WordFilter`, `NewWord`, `UpdateWord` structs in `db/types.rs`
- [ ] 3.2 Define `part_of_speech` key constants and per-language word_forms label lists in `db/labels.rs`
- [ ] 3.3 Define `Settings` struct and `load()`/`save()` in `config/mod.rs` using `serde`/`toml`; resolve path via `dirs::config_dir()`

## 4. DbTableBase Trait & DbTableSQLite (Rust/PC)

- [ ] 4.1 Define `DbTableBase` trait in `db/mod.rs` with all CRUD methods
- [ ] 4.2 Implement `DbTableSQLite::get_book_info()` and `update_book_info()`
- [ ] 4.3 Implement `DbTableSQLite::create_word()`: insert `words` + `word_meanings` + `word_forms` + `sentences` in one transaction; set `created_at` to current epoch
- [ ] 4.4 Implement `DbTableSQLite::update_word()`: update `words` row, replace sub-records in transaction, bump `db_info.last_modified`
- [ ] 4.5 Implement `DbTableSQLite::delete_word()`: delete `words` row (CASCADE handles sub-records), bump `db_info.last_modified`
- [ ] 4.6 Implement `DbTableSQLite::list_words()` with full-aggregate SELECT (JOIN or separate SELECTs per sub-table)
- [ ] 4.7 Implement `DbTableSQLite::clear_practice_stats()`: reset all counts and `practiced_at`, bump `db_info.last_modified`
- [ ] 4.8 Write `DbTableSQLite` unit tests using `tempfile`; cover: create/read/update/delete word, cascade delete, duplicate word_meaning ignored, clear_practice_stats

## 5. DbTableMemory (Rust/PC)

- [ ] 5.1 Implement `DbTableMemory` holding `Vec<WordEntry>`; implement all `DbTableBase` methods operating on the Vec
- [ ] 5.2 Implement `DbTableMemory::load_from(sqlite: &DbTableSQLite)`: load all words with full aggregates
- [ ] 5.3 Implement `WordFilter` application in `list_words()`: language filter + text search (word, reading, all meanings)
- [ ] 5.4 Implement sort by word / reading / meaning / correct_rate in `list_words()`
- [ ] 5.5 Write `DbTableMemory` unit tests: filter by language, text search matches secondary meaning, sort orders, empty-state

## 6. Quiz Engine (Rust/PC)

- [ ] 6.1 Implement weighted random sampler in `quiz/sampler.rs`: `NEW_WEIGHT=3.0`, `BASE=1.0`, `MULTIPLIER=3.0`; accepts `Vec<WordEntry>` and returns a weighted-random pick
- [ ] 6.2 Implement quiz mode selector: given a `WordEntry`, choose between available modes based on language and available data
- [ ] 6.3 Implement typing mode logic (`中翻英`/`中翻日`): random meaning as prompt; conjugation fields per language+POS; synonym acceptance (grade against the word user typed; if synonym has no word_forms, accept any input for those fields); collect all valid synonyms for reveal
- [ ] 6.4 Implement multiple-choice mode logic (`英翻中`/`日翻中`): collect ALL correct meanings as correct set (never truncated); draw distractors (meaning strings) excluding synonym-intersecting meanings; shuffle options
- [ ] 6.5 Implement give-up action: reveal correct answer, record as incorrect (practice_count++ only), show [Next]
- [ ] 6.6 Implement counter update: `practice_count += 1`, `practiced_at = now()`, `correct_count += 1` if correct; write to SQLite and update DbTableMemory
- [ ] 6.7 Write quiz engine unit tests: weight formula, synonym exclusion from distractors, all meanings shown in multi-choice (>4 correct), synonym acceptance in typing with correct conjugation lookup, give-up records as wrong

## 7. Config & Keychain (Rust/PC)

- [ ] 7.1 Implement `config::Settings` load/save with all fields (ui_language, theme, sync_method, FTP fields, Drive/OneDrive folder, last_word_language, last_synced)
- [ ] 7.2 Implement keychain helpers in `config::keychain`: `store(service, key, secret)`, `load(service, key)`, `delete(service, key)` using `keyring` crate
- [ ] 7.3 Write config unit tests: round-trip serialize/deserialize `Settings`

## 8. Cloud Sync (Rust/PC)

- [ ] 8.1 Implement `network::SyncClient` trait with `upload(local_path)`, `download(dest_path)` methods
- [ ] 8.2 Implement `network::FtpClient` using `suppaftp`; load password from keychain; support FTPS
- [ ] 8.3 Implement `network::SftpClient` using `russh` + `russh-sftp`; load password from keychain
- [ ] 8.4 Implement OAuth2 PKCE helper in `network::oauth`: bind TCP on port 0, open browser, await callback, exchange code for tokens, store in keychain
- [ ] 8.5 Implement `network::DriveClient` (Google Drive API v3): find-or-create folder by name, upload file, download file, refresh token if expired
- [ ] 8.6 Implement `network::OneDriveClient` (Microsoft Graph API): find-or-create folder, upload, download, token refresh
- [ ] 8.7 Implement sync orchestrator in `network::sync`: three-way conflict detection using `db_info.last_modified` vs `last_synced`; prompt conflict resolution; after download: close SQLite, replace file, reopen, reload DbTableMemory
- [ ] 8.8 Write sync unit tests using mock `SyncClient`: fast-forward download, fast-forward upload, conflict detection

## 9. UI — Application Shell (Rust/PC)

- [ ] 9.1 Set up iced `Application` struct with `Message` enum and `AppState`; wire up tokio runtime via iced `tokio` feature
- [ ] 9.2 Implement top tab bar with three tabs: Quiz, Word List, Settings; default to Quiz tab
- [ ] 9.3 Implement app startup: load `Settings`, open DB, load `DbTableMemory` via `spawn_blocking`, then render first frame

## 10. UI — Word List Tab (Rust/PC)

- [ ] 10.1 Implement word list table using iced `table` widget: four columns (Word, Reading, Meaning, Correct Rate); correct rate as `XX%` or `—`
- [ ] 10.2 Implement column header click for sort (ascending/descending toggle); sort in DbTableMemory
- [ ] 10.3 Implement language filter dropdown; apply instantly via DbTableMemory filter
- [ ] 10.4 Implement search input toggle and real-time text filter (word + reading + all meanings)
- [ ] 10.5 Spike: verify iced `table` secondary mouse event support; implement right-click context menu (Edit / Delete / More info / Homophones) via `stack` overlay if needed
- [ ] 10.6 Implement delete confirmation dialog and word deletion flow (SQLite → DbTableMemory → UI refresh)
- [ ] 10.7 Implement empty-state view (0 words globally); implement "no results" state (filtered to 0)
- [ ] 10.8 Implement action bar: ＋ New, 🔍 Search toggle, 🔄 Sync Now, … More (Import / Export / Stats — stubs for import/export)

## 11. UI — Word Edit Dialog (Rust/PC)

- [ ] 11.1 Implement modal dialog scaffold using iced `stack` overlay
- [ ] 11.2 Implement fixed fields: language dropdown (with last-used memory), word, reading, primary meaning, part-of-speech dropdown (per language)
- [ ] 11.3 Implement dynamic additional meanings list: add/remove rows
- [ ] 11.4 Implement dynamic word_forms section: auto-populate suggestions on language+POS change; allow remove/add custom
- [ ] 11.5 Implement sentences section: add/remove sentence+translation rows
- [ ] 11.6 Implement Save: validate required fields, write to SQLite in transaction, update DbTableMemory, close dialog
- [ ] 11.7 Implement Edit flow: pre-fill dialog with existing `WordEntry` data

## 12. UI — Quiz Tab (Rust/PC)

- [ ] 12.1 Implement quiz language filter dropdown; wire to quiz engine pool
- [ ] 12.2 Implement typing quiz UI: prompt (random meaning), input fields per language+POS, [Submit] + [Give Up / Show Answer] buttons; reveal with ✓/✗ per field + synonyms list + [Next]
- [ ] 12.3 Implement multiple-choice quiz UI: word prompt, checkbox options (all correct meanings always shown), [Submit] + [Give Up / Show Answer]; reveal with ✓/✗ per option + [Next]
- [ ] 12.5 Wire counter update to each quiz verdict; advance to next card
- [ ] 12.6 Implement empty-state on Quiz tab (no words / no words in filter)
- [ ] 12.7 Implement ⏭ Skip button in action bar (skips current card without recording any counter update)

## 13. UI — Settings Tab (Rust/PC)

- [ ] 13.1 Implement Settings tab layout: scrollable content + fixed footer
- [ ] 13.2 Implement App section: language dropdown (instant switch), theme radio (Light/Dark/Auto)
- [ ] 13.3 Implement Sync section: method radio buttons; show only active method's fields
- [ ] 13.4 Implement FTP/FTPS fields (host, port, username, password, directory); save password to keychain on save
- [ ] 13.5 Implement SFTP fields; save password to keychain
- [ ] 13.6 Implement Google Drive fields: [Log in] → OAuth PKCE flow → show logged-in state with email; [Log out]; folder name input
- [ ] 13.7 Implement OneDrive fields (same pattern as Google Drive)
- [ ] 13.8 Implement [Sync Now] button: trigger sync, lock UI, show progress, handle conflict dialog
- [ ] 13.9 Implement Practice section: [Clear Practice Statistics] with confirmation dialog

## 14. i18n & Theme (Rust/PC)

- [ ] 14.1 Define locale string keys and implement `t!(key)` macro or function for en / zh-TW / zh-CN
- [ ] 14.2 Add zh-TW and zh-CN locale string files; verify all UI strings have translations
- [ ] 14.3 Implement Day/Night/Auto theme switching; apply purple+teal color palette

## 15. Coverage & Manual Verification (Rust/PC)

- [ ] 15.1 Run `cargo llvm-cov` and ensure DB layer (DbTableSQLite + DbTableMemory) and quiz engine have meaningful coverage; fix gaps
- [ ] 15.2 Manual verification: add EN word with multiple meanings, quiz typing + multiple-choice modes, verify counter updates; verify give-up counts as wrong
- [ ] 15.3 Manual verification: add JA word with verb conjugations, quiz 中翻日 typing mode
- [ ] 15.4 Manual verification: FTP sync round-trip (upload then download on clean install)
- [ ] 15.5 Manual verification: Google Drive OAuth flow end-to-end
- [ ] 15.6 Manual verification: conflict dialog triggers when both sides modified
- [ ] 15.7 Manual verification: Settings language switch, theme switch, clear stats
