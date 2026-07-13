## Context

This change delivers the Android Kotlin app for EasyVocaBook and removes OneDrive from both
platforms. The Rust desktop app (rust-desktop change) is already complete; this change must
stay consistent with it at the schema, domain logic, and sync contract levels.

Key constraints inherited:
- `DbTableBase` interface pattern (two implementations: SQLite + Memory)
- Whole-file sync, latest-wins, no `last_synced`, no conflict dialog
- `db_info.version` integer for schema migration (same SQL, implemented independently)
- All timestamps as Unix epoch `Long` (seconds)
- Fixed DB path: `filesDir/easyvocabook.db`
- No Firebase SDK; no external frameworks beyond platform-standard

## Goals / Non-Goals

**Goals:**
- Complete Android app: DB layer, quiz engine, word management UI, settings, cloud sync
- Feature parity with desktop except OneDrive (intentionally excluded)
- Quiz engine behavior identical to desktop (typing + MCQ, give-up = auto-incorrect, skip = unscored)
- Google Drive via Google Identity Services Authorization API (no Firebase, no deprecated Sign-In SDK)
- Multilingual UI (en / zh-rTW / zh-rCN), Day/Night theme (purple + teal)
- DB layer unit tests (both DbTableMemory and DbTableSQLite via getCacheDir() temp files)
- Remove OneDrive from Rust desktop and all specs

**Non-Goals:**
- OneDrive (removed from all platforms)
- Multi-book management (single `filesDir/easyvocabook.db`)
- Furigana ruby rendering (fallback: `漢字（かな）` side-by-side, same as desktop)
- Import/export format specification
- Background/scheduled sync
- Automated UI tests

## Decisions

### D1: UI Framework — Jetpack Compose (not XML layouts)

**Decision**: Jetpack Compose + Material Design 3 throughout. `LazyColumn` for lists,
`ModalBottomSheetLayout` for word edit, `NavHost` + bottom `NavigationBar` for navigation.
**Rationale**: Declared in `openspec/config.yaml`. Compose handles dynamic forms (variable
meanings list, word_forms by language) more naturally than XML + RecyclerView adapters.
**Alternative rejected**: XML layouts + ViewBinding — consistent with ECA/EHR but more
boilerplate for this app's complex editing forms.

### D2: Architecture — MVVM with ViewModel + StateFlow

**Decision**: One `ViewModel` per screen, exposing `StateFlow<UiState>`. Repository layer
holds `DbTableBase` reference and provides coroutine-friendly suspend functions.
**Rationale**: Compose's `collectAsStateWithLifecycle()` integrates directly with StateFlow.
ViewModels survive configuration changes, preventing DB re-queries on rotation.
**Alternative rejected**: Direct Activity/Fragment access to DB (ECA/EHR style) — no lifecycle
safety, incompatible with Compose recomposition model.

### D3: Navigation — Bottom NavigationBar, 3 tabs

**Decision**: `NavigationBar` with three fixed destinations: **Quiz** (default) → **Word List**
→ **Settings**. Quiz is the leftmost tab and the launch destination.
Word edit opens as a `ModalBottomSheet` (not a separate NavGraph destination).
**Rationale**: 3 top-level destinations is the canonical use case for bottom navigation
(Material Design 3 guidelines). Quiz-first matches desktop tab order.
**Alternative rejected**: Navigation Drawer (ECA/EHR style) — bottom nav is more thumb-friendly
on phones and more appropriate for exactly 3 destinations.

### D4: DB Layer — raw Android SQLite API (not Room)

**Decision**: Implement `DbTableBase` interface using `android.database.sqlite.SQLiteDatabase`
directly. Same `DbTableBase` / `DbTableSQLite` / `DbTableMemory` pattern as ECA/EHR and Rust.
**Rationale**: Room adds code-generation complexity and an ORM abstraction that fights against
the existing whole-file sync model (Room's WAL journal would complicate file replacement during
sync). Direct SQLite is consistent with ECA/EHR and keeps the codebase understandable.
**Alternative rejected**: Room — WAL vs DELETE journal mode conflicts with atomic file-swap sync;
code generation differs from the interface-based pattern used across all Easy-series apps.

### D5: Google Drive Auth — Authorization API (Google Identity Services)

