## MODIFIED Requirements

### Requirement: Sync section — method selection
The Sync section SHALL provide radio buttons: Disabled, FTP/FTPS, SFTP, Google Drive.
Only the fields relevant to the selected method SHALL be visible; all other method fields SHALL
be collapsed. The selection SHALL be stored in the platform-appropriate settings store
(`settings.toml` on PC, `SharedPreferences` on Android).

#### Scenario: FTP selected shows only FTP fields
- **WHEN** the user selects FTP/FTPS
- **THEN** Host, Port, Username, Password, Directory fields are visible; SFTP and Google Drive fields are not rendered

#### Scenario: Sync Now disabled when Disabled is selected
- **WHEN** the sync method is set to Disabled
- **THEN** the [Sync Now] button is greyed out and non-interactive

### Requirement: Google Drive configuration
When Google Drive is selected, the system SHALL display:
- If not logged in: **[Log in to Google Drive]** button
- If logged in: "✓ Logged in: user@gmail.com" (if email available) and **[Log out]** button
- Folder name (text input): the Drive folder name where the DB file is stored; created automatically
  if it does not exist

The folder name SHALL be stored in the platform-appropriate settings store.

Login behavior is platform-specific:
- **PC (Rust)**: tapping [Log in] opens the system browser for OAuth2 PKCE authentication; tokens stored in OS keychain
- **Android (Kotlin)**: tapping [Log in] calls `Authorization.getClient(context).authorize(request)`;
  if consent is required, the returned `pendingIntent` is launched; Play Services manages tokens — the app stores only the folder name in `SharedPreferences`

#### Scenario: Android Google Drive login shows consent screen when needed
- **WHEN** the user taps [Log in to Google Drive] on Android and no consent exists
- **THEN** the `pendingIntent` from `AuthorizationResult` is launched, showing the Google consent screen

#### Scenario: PC Google Drive login opens system browser
- **WHEN** the user clicks [Log in to Google Drive] on PC
- **THEN** the default system browser opens the Google OAuth2 PKCE authorization URL

#### Scenario: Logged-in state shows confirmation
- **WHEN** the user has previously authenticated on either platform
- **THEN** the settings page shows "✓ Logged in" (and email if available) with a [Log out] button

#### Scenario: Folder created if missing
- **WHEN** the configured Drive folder name does not exist in the user's Drive
- **THEN** the folder is created automatically before uploading

## ADDED Requirements

### Requirement: Android settings screen — Compose structure
On Android, the Settings screen SHALL be implemented as a scrollable `LazyColumn` Composable
backed by a `SettingsViewModel` exposing `StateFlow<SettingsUiState>`. All setting values SHALL
be loaded from `SharedPreferences` on screen entry and saved back on each change.

The screen SHALL have four sections rendered as `ListItem` groups or equivalent Material 3
components: **App**, **Sync**, **Practice**, **About**.

#### Scenario: Android settings screen loads current values
- **WHEN** the Android Settings screen enters composition
- **THEN** all fields reflect the current values from `SharedPreferences`

### Requirement: Android app section — UI language
On Android, the language selector SHALL store the selected language code in
`SharedPreferences` (`SP_UI_LANGUAGE`). Changing the language SHALL trigger recreation of the
`Activity` (via `recreate()`) so the locale takes effect immediately across all Compose
composables.

Available options: English (`en`), Traditional Chinese (`zh-TW`), Simplified Chinese (`zh-CN`).

#### Scenario: Android language change applies immediately
- **WHEN** the user selects 繁體中文 on the Android Settings screen
- **THEN** `SP_UI_LANGUAGE = "zh-TW"` is stored and `Activity.recreate()` is called, switching all UI strings

### Requirement: Android app section — theme
On Android, the theme selector SHALL store the selection in `SharedPreferences`
(`SP_THEME`: `light`, `dark`, or `auto`). The selection SHALL be passed to
`MaterialTheme { dynamicColorScheme / darkColorScheme / lightColorScheme }` at the app root.
`auto` follows the system `isSystemInDarkTheme()` value.

#### Scenario: Android Auto theme follows system
- **WHEN** the user selects Auto and the OS is in dark mode
- **THEN** the app renders with the dark color scheme

### Requirement: Android FTP/SFTP credentials — EncryptedSharedPreferences
On Android, FTP/FTPS and SFTP passwords SHALL be stored in `EncryptedSharedPreferences`
(`androidx.security:security-crypto`). Non-secret fields (host, port, username, directory,
sync method) SHALL be stored in plain `SharedPreferences`. No password or token SHALL be stored
in plain `SharedPreferences`.

#### Scenario: Android SFTP password stored encrypted
- **WHEN** the user saves SFTP settings with a password on Android
- **THEN** the password is stored in `EncryptedSharedPreferences`; plain `SharedPreferences` contains no password field

## REMOVED Requirements

### Requirement: OneDrive configuration
**Reason**: OneDrive is removed from all platforms. Android will not implement OneDrive; removing it from the desktop spec eliminates dead code and simplifies the sync provider list.
**Migration**: Users relying on OneDrive sync must migrate to Google Drive, FTP, or SFTP before updating.
