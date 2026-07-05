## Why

EasyVocaBook is a new app in the "Easy XXX" personal series — a vocabulary practice notebook where
each page is one word, supporting English, Japanese, and other languages. This change establishes
the complete PC desktop implementation (Rust + iced) as the first platform, covering the database
layer, quiz engine, UI, and cloud sync.

## What Changes

- **New app from scratch**: no existing codebase; this change creates the entire Rust desktop app
- **SQLite database** with schema v1: `db_info`, `words`, `word_meanings`, `word_forms`, `sentences`
- **DbTableBase trait** with two implementations: `DbTableSQLite` (source of truth) and
  `DbTableMemory` (full-aggregate in-memory cache for UI and search)
- **Three-tab UI** (iced 0.14): Quiz tab (default), Word List tab, Settings tab
- **Quiz engine**: weighted random selection, flip-card + typing + multiple-choice modes,
  `practice_count` / `correct_count` tracking, multi-meaning support
- **Add/Edit word dialog**: multi-meaning, dynamic word_forms by language+POS, example sentences
- **Settings page**: UI language (instant switch), Day/Night/Auto theme, sync configuration,
  clear practice stats, fixed About footer
- **Cloud sync**: FTP/FTPS (`suppaftp`), SFTP (`russh`/`russh-sftp`), Google Drive and OneDrive
  via OAuth2 PKCE (system browser + loopback HTTP server); credentials in OS keychain (`keyring`)
- **Three-layer storage**: DB file (synced), OS keychain (secrets), local `settings.toml` (UI prefs)
- **Local config** (`settings.toml`): UI language, theme, sync method, FTP/cloud folder, last-used
  word language
- **Whole-file sync** with latest-wins resolution (no `last_synced`, no conflict dialog)

## Capabilities

### New Capabilities

- `db-schema`: SQLite schema v1 definition — tables, indexes, FK constraints, migration skeleton
- `db-layer`: `DbTableBase` trait + `DbTableSQLite` + `DbTableMemory` implementations; full-aggregate
  cache; CRUD for words, word_meanings, word_forms, sentences
- `quiz-engine`: weighted random selection algorithm, quiz modes (typing, multiple-choice), give-up
  action, answer grading (multi-meaning, synonym acceptance), `practice_count`/`correct_count`/`practiced_at`
  update rules
- `word-list-ui`: table view with sorting, language filter, text search (covers all meanings),
  right-click context menu (edit/delete/more/homophones), empty-state guidance, `…` menu
  (import/export/stats)
- `word-edit-ui`: add/edit modal dialog — multi-meaning, part-of-speech dropdown (per language),
  dynamic word_forms suggestions, example sentences
- `quiz-ui`: Quiz tab UI — typing (with conjugation fields), multiple-choice; give-up action
  (reveal answer, count as wrong); answer reveal and score update; ⏭ Skip button
- `settings-ui`: Settings tab — App section (language, theme), Sync section (method radio + per-method
  fields), Practice section (clear stats), fixed About footer
- `cloud-sync`: FTP/FTPS/SFTP file transfer; Google Drive + OneDrive OAuth2 PKCE flow; whole-file
  whole-file sync with latest-wins resolution; UI lock during sync; post-sync DbTableMemory reload

### Modified Capabilities

*(none — this is a greenfield change)*

## Impact

**Platform**: PC only (Rust + iced). Android implementation is a separate future change.

**New dependencies** (Rust):
- `iced` 0.14.0 (tokio feature)
- `rusqlite` 0.40.1
- `dirs` 6.0.0
- `tempfile` 3.27.0 (test only)
- `suppaftp` 9.0.0
- `russh` 0.61.2 + `russh-sftp` 2.3.0
- `keyring` (OS keychain)
- `webbrowser` + `oauth2` (PKCE flow)
- `serde` + `toml` (settings.toml)

**New files**: entire `rust/` subtree, `doc/schema.md`

**Non-goals**:
- Android / Kotlin implementation (separate change)
- OpenAPI server sync endpoint (separate change)
- Furigana (ruby text) rendering above kanji — fallback to `漢字（かな）` side-by-side display;
  proper ruby layout deferred pending iced widget availability
- Full spaced-repetition (SRS) scheduling — count-based weighting only for now
- Cloze (fill-in-the-blank sentence) quiz mode — deferred to future change
- Conjugation drill (random single form) quiz mode — deferred to future change
- Import/export file format specification — UI entry points exist but format TBD
