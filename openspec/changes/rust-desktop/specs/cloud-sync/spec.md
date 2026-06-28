## ADDED Requirements

### Requirement: Whole-file sync model
The system SHALL sync the `easyvocabook.db` file as a single atomic unit (upload or download the
entire file). Record-level merge is not supported.

Before accepting a downloaded DB, the system SHALL check `db_info.version`:
- If remote version > app's supported version → refuse sync, show "Please update the app"
- Otherwise → proceed with conflict detection

#### Scenario: Remote DB version too new
- **WHEN** the downloaded DB has `db_info.version = 2` and the app only supports v1
- **THEN** sync is aborted and the user sees "Please update the app to open this file"

### Requirement: Three-way conflict detection
The system SHALL store `last_synced` in `settings.toml` after every successful sync.
`last_synced` SHALL store the **`db_info.last_modified` value of the DB at the time of that sync**
(not the wall-clock time of when the sync occurred). Using wall-clock time would always differ from
`db_info.last_modified`, making every sync appear as a conflict.

On triggering sync, compare local `db_info.last_modified` (L), remote `db_info.last_modified` (R),
and `last_synced` (S):

- `L == R` → both sides identical → **no-op** (skip transfer, no `last_synced` update needed)
- `L == S` and `R != S` → only remote changed → safe **download** (fast-forward)
- `R == S` and `L != S` → only local changed → safe **upload** (fast-forward)
- `L != S` and `R != S` → both changed → **conflict** → prompt user

After a successful transfer, set `last_synced = db_info.last_modified` of the resulting DB.

#### Scenario: No-op when both sides are identical
- **WHEN** `local.last_modified == remote.last_modified`
- **THEN** sync completes immediately with no file transfer

#### Scenario: Fast-forward download
- **WHEN** `local.last_modified == last_synced` and `remote.last_modified != last_synced`
- **THEN** the local DB is replaced with the remote DB and `last_synced` is set to `remote.last_modified`

#### Scenario: True conflict prompts user
- **WHEN** both `local.last_modified` and `remote.last_modified` differ from `last_synced`
- **THEN** a dialog appears: "Both local and remote have changes. Keep local / Use remote?"

#### Scenario: User chooses "Use remote" on conflict
- **WHEN** the user selects "Use remote" in the conflict dialog
- **THEN** the local DB is replaced with the remote DB; `last_synced` is set to `remote.last_modified`

### Requirement: UI lock during sync
While a sync operation is in progress, the system SHALL disable all word edit operations (add,
edit, delete, clear stats) and display a progress indicator. The lock is released when sync
completes or fails.

#### Scenario: Edit button disabled during sync
- **WHEN** a sync is running
- **THEN** the ＋ button and context menu edit/delete options are non-interactive

### Requirement: DbTableMemory reload after download
After a successful download sync, the system SHALL:
1. Close the SQLite connection
2. Replace the local `easyvocabook.db` with the downloaded file
3. Reopen the SQLite connection
4. Reload `DbTableMemory` with a full-aggregate load
5. Unlock the UI and refresh all views

#### Scenario: Word list reflects downloaded data
- **WHEN** a successful download sync completes
- **THEN** the Word List immediately shows the words from the downloaded database

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
Same behavior as Google Drive sync using Microsoft Graph API and Microsoft OAuth2 PKCE endpoints.
Folder is created at the root of the user's OneDrive if it does not exist.

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
