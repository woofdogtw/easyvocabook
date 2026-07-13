use keyring::Entry;

const SERVICE: &str = "easyvocabook";

pub fn store(key: &str, secret: &str) -> Result<(), String> {
    Entry::new(SERVICE, key)
        .map_err(|e| format!("Keychain entry error: {e}"))?
        .set_password(secret)
        .map_err(|e| format!("Keychain store error: {e}"))
}

pub fn load(key: &str) -> Result<Option<String>, String> {
    let entry = Entry::new(SERVICE, key).map_err(|e| format!("Keychain entry error: {e}"))?;
    match entry.get_password() {
        Ok(s) => Ok(Some(s)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(format!("Keychain load error: {e}")),
    }
}

pub fn delete(key: &str) -> Result<(), String> {
    let entry = Entry::new(SERVICE, key).map_err(|e| format!("Keychain entry error: {e}"))?;
    match entry.delete_credential() {
        Ok(()) => Ok(()),
        Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(format!("Keychain delete error: {e}")),
    }
}

// Well-known keychain keys
pub const FTP_PASSWORD: &str = "ftp_password";
pub const SFTP_PASSWORD: &str = "sftp_password";
pub const DRIVE_ACCESS_TOKEN: &str = "drive_access_token";
pub const DRIVE_REFRESH_TOKEN: &str = "drive_refresh_token";
