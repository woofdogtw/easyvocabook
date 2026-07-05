pub mod labels;
pub mod memory;
pub mod schema;
pub mod sqlite;
pub mod types;

use rusqlite::Connection;
use std::path::PathBuf;

pub use memory::DbTableMemory;
pub use sqlite::{DbTableBase, DbTableSQLite};
pub use types::*;

/// Resolve the platform-appropriate path for `easyvocabook.db`.
pub fn db_path() -> PathBuf {
    dirs::data_local_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("easyvocabook")
        .join("easyvocabook.db")
}

/// Open (or create) the database, enable FK enforcement, run schema + migrations.
/// Returns an error string if the DB version is newer than the app supports.
pub fn open_db(path: &PathBuf) -> std::result::Result<Connection, String> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("Cannot create DB directory: {e}"))?;
    }

    let conn = Connection::open(path).map_err(|e| format!("Cannot open database: {e}"))?;

    conn.execute_batch("PRAGMA foreign_keys = ON;")
        .map_err(|e| format!("Cannot enable foreign keys: {e}"))?;

    let version: Option<i64> = conn
        .query_row("SELECT version FROM db_info WHERE id = 1", [], |row| {
            row.get(0)
        })
        .ok();

    match version {
        None => {
            // Fresh database — create schema and seed db_info.
            schema::create_schema(&conn).map_err(|e| format!("Schema creation failed: {e}"))?;
            schema::seed_db_info(&conn, "My Vocabulary Book")
                .map_err(|e| format!("DB init failed: {e}"))?;
        }
        Some(v) if v > schema::CURRENT_VERSION => {
            return Err(format!(
                "Please update the app to open this file (DB version {v}, app supports {})",
                schema::CURRENT_VERSION
            ));
        }
        Some(v) => {
            schema::migrate(&conn, v).map_err(|e| format!("Migration failed: {e}"))?;
        }
    }

    Ok(conn)
}