**Decision**: Use `Authorization.getClient(context).authorize(authorizationRequest)` from
`com.google.android.gms:play-services-auth` to obtain a short-lived `accessToken` for
`drive.file` scope. Drive REST API called via okhttp3 (same endpoints as desktop).
Play Services manages token refresh silently; we store only the Drive folder name
(`SP_SYNC_GOOGLE_FOLDER`) in SharedPreferences — no tokens stored by the app.
**Rationale**: Modern replacement for deprecated `GoogleSignInClient`. No Firebase required.
Silent refresh avoids our own token lifecycle management. Consistent with desktop at the
REST-call level.
**Alternative rejected**: `GoogleSignInClient` — deprecated since 2024.
Firebase Auth — prohibited by `openspec/config.yaml` ("no Firebase SDK").

### D6: SFTP Library — SSHJ

**Decision**: `com.hierynomus:sshj` for SFTP.
**Rationale**: Actively maintained (2024+), modern Java/Kotlin API, supports Android.
**Alternative rejected**: JSch — last meaningful update 2018, effectively unmaintained.

### D7: Secrets Storage — EncryptedSharedPreferences for FTP/SFTP passwords

**Decision**: Store FTP/SFTP passwords in `EncryptedSharedPreferences`
(`androidx.security:security-crypto`). Google Drive tokens not stored (Play Services holds them).
**Rationale**: No Android keychain API equivalent to Rust's `keyring` crate at this level of
simplicity; `EncryptedSharedPreferences` is the ECA/EHR established pattern and remains
functional despite being in maintenance mode.
**Note**: `security-crypto` is feature-frozen (maintenance mode as of 2024). Acceptable for v1;
migrate if Google provides a stable alternative.

### D8: Sync Contract — Must Match Desktop Exactly

Android sync logic must implement the same contract as `rust/src/network/sync.rs`:
- **latest-wins**: compare `local.last_modified` vs `remote.last_modified`; larger wins;
  no `last_synced`, no conflict dialog
- **fresh install seed**: `db_info.last_modified = 0` on new DB creation (not current time),
  ensuring first sync always downloads from remote
- **`remoteLastModified()` error handling**: distinguish "file not found → null (no remote)"
  from "network/auth error → throw, abort sync" — never swallow real errors as "no file"
- **atomic download**: download to temp file, verify `db_info.version`, then replace
  `filesDir/easyvocabook.db` (close SQLite, replace file, reopen, reload in-memory cache)

### D9: OneDrive Removal

**Decision**: Remove OneDrive from both platforms in this change.
- Rust: delete `onedrive.rs`, remove from `mod.rs`, `config::Settings`, sync orchestrator, settings UI
- Specs: remove OneDrive requirements from `cloud-sync` and `settings-ui` delta specs
- `openspec/config.yaml`: remove OneDrive from sync provider list
**Rationale**: Android will not implement OneDrive; maintaining dead code on desktop serves
no purpose. Removing now (before rust-desktop is archived) keeps specs clean.

### D10: Word Edit UI — ModalBottomSheet

**Decision**: Word add/edit opens as `ModalBottomSheet` triggered by FAB (add) or item
long-press (edit) in the Word List screen.
Primary meaning (`words.meaning`, required) is a distinct field above the additional meanings
(`word_meanings`, 0..N). `note` field is included. `word_forms` suggestions are dynamic by
language + part_of_speech.
**Rationale**: Bottom sheet keeps the user in context; scrollable content area handles
variable-length forms. Avoids full-screen navigation for an editing operation.

## Risks / Trade-offs

**[Risk] EncryptedSharedPreferences maintenance mode**
→ Mitigation: functional for v1; watch for Google's replacement. Migration is isolated to
credential storage only.

**[Risk] SSHJ APK size increase**
→ Mitigation: R8 / ProGuard shrinking in release builds; SSHJ is smaller than Apache SSHD.

**[Risk] Authorization API requires Google Play Services (not available on de-Googled devices)**
→ Acceptable: Google Drive sync is optional; FTP/SFTP are available alternatives. No mitigation.

**[Risk] Atomic file swap on Android — SQLiteDatabase holds file lock**
→ Mitigation: close the `SQLiteDatabase` connection before replacing the file, then reopen.
Wrap in a coroutine with `Dispatchers.IO`. Match the `spawn_blocking` pattern from Rust.

**[Trade-off] DbTableMemory loads all words at startup**
→ Acceptable for v1 (same trade-off as desktop). Vocabulary books rarely exceed a few thousand
entries.
