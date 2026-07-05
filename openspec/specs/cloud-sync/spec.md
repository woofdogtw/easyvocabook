# cloud-sync Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Whole-file sync model
The system SHALL sync the `easyvocabook.db` file as a single atomic unit (upload or download the
entire file). Record-level merge is not supported.

Before accepting a downloaded DB, the system SHALL check `db_info.version`:
- If remote version > app's supported version → refuse sync, show "Please update the app"
- Otherwise → proceed with the latest-wins decision

#### Scenario: Remote DB version too new
- **WHEN** the downloaded DB has `db_info.version = 2` and the app only supports v1
- **THEN** sync is aborted and the user sees "Please update the app to open this file"

### Requirement: Latest-wins conflict resolution
On triggering sync, the system SHALL compare `local.last_modified` (L) and `remote.last_modified` (R)
and apply the latest-wins rule:

- Remote absent → **upload** (first use of this remote location)
- `L == R` → both sides identical → **no-op**
- `L > R` → local is newer → **upload**
- `R > L` → remote is newer → **download**

No `last_synced` field is stored. There is no conflict dialog — the newer side always wins.

`db_info.last_modified` for a fresh install SHALL be initialized to `0`. This guarantees that any
real remote DB (with `last_modified > 0`) is always considered newer, so the first sync on a new
machine automatically downloads from remote rather than overwriting it.

#### Scenario: No-op when both sides are identical
- **WHEN** `local.last_modified == remote.last_modified`
- **THEN** sync completes immediately with no file transfer

#### Scenario: Download when remote is newer
- **WHEN** `remote.last_modified > local.last_modified`
- **THEN** the local DB is replaced with the remote DB

#### Scenario: Upload when local is newer
- **WHEN** `local.last_modified > remote.last_modified`
- **THEN** the local DB is uploaded to the remote location

#### Scenario: First sync on a new machine downloads remote data
- **WHEN** a fresh install has `db_info.last_modified = 0` and a remote DB exists with `last_modified > 0`
- **THEN** sync downloads the remote DB and replaces the empty local DB

### Requirement: UI lock during sync
While a sync operation is in progress, the system SHALL disable all word edit operations (add,
edit, delete, clear stats) and display a progress indicator. The lock is released when sync
completes or fails.

#### Scenario: Edit button disabled during sync
- **WHEN** a sync is running
- **THEN** the ＋ button and context menu edit/delete options are non-interactive

### Requirement: Atomic download-and-replace
When a download is required, the system SHALL:
1. Download the remote DB to a temporary file in the OS temp directory (`std::env::temp_dir()`),
   named with the process ID to avoid collisions (e.g., `easyvocabook_sync_<pid>.db`)
2. Open the temp file in a dedicated blocking thread, read `db_info.version`, then close it
   (the dedicated thread ensures the OS file handle is fully released before the rename step —
   required on Windows, where SQLite connections opened outside `spawn_blocking` may hold a handle
   that blocks `std::fs::rename`)
3. If the version check passes, atomically rename the temp file over `easyvocabook.db`
4. Delete the temp file if the version check fails

Note: `/tmp/` does not exist on Windows. All temporary paths MUST use `std::env::temp_dir()`.

#### Scenario: Temp file cleaned up on version error
- **WHEN** the downloaded DB version is too new
- **THEN** the temp file is deleted and the local DB is unchanged

### Requirement: DbTableMemory reload after download
After the atomic replace succeeds, the system SHALL:
1. Reopen the SQLite connection to the new `easyvocabook.db`
2. Reload `DbTableMemory` with a full-aggregate load (in a `spawn_blocking` context)
3. Unlock the UI and refresh all views (Word List and Quiz tab)

#### Scenario: Word list reflects downloaded data
- **WHEN** a successful download sync completes
- **THEN** the Word List and Quiz tab immediately show the words from the downloaded database

### Requirement: FTP/FTPS sync
The system SHALL upload and download `easyvocabook.db` over FTP or FTPS. The remote path is
`<directory>/easyvocabook.db` where `<directory>` is from `settings.toml`. Credentials
(except password) are stored in `settings.toml`; password is stored in OS keychain.

#### Scenario: FTP upload succeeds
- **WHEN** the user triggers Sync Now with FTP configured and the local DB is newer
- **THEN** `easyvocabook.db` is uploaded to `<directory>/easyvocabook.db` on the FTP server

### Requirement: SFTP sync
The system SHALL upload and download `easyvocabook.db` over SFTP using a pure-Rust SSH
implementation (no native C library dependency). Credentials are stored in `settings.toml`
and OS keychain.

#### Scenario: SFTP download succeeds
- **WHEN** the user triggers Sync Now with SFTP configured and the remote DB is newer
- **THEN** `easyvocabook.db` is downloaded from the remote path and replaces the local file

### Requirement: Google Drive sync
The system SHALL find (or create) a folder by name in the root of Google Drive using the Drive
API v3. It SHALL upload/download `easyvocabook.db` to/from that folder.

The system SHALL authenticate with Google Drive using OAuth2 PKCE via the system browser.
After the user authorizes the app, access and refresh tokens SHALL be stored in the OS keychain.
The redirect mechanism is platform-specific (PC: loopback HTTP; mobile: custom URL scheme) — see
design.md D7. The access token is refreshed automatically before each sync if expired.

#### Scenario: First-time Google Drive login
- **WHEN** the user taps [Log in to Google Drive] in Settings
- **THEN** the system browser opens the Google authorization page

#### Scenario: Folder created if missing
- **WHEN** the configured Drive folder name does not exist in the user's Drive
- **THEN** the folder is created automatically before uploading

#### Scenario: Token refreshed on expiry
- **WHEN** the stored access token is expired at sync time
- **THEN** the system uses the refresh token to obtain a new access token before syncing

### Requirement: OneDrive sync
The system SHALL provide OneDrive sync with the same behavior as Google Drive sync, using Microsoft
Graph API and Microsoft OAuth2 PKCE endpoints. The folder SHALL be created at the root of the
user's OneDrive if it does not exist.

#### Scenario: OneDrive upload
- **WHEN** the user triggers Sync Now with OneDrive configured and the local DB is newer
- **THEN** `easyvocabook.db` is uploaded to the configured OneDrive folder

### Requirement: Sync credentials security
All OAuth tokens (Google, OneDrive) and FTP/SFTP passwords SHALL be stored exclusively in the
OS keychain / secure credential store. No secrets SHALL appear in `settings.toml` or any other plaintext
file.

#### Scenario: settings.toml contains no secrets
- **WHEN** the app is configured with all sync methods
- **THEN** `settings.toml` contains no password or token fields; only non-secret fields (host, username, folder, etc.)

