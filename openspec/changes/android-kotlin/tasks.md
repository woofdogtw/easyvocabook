## 1. Rust Desktop — OneDrive Removal

- [ ] 1.1 Delete `rust/src/network/onedrive.rs`
- [ ] 1.2 Remove `pub mod onedrive` from `rust/src/network/mod.rs`; remove `OneDrive(OneDriveClient)` variant from `SyncClient` enum; remove all OneDrive arms from sync orchestrator match
- [ ] 1.3 Remove `onedrive_folder` field from `rust/src/config/mod.rs` `Settings` struct and all deserialization/default impls
- [ ] 1.4 Remove OneDrive radio button and folder input from Rust settings UI; remove all `onedrive_folder` references in settings view/update handlers
- [ ] 1.5 Remove OneDrive from `openspec/config.yaml` sync provider list

## 2. Documentation Fix

- [ ] 2.1 Edit `doc/schema.md` "Sync notes" section: replace the stale `last_synced` / three-way conflict detection paragraph with a description of the latest-wins model (`db_info.last_modified` bumped on every write; compare local vs remote on sync)

## 3. Android Project Setup

- [ ] 3.1 Create `kotlin/` Android project: `build.gradle.kts` (app module), `settings.gradle.kts`, `gradle.properties`; set `compileSdk = 34`, `minSdk = 29`, `applicationId = "tw.idv.woofdog.easyvocabook"`; configure Java 1.8 compatibility
- [ ] 3.2 Add dependencies in `kotlin/app/build.gradle.kts`: Compose BOM + `material3`, `lifecycle-viewmodel-compose`, `activity-compose`, `navigation-compose`, `play-services-auth`, `okhttp3`, `commons-net`, `sshj`, `security-crypto`; add JaCoCo plugin
- [ ] 3.3 Create `AndroidManifest.xml`: declare `MainActivity`, Internet permission, network state permission; no external storage permission
- [ ] 3.4 Create `MainActivity.kt`: single-activity host; call `setContent { EasyVocaBookApp() }` with `enableEdgeToEdge()`
- [ ] 3.5 Create locale resource files: `res/values/strings.xml` (English default), `res/values-zh-rTW/strings.xml` (Traditional Chinese), `res/values-zh-rCN/strings.xml` (Simplified Chinese) — include all UI string keys
- [ ] 3.6 Define Material 3 color scheme in `ui/theme/Theme.kt`: purple primary + teal secondary; `darkColorScheme` and `lightColorScheme`; `EasyVocaBookTheme` respects `SP_THEME` (light / dark / auto)

## 4. Android Domain Model & DB Interface

- [ ] 4.1 Create data classes in `data/model/`: `BookInfo`, `WordEntry` (with sub-lists), `WordMeaning`, `WordForm`, `Sentence`, `WordFilter`; all timestamps as `Long` (Unix epoch seconds)
- [ ] 4.2 Define `DbTableBase` interface in `data/db/DbTableBase.kt` with all operations: `getBookInfo`, `updateBookInfo`, `listWords`, `getWord`, `createWord`, `updateWord`, `deleteWord`, `clearPracticeStats`

## 5. Android DB Layer — SQLite & Memory

- [ ] 5.1 Implement `DbTableSQLite` extending `SQLiteOpenHelper` in `data/db/DbTableSQLite.kt`: `onCreate` creates all v1 tables and indexes (same SQL as desktop); call `setWriteAheadLoggingEnabled(false)` in the constructor (before any connection opens) to disable WAL; execute `PRAGMA foreign_keys = ON` and `PRAGMA journal_mode = DELETE` on every `getWritableDatabase()` call; `db_info.last_modified` initialized to `0` on fresh creation
- [ ] 5.2 Implement `DbTableSQLite.onUpgrade` version migration guard: if `version > CURRENT_VERSION` throw "Please update the app"; otherwise run sequential migrations
- [ ] 5.3 Implement `DbTableSQLite` CRUD: `createWord` / `updateWord` / `deleteWord` use transactions; `listWords` performs full-aggregate JOIN query with optional language + text filter; `clearPracticeStats` resets counters and bumps `last_modified`; all methods called via `withContext(Dispatchers.IO)`
- [ ] 5.4 Implement `DbTableMemory` in `data/db/DbTableMemory.kt`: holds `List<WordEntry>` in memory; `loadAll(sqlite)` does full-aggregate load; `listWords` applies language + text filter in-memory; incremental add/update/remove methods
- [ ] 5.5 Write unit tests for `DbTableSQLite` using temp files in `context.cacheDir`: test create/update/delete word, clearPracticeStats, cascade delete, foreign key enforcement, fresh DB `last_modified = 0`, version guard
- [ ] 5.6 Write unit tests for `DbTableMemory`: test `listWords` language filter, text search (secondary meanings), incremental add/update/remove

