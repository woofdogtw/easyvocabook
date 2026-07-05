pub mod keychain;

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub ui_language: String,
    pub theme: Theme,
    pub sync_method: SyncMethod,
    pub last_word_language: String,

    // FTP / SFTP fields (password stored in keychain, not here)
    pub ftp_host: String,
    pub ftp_port: u16,
    pub ftp_username: String,
    pub ftp_directory: String,
    pub ftp_tls: bool,

    pub sftp_host: String,
    pub sftp_port: u16,
    pub sftp_username: String,
    pub sftp_directory: String,

    // Google Drive
    pub drive_folder: String,

    // OneDrive
    pub onedrive_folder: String,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            ui_language: "en".into(),
            theme: Theme::Auto,
            sync_method: SyncMethod::Disabled,
            last_word_language: "en".into(),
            ftp_host: String::new(),
            ftp_port: 21,
            ftp_username: String::new(),
            ftp_directory: String::new(),
            ftp_tls: false,
            sftp_host: String::new(),
            sftp_port: 22,
            sftp_username: String::new(),
            sftp_directory: String::new(),
            drive_folder: "EasyVocaBook".into(),
            onedrive_folder: "EasyVocaBook".into(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Theme {
    Light,
    Dark,
    #[default]
    Auto,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum SyncMethod {
    #[default]
    Disabled,
    Ftp,
    Sftp,
    GoogleDrive,
    OneDrive,
}

fn config_path() -> PathBuf {
    dirs::config_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("easyvocabook")
        .join("settings.toml")
}

impl Settings {
    pub fn load() -> Self {
        let path = config_path();
        std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| toml::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) -> Result<(), String> {
        let path = config_path();
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)
                .map_err(|e| format!("Cannot create config dir: {e}"))?;
        }
        let text =
            toml::to_string_pretty(self).map_err(|e| format!("Cannot serialize settings: {e}"))?;
        std::fs::write(&path, text).map_err(|e| format!("Cannot write settings: {e}"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_default() {
        let s = Settings::default();
        let toml = toml::to_string_pretty(&s).unwrap();
        let s2: Settings = toml::from_str(&toml).unwrap();
        assert_eq!(s.ui_language, s2.ui_language);
        assert_eq!(s.ftp_port, s2.ftp_port);
        assert_eq!(s.sftp_port, s2.sftp_port);
        assert_eq!(s.drive_folder, s2.drive_folder);
    }

    #[test]
    fn missing_fields_use_defaults() {
        let minimal = r#"ui_language = "zh-TW""#;
        let s: Settings = toml::from_str(minimal).unwrap();
        assert_eq!(s.ui_language, "zh-TW");
        assert_eq!(s.ftp_port, 21);
        assert_eq!(s.theme, Theme::Auto);
    }
}
