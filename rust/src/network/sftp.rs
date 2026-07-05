use russh::client;
use russh_sftp::client::SftpSession;
use std::path::Path;

use crate::config::Settings;
use crate::config::keychain;

pub struct SftpClient {
    host: String,
    port: u16,
    username: String,
    directory: String,
    password_override: Option<String>,
}

impl SftpClient {
    pub fn from_settings(s: &Settings) -> Self {
        Self {
            host: s.sftp_host.clone(),
            port: s.sftp_port,
            username: s.sftp_username.clone(),
            directory: s.sftp_directory.clone(),
            password_override: None,
        }
    }

    /// Test constructor: bypasses OS keychain so tests can run in CI without DBus/keychain.
    #[cfg(test)]
    pub fn with_credentials(
        host: impl Into<String>,
        port: u16,
        username: impl Into<String>,
        directory: impl Into<String>,
        password: impl Into<String>,
    ) -> Self {
        Self {
            host: host.into(),
            port,
            username: username.into(),
            directory: directory.into(),
            password_override: Some(password.into()),
        }
    }

    fn remote_path(&self) -> String {
        if self.directory.is_empty() {
            "easyvocabook.db".into()
        } else {
            format!("{}/easyvocabook.db", self.directory.trim_end_matches('/'))
        }
    }

    async fn open_sftp(&self) -> Result<(client::Handle<DummyHandler>, SftpSession), String> {
        let password = if let Some(p) = &self.password_override {
            p.clone()
        } else {
            keychain::load(keychain::SFTP_PASSWORD)
                .map_err(|e| format!("Keychain error: {e}"))?
                .unwrap_or_default()
        };

        let config = std::sync::Arc::new(client::Config::default());
        let mut session = client::connect(config, (self.host.as_str(), self.port), DummyHandler)
            .await
            .map_err(|e| format!("SFTP connect failed: {e}"))?;

        let auth_result = session
            .authenticate_password(&self.username, &password)
            .await
            .map_err(|e| format!("SFTP auth failed: {e}"))?;
        if !auth_result.success() {
            return Err("SFTP authentication rejected".into());
        }

        let channel = session
            .channel_open_session()
            .await
            .map_err(|e| format!("SFTP channel failed: {e}"))?;
        channel
            .request_subsystem(true, "sftp")
            .await
            .map_err(|e| format!("SFTP subsystem failed: {e}"))?;
        let sftp = SftpSession::new(channel.into_stream())
            .await
            .map_err(|e| format!("SFTP session failed: {e}"))?;
        Ok((session, sftp))
    }
}

struct DummyHandler;
impl client::Handler for DummyHandler {
    type Error = russh::Error;
    async fn check_server_key(
        &mut self,
        _server_public_key: &russh::keys::PublicKey,
    ) -> Result<bool, Self::Error> {
        Ok(true) // Accept all host keys (production: verify against known_hosts)
    }
}

impl crate::network::SyncClient for SftpClient {
    fn upload<'a>(
        &'a self,
        local_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let data = tokio::fs::read(local_path)
                .await
                .map_err(|e| format!("Cannot read local file: {e}"))?;
            let (_session, sftp) = self.open_sftp().await?;
            let remote = self.remote_path();
            let mut file = sftp
                .create(&remote)
                .await
                .map_err(|e| format!("SFTP create failed: {e}"))?;
            use tokio::io::AsyncWriteExt;
            file.write_all(&data)
                .await
                .map_err(|e| format!("SFTP write failed: {e}"))?;
            Ok(())
        })
    }

    fn download<'a>(
        &'a self,
        dest_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let (_session, sftp) = self.open_sftp().await?;
            let remote = self.remote_path();
            let mut file = sftp
                .open(&remote)
                .await
                .map_err(|e| format!("SFTP open failed: {e}"))?;
            let mut data = Vec::new();
            use tokio::io::AsyncReadExt;
            file.read_to_end(&mut data)
                .await
                .map_err(|e| format!("SFTP read failed: {e}"))?;
            tokio::fs::write(dest_path, data)
                .await
                .map_err(|e| format!("Cannot write local file: {e}"))
        })
    }

    fn remote_last_modified<'a>(
        &'a self,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Option<i64>, String>> + Send + 'a>>
    {
        Box::pin(async move {
            let tmp = std::env::temp_dir()
                .join(format!("easyvocabook_sftp_{}.db", std::process::id()));
            self.download(&tmp).await?;
            let last_modified =
                crate::network::ftp::read_last_modified_from_pub(&tmp.to_string_lossy());
            let _ = std::fs::remove_file(&tmp);
            last_modified
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::network::SyncClient;
    use tempfile::tempdir;

    fn make_test_db(last_modified: i64) -> (tempfile::TempDir, std::path::PathBuf) {
        let dir = tempdir().unwrap();
        let path = dir.path().join("test.db");
        let conn = rusqlite::Connection::open(&path).unwrap();
        crate::db::schema::create_schema(&conn).unwrap();
        crate::db::schema::seed_db_info(&conn, "test").unwrap();
        conn.execute(
            "UPDATE db_info SET last_modified = ?1 WHERE id = 1",
            [last_modified],
        )
        .unwrap();
        (dir, path)
    }

    fn client_from_env() -> Option<SftpClient> {
        let host = std::env::var("TEST_SFTP_HOST").ok()?;
        let port = std::env::var("TEST_SFTP_PORT")
            .ok()
            .and_then(|p| p.parse().ok())
            .unwrap_or(22u16);
        let user = std::env::var("TEST_SFTP_USER").ok()?;
        let pass = std::env::var("TEST_SFTP_PASS").ok()?;
        let dir = std::env::var("TEST_SFTP_DIR").unwrap_or_default();
        Some(SftpClient::with_credentials(host, port, user, dir, pass))
    }

    /// Full round-trip: upload a DB, verify remote_last_modified, download and check content.
    /// Skipped when TEST_SFTP_HOST / TEST_SFTP_USER / TEST_SFTP_PASS are not set.
    #[tokio::test]
    async fn sftp_round_trip() {
        let Some(client) = client_from_env() else {
            return;
        };
        let test_lm = 1_750_000_001i64;
        let (_upload_guard, upload_path) = make_test_db(test_lm);

        client.upload(&upload_path).await.expect("SFTP upload failed");

        let remote_lm = client
            .remote_last_modified()
            .await
            .expect("remote_last_modified failed");
        assert_eq!(remote_lm, Some(test_lm), "remote last_modified mismatch after upload");

        let download_dir = tempdir().unwrap();
        let download_path = download_dir.path().join("downloaded.db");
        client
            .download(&download_path)
            .await
            .expect("SFTP download failed");

        let conn = rusqlite::Connection::open(&download_path).unwrap();
        let downloaded_lm: i64 = conn
            .query_row(
                "SELECT last_modified FROM db_info WHERE id = 1",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(downloaded_lm, test_lm, "downloaded last_modified mismatch");
    }
}
