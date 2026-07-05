use std::path::Path;
use suppaftp::tokio::AsyncFtpStream;
use tokio::io::AsyncReadExt;

use crate::config::Settings;
use crate::config::keychain;

pub struct FtpClient {
    host: String,
    port: u16,
    username: String,
    directory: String,
    #[allow(dead_code)] // reserved for FTPS implementation
    tls: bool,
    password_override: Option<String>,
}

impl FtpClient {
    pub fn from_settings(s: &Settings) -> Self {
        Self {
            host: s.ftp_host.clone(),
            port: s.ftp_port,
            username: s.ftp_username.clone(),
            directory: s.ftp_directory.clone(),
            tls: s.ftp_tls,
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
        tls: bool,
        password: impl Into<String>,
    ) -> Self {
        Self {
            host: host.into(),
            port,
            username: username.into(),
            directory: directory.into(),
            tls,
            password_override: Some(password.into()),
        }
    }

    async fn connect(&self) -> Result<AsyncFtpStream, String> {
        let addr = format!("{}:{}", self.host, self.port);
        let password = if let Some(p) = &self.password_override {
            p.clone()
        } else {
            keychain::load(keychain::FTP_PASSWORD)
                .map_err(|e| format!("Keychain error: {e}"))?
                .unwrap_or_default()
        };

        let mut ftp = AsyncFtpStream::connect(&addr)
            .await
            .map_err(|e| format!("FTP connect failed: {e}"))?;

        ftp.login(&self.username, &password)
            .await
            .map_err(|e| format!("FTP login failed: {e}"))?;

        Ok(ftp)
    }

    fn remote_path(&self) -> String {
        if self.directory.is_empty() {
            "easyvocabook.db".into()
        } else {
            format!("{}/easyvocabook.db", self.directory.trim_end_matches('/'))
        }
    }
}

impl crate::network::SyncClient for FtpClient {
    fn upload<'a>(
        &'a self,
        local_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let data = tokio::fs::read(local_path)
                .await
                .map_err(|e| format!("Cannot read local file: {e}"))?;
            let mut ftp = self.connect().await?;
            let remote = self.remote_path();
            let cursor = std::io::Cursor::new(data);
            ftp.put_file(&remote, &mut Box::pin(cursor))
                .await
                .map_err(|e| format!("FTP upload failed: {e}"))?;
            ftp.quit().await.ok();
            Ok(())
        })
    }

    fn download<'a>(
        &'a self,
        dest_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>> {
        Box::pin(async move {
            let mut ftp = self.connect().await?;
            let remote = self.remote_path();
            let mut reader = ftp
                .retr_as_stream(&remote)
                .await
                .map_err(|e| format!("FTP download failed: {e}"))?;
            let mut data = Vec::new();
            reader
                .read_to_end(&mut data)
                .await
                .map_err(|e| format!("FTP read failed: {e}"))?;
            ftp.finalize_retr_stream(reader)
                .await
                .map_err(|e| format!("FTP finalize failed: {e}"))?;
            ftp.quit().await.ok();
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
            // Download to a temp file and read db_info.last_modified.
            let tmp = tempfile_path();
            self.download(std::path::Path::new(&tmp)).await?;
            let last_modified = read_last_modified_from(&tmp);
            let _ = std::fs::remove_file(&tmp);
            last_modified
        })
    }
}

fn tempfile_path() -> String {
    std::env::temp_dir()
        .join(format!("easyvocabook_sync_{}.db", std::process::id()))
        .to_string_lossy()
        .into_owned()
}

pub fn read_last_modified_from_pub(path: &str) -> Result<Option<i64>, String> {
    read_last_modified_from(path)
}

fn read_last_modified_from(path: &str) -> Result<Option<i64>, String> {
    match rusqlite::Connection::open(path) {
        Ok(conn) => {
            let v: Result<i64, _> =
                conn.query_row("SELECT last_modified FROM db_info WHERE id = 1", [], |r| {
                    r.get(0)
                });
            Ok(v.ok())
        }
        Err(_) => Ok(None),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::network::SyncClient;
    use tempfile::tempdir;

    /// Build a minimal valid SQLite DB with a known last_modified.
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

    fn client_from_env() -> Option<FtpClient> {
        let host = std::env::var("TEST_FTP_HOST").ok()?;
        let port = std::env::var("TEST_FTP_PORT")
            .ok()
            .and_then(|p| p.parse().ok())
            .unwrap_or(21u16);
        let user = std::env::var("TEST_FTP_USER").ok()?;
        let pass = std::env::var("TEST_FTP_PASS").ok()?;
        let dir = std::env::var("TEST_FTP_DIR").unwrap_or_default();
        Some(FtpClient::with_credentials(host, port, user, dir, false, pass))
    }

    /// Full round-trip: upload a DB, verify remote_last_modified, download and check content.
    /// Skipped when TEST_FTP_HOST / TEST_FTP_USER / TEST_FTP_PASS are not set.
    #[tokio::test]
    async fn ftp_round_trip() {
        let Some(client) = client_from_env() else {
            return;
        };
        let test_lm = 1_750_000_000i64;
        let (_upload_guard, upload_path) = make_test_db(test_lm);

        client.upload(&upload_path).await.expect("FTP upload failed");

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
            .expect("FTP download failed");

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
