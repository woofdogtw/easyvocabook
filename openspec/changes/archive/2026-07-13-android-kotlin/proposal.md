## Why

EasyVocaBook has a complete PC desktop implementation (Rust + iced) but no Android app yet.
This change delivers the full Android app (Kotlin + Jetpack Compose) with feature parity to
the desktop, and removes OneDrive from both platforms as a deliberate scope reduction.

## What Changes

- **New Android app** (`kotlin/`): full Jetpack Compose app targeting Android (minSdk 29)
  with Word List, Quiz, and Settings tabs
- **Word CRUD**: add/edit/delete vocabulary entries with multi-meaning, word_forms, and sentences
- **Quiz engine on Android**: Typing and Multiple-Choice modes — same scoring logic as desktop
  (no flip-card, no self-report; Give Up = auto-incorrect, Skip = unscored)
- **Cloud sync on Android**: FTP/FTPS (commons-net), SFTP (SSHJ), Google Drive
  (Google Identity Services Authorization API — no Firebase)
- **Multilingual UI**: English (default), Traditional Chinese (zh-rTW), Simplified Chinese (zh-rCN)
- **Day/Night theme**: purple + teal, follows system setting
- **Remove OneDrive** from both platforms: delete `rust/src/network/onedrive.rs`, remove from
  Rust config/settings UI/sync orchestrator, update specs and `openspec/config.yaml`
- **doc/schema.md cleanup**: remove stale `last_synced` / three-way conflict detection note

## Capabilities

### New Capabilities

*(none — all capabilities are covered by existing specs; this change adds Android requirements
and removes OneDrive from existing specs)*

### Modified Capabilities

- `db-schema`: add note that `db_info.last_modified` is seeded to `0` on fresh Android install
  (already specified for desktop; make explicit it applies to Android too); fix `doc/schema.md`
  stale `last_synced` note
- `db-layer`: add Android `DbTableBase` interface, `DbTableSQLite`, and `DbTableMemory`
  requirements (Kotlin implementations parallel to the existing Rust requirements)
- `cloud-sync`: add Android sync requirements (Authorization API auth flow, SSHJ SFTP,
  same latest-wins logic and error handling as desktop); **REMOVE** OneDrive requirements
  from both platforms
- `quiz-engine`: no logic change — spec is already platform-agnostic; Android must implement
  the same scoring contract (typing + MCQ, give-up = incorrect, skip = unscored)
- `quiz-ui`: add Android quiz UI requirements (Compose screens for Typing and MCQ modes,
  Give Up / Skip / Next interactions)
- `word-list-ui`: add Android word list requirements (LazyColumn, sort/filter, FAB, long-press)
- `word-edit-ui`: add Android word edit requirements (ModalBottomSheet, primary meaning vs
  word_meanings, note field, dynamic word_forms, sentences)
- `settings-ui`: add Android settings requirements (Compose settings screen, Language/Theme/Sync
  sections, Google Drive login/logout); **REMOVE** OneDrive configuration requirements

## Impact

**Platforms affected**: Android (new), PC/Desktop (OneDrive removal only)

**New files**: entire `kotlin/` subtree

**Deleted files**: `rust/src/network/onedrive.rs`

**Modified files (Rust)**: `rust/src/network/mod.rs`, `rust/src/config/mod.rs`,
`rust/src/network/sync.rs`, settings UI source, `doc/schema.md`

**New dependencies (Kotlin/Android)**:
- `androidx.compose.*` (Jetpack Compose + Material 3)
- `androidx.lifecycle:lifecycle-viewmodel-compose` (MVVM)
- `com.google.android.gms:play-services-auth` (Google Identity Services)
- `com.squareup.okhttp3:okhttp` (Drive REST API)
- `commons-net:commons-net` (FTP/FTPS)
- `com.hierynomus:sshj` (SFTP)
- `androidx.security:security-crypto` (EncryptedSharedPreferences)

**Non-goals**:
- OneDrive on Android (not implemented; removed from desktop too)
- Multi-book management (single `easyvocabook.db` only; multi-book is a future change)
- Furigana ruby rendering (fallback: `漢字（かな）` side-by-side; deferred)
- Import/export (UI entry points may exist but format TBD)
- Automated UI tests (manual verification only)
- Background/scheduled sync (sync is user-triggered only)
