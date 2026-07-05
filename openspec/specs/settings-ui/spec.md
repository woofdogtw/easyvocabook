# settings-ui Specification

## Purpose
TBD - created by archiving change rust-desktop. Update Purpose after archive.
## Requirements
### Requirement: Settings tab layout
The Settings tab SHALL display a scrollable area with three sections (App, Sync, Practice) and
a fixed non-scrolling footer showing app version and copyright.

#### Scenario: Footer always visible
- **WHEN** the user scrolls the settings content
- **THEN** the footer "EasyVocaBook vX.Y.Z · MIT © 2026 Chien-Hong Chan" remains visible at the bottom

### Requirement: App section — UI language
The App section SHALL provide a dropdown to select the UI display language. Options:
English, 繁體中文 (zh-TW), 简体中文 (zh-CN).

The change SHALL take effect immediately without restarting the application. The selected language
SHALL be stored in `settings.toml` as a language code (e.g., `en`, `zh-TW`).

#### Scenario: Language switches instantly
- **WHEN** the user selects 繁體中文 in the language dropdown
- **THEN** all UI strings switch to Traditional Chinese immediately

### Requirement: App section — theme
The App section SHALL provide three radio buttons for theme: Light, Dark, Auto (follow system).
The selected theme SHALL apply immediately and be stored in `settings.toml`.

#### Scenario: Auto follows system dark mode
- **WHEN** the user selects Auto and the OS is in dark mode
- **THEN** the app renders in dark theme

### Requirement: Sync section — method selection
The Sync section SHALL provide radio buttons: Disabled, FTP/FTPS, SFTP, Google Drive, OneDrive.
Only the fields relevant to the selected method SHALL be visible; all other method fields SHALL
be collapsed. The selection SHALL be stored in `settings.toml`.

#### Scenario: FTP selected shows only FTP fields
- **WHEN** the user selects FTP/FTPS
- **THEN** Host, Port, Username, Password, Directory fields are visible; Google Drive and OneDrive
  fields are not rendered

### Requirement: FTP/FTPS/SFTP configuration
When FTP/FTPS or SFTP is selected, the system SHALL display: Host (text), Port (number, default 21
for FTP / 22 for SFTP), Username (text), Password (password field), Directory (text).

On save, the password SHALL be stored in the OS keychain. Host, port, username, and directory
SHALL be stored in `settings.toml`.

#### Scenario: Password stored in keychain not config file
- **WHEN** the user saves FTP settings with a password
- **THEN** the password is stored in OS keychain and `settings.toml` contains no password field

### Requirement: Google Drive configuration
When Google Drive is selected, the system SHALL display:
- If not logged in: **[Log in to Google Drive]** button
- If logged in: "✓ Logged in: user@gmail.com" and **[Log out]** button
- Folder name (text input): the Drive folder name where the DB file is stored; created automatically
  if it does not exist. Stored in `settings.toml`.

Tapping [Log in] SHALL open the system browser for OAuth2 PKCE authentication. Tokens SHALL be
stored in OS keychain. No "remember login" checkbox is needed.

#### Scenario: Login opens system browser
- **WHEN** the user clicks [Log in to Google Drive]
- **THEN** the default system browser opens the Google OAuth authorization URL

#### Scenario: Logged-in state shows email
- **WHEN** the user has previously authenticated
- **THEN** the settings page shows "✓ Logged in: user@gmail.com" and a [Log out] button

### Requirement: OneDrive configuration
The system SHALL provide OneDrive configuration with the same fields and behavior as Google Drive
configuration, using OneDrive branding and Microsoft OAuth endpoints.

#### Scenario: OneDrive login
- **WHEN** the user clicks [Log in to OneDrive]
- **THEN** the system browser opens the Microsoft OAuth authorization URL

### Requirement: Sync Now button
A **[Sync Now]** button SHALL be present in the Sync section (and also in the Word List action bar
as 🔄). Tapping it SHALL trigger an immediate sync with the configured service.

#### Scenario: Sync Now disabled when Disabled is selected
- **WHEN** the sync method is set to Disabled
- **THEN** the [Sync Now] button is greyed out and non-interactive

### Requirement: Practice section — clear statistics
The Practice section SHALL provide a **[Clear Practice Statistics]** button. Tapping it SHALL show
a confirmation dialog. On confirmation, `practice_count`, `correct_count`, and `practiced_at` are
reset to 0/0/NULL for all words, and `db_info.last_modified` is bumped.

#### Scenario: Confirmation required before clearing
- **WHEN** the user taps [Clear Practice Statistics]
- **THEN** a dialog asks "Reset all practice statistics? This cannot be undone." with [Cancel] and [Reset] buttons

#### Scenario: Cancel does not clear
- **WHEN** the user taps [Cancel] in the confirmation dialog
- **THEN** no statistics are changed