## 6. Android App Structure & Navigation

- [ ] 6.1 Create `EasyVocaBookApp.kt` root composable: apply `EasyVocaBookTheme`; set up `NavHost` with `rememberNavController()`; define three routes: `quiz` (start destination), `wordlist`, `settings`
- [ ] 6.2 Add `NavigationBar` with three `NavigationBarItem`s: 🎯 Quiz (index 0, default), 📖 Word List (index 1), ⚙ Settings (index 2); tab selection updates `NavController.navigate()` with `launchSingleTop = true`
- [ ] 6.3 Create a singleton `AppRepository` (or `AppContainer`) holding the `DbTableSQLite` and `DbTableMemory` instances; expose them via `ViewModelProvider.Factory` or Hilt (choose simple manual DI consistent with Easy-series); load `DbTableMemory` at startup in `withContext(Dispatchers.IO)` before emitting first UI state

## 7. Android Quiz Engine

- [ ] 7.1 Implement `QuizEngine.kt`: weighted random word selection (formula: `practice_count == 0 → 3.0`; else `1.0 + incorrect_rate × 3.0`); `nextWord(filter)` returns `WordEntry` or null if pool is empty
- [ ] 7.2 Implement `QuizEngine.buildTypingCard(word)`: determine fields by language + POS (EN verb: base_form/past_tense/past_participle/gerund; EN noun: singular/plural; EN adj: comparative/superlative; JA verb: dictionary_form/masu_form/ta_form/te_form/nai_form; JA i-adj: te_form/negative/past; JA na-adj: te_form/negative; JA particle if present); select one random meaning as prompt
- [ ] 7.3 Implement `QuizEngine.gradeTyping(card, userInputs)`: synonym lookup (any word in `DbTableMemory` whose meaning set intersects the prompt meaning); grade base word first; if synonym, grade conjugations against that synonym's word_forms; missing word_forms → accept any; all fields must match for correct
- [ ] 7.4 Implement `QuizEngine.buildMcqCard(word, allWords)`: correct set = `words.meaning` ∪ all `word_meanings`; distractors from other words' meanings excluding intersecting strings; total options = `max(correctCount + 3, 4)`; shuffle options
- [ ] 7.5 Implement `QuizEngine.gradeMcq(card, selectedMeanings)`: selected set must exactly equal correct set (no extra, no missing)
- [ ] 7.6 Implement counter update: `updateStats(wordId, isCorrect)` writes to `DbTableSQLite` and updates `DbTableMemory` incrementally; `giveUp` calls `updateStats(wordId, isCorrect = false)` then shows answer
- [ ] 7.7 Write unit tests for `QuizEngine`: weighted selection probabilities, typing grading (correct/incorrect/synonym/missing word_forms), MCQ grading (exact match, partial miss), give-up scoring, skip (no stat change)

## 8. Android Quiz UI

- [ ] 8.1 Create `QuizViewModel.kt`: `StateFlow<QuizUiState>` where state is `Loading | Empty | TypingCard(card) | McqCard(card) | TypingResult(result) | McqResult(result)`; methods: `setLanguageFilter`, `skip`, `giveUp`, `submitTyping`, `submitMcq`, `next`
- [ ] 8.2 Create `QuizScreen.kt` composable: observe `QuizViewModel.uiState`; render `EmptyState`, `TypingCardView`, or `McqCardView` based on state; include `⏭ Skip` `IconButton` in `TopAppBar` and language filter `ExposedDropdownMenuBox`
- [ ] 8.3 Create `TypingCardView` composable: large `Text` for meaning prompt; `OutlinedTextField` per word_form field; `[Give Up]` `TextButton` and `[Submit]` `Button`
- [ ] 8.4 Create `TypingResultView` composable: each field row with ✓/✗ icon + correct value text; synonyms list; `[Next →]` `Button`
- [ ] 8.5 Create `McqCardView` composable: word + reading `Text`; "Select all correct meanings" subtitle; `LazyColumn` of `Row { Checkbox; Text }` per option; `[Give Up]` and `[Submit]` buttons; shuffle order on render
- [ ] 8.6 Create `McqResultView` composable: each option row color-coded (correct ✓ / incorrect ✗); `[Next →]` `Button`

## 9. Android Word List UI

