pub mod drive;
pub mod ftp;
pub mod oauth;
pub mod sftp;
pub mod sync;

use std::path::Path;

/// Common interface for all sync backends.
pub trait SyncClient: Send + Sync {
    /// Upload `local_path` to the remote storage location.
    fn upload<'a>(
        &'a self,
        local_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>>;
    /// Download from remote storage into `dest_path`.
    fn download<'a>(
        &'a self,
        dest_path: &'a Path,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<(), String>> + Send + 'a>>;
    /// Fetch `db_info.last_modified` from the remote file without downloading the full file.
    /// Returns `None` if the remote file doesn't exist yet.
    fn remote_last_modified<'a>(
        &'a self,
    ) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<Option<i64>, String>> + Send + 'a>>;
}
