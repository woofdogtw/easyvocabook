use std::path::PathBuf;

use crate::network::SyncClient;

#[derive(Debug, Clone, PartialEq)]
pub enum SyncResult {
    NoOp,
    Uploaded,
    Downloaded,
    Error(String),
}

/// Pure last-modified-wins decision — no I/O.
#[derive(Debug, PartialEq)]
pub enum SyncDecision {
    NoOp,
    Upload,
    Download,
}

/// Compare local vs remote last_modified; the newer one wins.
pub fn decide(local_lm: i64, remote_lm: Option<i64>) -> SyncDecision {
    match remote_lm {
        None => SyncDecision::Upload,
        Some(r) if r == local_lm => SyncDecision::NoOp,
        Some(r) if local_lm > r => SyncDecision::Upload,
        Some(_) => SyncDecision::Download,
    }
}

/// Run the sync: read DB timestamps, decide, then execute the required I/O.
#[allow(dead_code)]
pub async fn run_sync(client: &dyn SyncClient, db_path: &PathBuf) -> SyncResult {
    let local_lm = match read_local_last_modified(db_path) {
        Ok(v) => v,
        Err(e) => return SyncResult::Error(e),
    };
    let remote_lm = match client.remote_last_modified().await {
        Ok(v) => v,
        Err(e) => return SyncResult::Error(e),
    };

    match decide(local_lm, remote_lm) {
        SyncDecision::NoOp => SyncResult::NoOp,
        SyncDecision::Upload => match client.upload(db_path).await {
            Ok(()) => SyncResult::Uploaded,
            Err(e) => SyncResult::Error(e),
        },
        SyncDecision::Download => match download_and_reload(client, db_path).await {
            Ok(()) => SyncResult::Downloaded,
            Err(e) => SyncResult::Error(e),
        },
    }
}

pub async fn download_and_reload(client: &dyn SyncClient, db_path: &PathBuf) -> Result<(), String> {
    let tmp = db_path.with_extension("db.tmp");
    client.download(&tmp).await?;

    // Run the version check in spawn_blocking so the rusqlite connection is opened and
    // fully closed (OS handle released) on a dedicated thread before we rename on Windows.
    let tmp2 = tmp.clone();
    let ver_result = tokio::task::spawn_blocking(move || -> Result<(), String> {
        let remote_ver = read_db_version(&tmp2).unwrap_or(0);
        if remote_ver > crate::db::schema::CURRENT_VERSION {
            return Err(format!(
                "Remote DB version {remote_ver} is newer than this app supports ({}). \
                 Please update the app first.",
                crate::db::schema::CURRENT_VERSION
            ));
        }
        Ok(())
    })
    .await
    .map_err(|e| format!("Thread error: {e}"))??;
    let _ = ver_result;

    std::fs::rename(&tmp, db_path).map_err(|e| format!("Cannot replace local DB: {e}"))
}

fn read_db_version(path: &std::path::Path) -> Result<i64, String> {
    let conn =
        rusqlite::Connection::open(path).map_err(|e| format!("Cannot open downloaded DB: {e}"))?;
    conn.query_row("SELECT version FROM db_info WHERE id = 1", [], |r| r.get(0))
        .map_err(|e| format!("Cannot read version from downloaded DB: {e}"))
}

pub async fn read_local_last_modified_async(db_path: PathBuf) -> Result<i64, String> {
    tokio::task::spawn_blocking(move || read_local_last_modified(&db_path))
        .await
        .map_err(|e| format!("Thread error: {e}"))?
}

pub fn read_local_last_modified(db_path: &PathBuf) -> Result<i64, String> {
    let conn =
        rusqlite::Connection::open(db_path).map_err(|e| format!("Cannot open local DB: {e}"))?;
    conn.query_row("SELECT last_modified FROM db_info WHERE id = 1", [], |r| {
        r.get(0)
    })
    .map_err(|e| format!("Cannot read last_modified: {e}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decide_upload_when_remote_empty() {
        assert_eq!(decide(100, None), SyncDecision::Upload);
    }

    #[test]
    fn decide_noop_when_equal() {
        assert_eq!(decide(100, Some(100)), SyncDecision::NoOp);
    }

    #[test]
    fn decide_upload_when_local_newer() {
        assert_eq!(decide(200, Some(100)), SyncDecision::Upload);
    }

    #[test]
    fn decide_download_when_remote_newer() {
        assert_eq!(decide(100, Some(200)), SyncDecision::Download);
    }
}