- [ ] 9.1 Create `WordListViewModel.kt`: `StateFlow<WordListUiState>` with the filtered+sorted word list; methods: `setLanguageFilter`, `setSearchQuery`, `setSortOrder`, `deleteWord`, `clearStats`; emits updated state after each write
- [ ] 9.2 Create `WordListScreen.kt` composable: `TopAppBar` with search toggle `TextField`, sort `IconButton`, overflow `DropdownMenu` (Sort / Import / Export / Practice Statistics / Sync Now); `LazyColumn` of word rows
- [ ] 9.3 Create word row composable: display word, reading (if present), primary meaning, correct rate (`XX%` or `—`); `Modifier.combinedClickable(onLongClick = { showMenu = true })`; `DropdownMenu` with Edit, Delete, More Info, Homophones
- [ ] 9.4 Add `FloatingActionButton` (FAB) to `WordListScreen`: opens `WordEditSheet` in add mode; hide FAB during active sync
- [ ] 9.5 Implement empty state: when zero words (no filter active), show "No words yet. Tap ＋ to add your first word." with a highlighted button
- [ ] 9.6 Implement Homophones query: for JA words, find all `DbTableMemory` words with same `reading` + `language`; for EN words with no reading, match on `word_forms["phonetic"]` if present; show results in a simple dialog or bottom sheet

## 10. Android Word Edit UI

- [ ] 10.1 Create `WordEditViewModel.kt`: manages `WordEditUiState` (all form fields as mutable state); methods: `setLanguage`, `setWord`, `setReading`, `setPOS`, `setPrimaryMeaning`, `setNote`, `addMeaning`, `removeMeaning`, `addWordForm`, `removeWordForm`, `addSentence`, `removeSentence`, `save`; `setLanguage` and `setPOS` update suggested word_form labels; `save` validates required fields then calls `DbTableBase.createWord` or `updateWord`
- [ ] 10.2 Create `WordEditSheet.kt` ModalBottomSheet composable: scrollable `Column` with all fixed fields (Language dropdown, Word, Reading, POS dropdown, Primary meaning, Additional meanings dynamic list, Note, word_forms section, Sentences section); [Cancel] and [Save] footer buttons
- [ ] 10.3 Implement language dropdown: options hardcoded (en, ja, …); on change update POS options and regenerate word_form suggestions; store last-used language in `SharedPreferences` (`SP_LAST_LANGUAGE`)
- [ ] 10.4 Implement POS dropdown options per language (EN: noun/verb/adjective/adverb/…; JA: noun/動詞/い形容詞/な形容詞/…); value stored as language-neutral key
- [ ] 10.5 Implement word_form suggestions: map of (language, POS) → label list; when language or POS changes, replace suggested rows (preserving any filled values that match a new label); allow [＋ Add custom field] for arbitrary labels
- [ ] 10.6 Implement validation: Word and Primary meaning are required; on Save with empty field highlight the field and do not save; `word_meanings` deduplication via `INSERT OR IGNORE` (existing DB constraint handles it)

## 11. Android Settings UI

- [ ] 11.1 Create `SettingsViewModel.kt`: loads all settings from `SharedPreferences` on init; `StateFlow<SettingsUiState>` with current values; methods: `setLanguage`, `setTheme`, `setSyncMethod`, `setFtpConfig`, `setSftpConfig`, `setGoogleDriveFolder`, `loginGoogleDrive`, `logoutGoogleDrive`, `syncNow`, `clearPracticeStats`; FTP/SFTP passwords stored to `EncryptedSharedPreferences`
- [ ] 11.2 Create `SettingsScreen.kt` composable: scrollable `LazyColumn`; four sections rendered as Material 3 `ListItem` groups: App, Sync, Practice, About
- [ ] 11.3 Implement App section: UI Language `ExposedDropdownMenuBox` (en / zh-TW / zh-CN) — on change store to `SP_UI_LANGUAGE` and call `Activity.recreate()`; Theme radio group (Light / Dark / Auto) — on change store to `SP_THEME` and reapply to `EasyVocaBookTheme`
- [ ] 11.4 Implement Sync section: radio group (Disabled / FTP / FTPS / SFTP / Google Drive); conditionally show only the fields for the selected method; [Sync Now] button (disabled when method = Disabled)
- [ ] 11.5 Implement FTP/FTPS fields: Host, Port (default 21), Username, Password (`EncryptedSharedPreferences`), Directory; SFTP fields: Host, Port (default 22), Username, Password (`EncryptedSharedPreferences`), Directory
- [ ] 11.6 Implement Google Drive section: show [Log in to Google Drive] button if not logged in; on tap call `Authorization.getClient(context).authorize(request)` — if `pendingIntent` returned, launch it via `rememberLauncherForActivityResult`; on consent granted, store confirmation flag and folder name; show "✓ Logged in" + [Log out] when authorized
- [ ] 11.7 Implement Practice section: [Clear Practice Statistics] button → `AlertDialog` "Reset all practice statistics? This cannot be undone." → [Cancel] / [Reset]; on confirm call `clearPracticeStats` via ViewModel
- [ ] 11.8 Implement About section: static `Text` with app version (from `BuildConfig.VERSION_NAME`) and "MIT © 2026 Chien-Hong Chan"

