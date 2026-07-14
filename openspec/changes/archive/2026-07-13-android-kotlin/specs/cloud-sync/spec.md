## MODIFIED Requirements

### Requirement: SFTP sync
The system SHALL upload and download `easyvocabook.db` over SFTP. The remote path is
`<directory>/easyvocabook.db` where `<directory>` is from the app's settings. Credentials
are stored in app settings (non-secret fields) and a secure store (password).

Platform-specific SSH implementation:
- **PC (Rust)**: pure-Rust SSH library (no native C library dependency); credentials stored in `settings.toml` and OS keychain
- **Android (Kotlin)**: SSHJ (`com.hierynomus:sshj`); credentials stored in SharedPreferences and EncryptedSharedPreferences

#### Scenario: SFTP download succeeds
- **WHEN** the user triggers Sync Now with SFTP configured and the remote DB is newer
- **THEN** `easyvocabook.db` is downloaded from the remote path and replaces the local file

### Requirement: Google Drive sync
The system SHALL find (or create) a folder by name in the root of Google Drive using the Drive
API v3. It SHALL upload/download `easyvocabook.db` to/from that folder.

Authentication is platform-specific:
- **PC (Rust)**: OAuth2 PKCE via the system browser; loopback HTTP redirect on localhost; access and refresh tokens stored in OS keychain; token refreshed manually using stored refresh token before each sync if expired
- **Android (Kotlin)**: Google Identity Services Authorization API (`com.google.android.gms:play-services-auth`); call `Authorization.getClient(context).authorize(request)` with `drive.file` scope before each sync; if `AuthorizationResult.accessToken` is non-null, use it immediately; if result has a `pendingIntent`, launch it to prompt user consent; Play Services manages token refresh silently — the app SHALL NOT store access or refresh tokens

In both cases the Drive REST API (v3) is called directly with the resulting access token.

#### Scenario: First-time Google Drive login on Android
- **WHEN** the user taps [Log in to Google Drive] in Android Settings and no consent has been given
- **THEN** `AuthorizationResult.pendingIntent` is launched, showing the Google consent screen

#### Scenario: Silent token reuse on Android
- **WHEN** the user has previously authorized and taps Sync Now on Android
- **THEN** `authorize()` returns an `accessToken` directly without any UI; sync proceeds immediately

#### Scenario: First-time Google Drive login on PC
- **WHEN** the user clicks [Log in to Google Drive] in PC Settings
- **THEN** the system browser opens the Google OAuth2 PKCE authorization URL

#### Scenario: Folder created if missing
- **WHEN** the configured Drive folder name does not exist in the user's Drive
- **THEN** the folder is created automatically before uploading

### Requirement: Atomic download-and-replace
When a download is required, the system SHALL:
1. Download the remote DB to a temporary file
2. Read `db_info.version` from the temp file to validate it
3. If the version check passes, atomically replace the live database file with the temp file
4. Delete the temp file if the version check fails

Platform-specific details:
- **PC (Rust)**: temp file in `std::env::temp_dir()` named `easyvocabook_sync_<pid>.db`; version read in a dedicated `spawn_blocking` thread to ensure the OS file handle is fully released before `std::fs::rename`; required on Windows where handles block rename
- **Android (Kotlin)**: temp file in `context.cacheDir` (e.g., `easyvocabook_sync.db`); close the active `SQLiteDatabase` connection before replacing `filesDir/easyvocabook.db`; wrap the entire download-and-replace sequence in `withContext(Dispatchers.IO)`; reopen the connection after replace

#### Scenario: Temp file cleaned up on version error
- **WHEN** the downloaded DB version is too new
- **THEN** the temp file is deleted and the live database file is unchanged

#### Scenario: Android closes DB before file replace
- **WHEN** an Android sync requires a download
- **THEN** the `SQLiteDatabase` connection is closed before the file is replaced and reopened after

### Requirement: DbTableMemory reload after download
After the atomic replace succeeds, the system SHALL:
1. Reopen the database connection to the new file
2. Reload `DbTableMemory` with a full-aggregate load (off the UI thread)
3. Release the UI lock and refresh all views (Word List and Quiz tab)

Platform-specific details:
- **PC (Rust)**: reopen via `spawn_blocking`; signal iced to refresh subscriptions
- **Android (Kotlin)**: reopen `SQLiteDatabase` in `withContext(Dispatchers.IO)`; reload `DbTableMemory`; update the shared `StateFlow<UiState>` to trigger Compose recomposition across all screens

#### Scenario: Word list reflects downloaded data
- **WHEN** a successful download sync completes
- **THEN** the Word List and Quiz tab immediately show the words from the downloaded database

### Requirement: Sync credentials security
All FTP/SFTP passwords and, on PC, OAuth tokens (Google Drive) SHALL be stored exclusively in a
platform-appropriate secure store. No secrets SHALL appear in plaintext configuration files.

Platform-specific secure stores:
- **PC (Rust)**: OS keychain via the `keyring` crate for both OAuth tokens and FTP/SFTP passwords; `settings.toml` contains no secrets
- **Android (Kotlin)**: FTP/SFTP passwords stored in `EncryptedSharedPreferences` (`androidx.security:security-crypto`); Google Drive tokens are held by Play Services and SHALL NOT be stored by the app; `SharedPreferences` contains no passwords or tokens

#### Scenario: Android settings contain no secrets
- **WHEN** the Android app is configured with Google Drive and SFTP
- **THEN** `SharedPreferences` contains no password or token fields; only non-secret fields (host, username, folder, etc.)

## ADDED Requirements

### Requirement: remoteLastModified reads db_info from within the file
To determine the remote side's timestamp for the latest-wins decision, the system SHALL download
the remote DB file to a temporary path, open it with SQLite (read-only), read `db_info.last_modified`,
delete the temp file, and return the value. This applies to all sync backends (FTP, FTPS, SFTP,
Google Drive) uniformly.

No provider metadata SHALL be used for this purpose: `stat().mtime`, FTP MDTM, Drive `modifiedTime`,
Drive `appProperties`, or any other storage-layer field are all forbidden as timestamp sources.
These reflect upload time or file-system events, not the time vocabulary data was last edited.

#### Scenario: remoteLastModified reads from within the file
- **WHEN** any backend's `remoteLastModified()` is called
- **THEN** the returned value is `db_info.last_modified` read from within the downloaded temp copy of the remote DB file, not from any provider metadata field

#### Scenario: Remote file absent returns null
- **WHEN** the remote DB file does not exist at the configured path
- **THEN** `remoteLastModified()` returns null and sync proceeds to upload

## REMOVED Requirements

### Requirement: OneDrive sync
**Reason**: OneDrive is removed from all platforms. Android will not implement OneDrive; maintaining the dead code on the desktop serves no purpose.
**Migration**: Users relying on OneDrive sync must migrate to Google Drive, FTP, or SFTP before updating to this version.