## 12. Android Cloud Sync

- [ ] 12.1 Define `SyncClient` interface in `network/SyncClient.kt`: `remoteLastModified(): Long?` (null = no remote file; throw on error), `upload(file: File)`, `download(dest: File)`
- [ ] 12.2 Implement `NetFtp.kt` using commons-net `FTPClient`: support both FTP (plain) and FTPS (`FTPSClient`); `remoteLastModified`: download the remote file to a temp path in `cacheDir`, open with SQLite (read-only), read `db_info.last_modified`, delete the temp file, return the value; return null if the file does not exist on the server; rethrow all other errors; upload via `storeFile`; download via `retrieveFile`
- [ ] 12.3 Implement `NetSftp.kt` using SSHJ `SSHClient`: connect, authenticate with password, open SFTP channel; `remoteLastModified`: download remote file to temp in `cacheDir` via `SFTPClient.get`, open with SQLite (read-only), read `db_info.last_modified`, delete temp, return value; return null if `SFTPException` with NO_SUCH_FILE; rethrow other exceptions; upload via `SFTPClient.put`; full download via `SFTPClient.get`
- [ ] 12.4 Implement `NetDrive.kt` using okhttp3 for Drive REST API v3: `authorize()` helper calls `Authorization.getClient(context).authorize(request)` and returns `accessToken`; find or create folder by name; `remoteLastModified`: download remote file to `cacheDir` temp via `alt=media`, open with SQLite (read-only), read `db_info.last_modified`, delete temp, return value; return null if file does not exist; rethrow all other errors; do NOT read or write any Drive metadata fields (no appProperties, no modifiedTime); full download via `alt=media`
- [ ] 12.5 Implement sync orchestrator in `network/SyncOrchestrator.kt`: read `local.last_modified` from `DbTableMemory.getBookInfo()`; call `remoteLastModified()` (null → upload, error → throw + abort); compare L vs R (L>R → upload; R>L → download; L==R → no-op); for download: download to `cacheDir/easyvocabook_sync.db`, read version, check against `CURRENT_VERSION`, close `DbTableSQLite` connection, atomically replace `filesDir/easyvocabook.db`, reopen, reload `DbTableMemory`; emit sync result to ViewModel `StateFlow`
- [ ] 12.6 Integrate UI lock during sync: `SyncViewModel` (or shared `AppViewModel`) emits `isSyncing: Boolean` via `StateFlow`; word list FAB and edit actions disabled while `isSyncing = true`; progress indicator shown in Sync section and word list top bar

## 13. Android CI & Build

- [ ] 13.1 Add `.github/workflows/build-test.yaml` Android job: checkout, setup JDK 17, `./gradlew :app:testDebugUnitTest`, upload test results
- [ ] 13.2 Configure JaCoCo: add `jacocoTestReport` task in `build.gradle.kts`; coverage report generated as XML + HTML; exclude Compose-generated classes from coverage

## 14. Manual Verification

- [ ] 14.1 Install debug APK on Android device/emulator; verify: app launches on Quiz tab, empty state shown, can add a word (all fields), word appears in list and quiz
- [ ] 14.2 Verify quiz engine: typing mode prompt/fields match language+POS; give up records wrong; correct increments both counters; skip changes nothing; MCQ requires all correct meanings selected
- [ ] 14.3 Verify word edit: language change updates POS options and word_form suggestions; edit pre-fills all existing data; delete with confirmation removes word; homophones search returns correct results
- [ ] 14.4 Verify settings: language switch recreates Activity and changes UI language; theme toggle applies immediately; Sync Disabled greys out Sync Now
- [ ] 14.5 Verify Google Drive sync: login flow; upload on first sync; download when remote is newer; no-op when equal; fresh install (last_modified=0) downloads remote
- [ ] 14.6 Verify FTP/SFTP sync: same latest-wins scenarios as Drive; password stored encrypted (confirm `SharedPreferences` XML has no plaintext password)
- [ ] 14.7 Verify Rust desktop OneDrive removal: desktop settings UI has no OneDrive option; `settings.toml` has no `onedrive_folder`; Rust build passes; desktop sync still works with remaining providers
- [ ] 14.8 Verify cross-platform sync: create word on Android, sync to Google Drive, sync from desktop — word appears on desktop (and vice versa)
