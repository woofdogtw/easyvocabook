use rusqlite::{Connection, Result, params};

use crate::db::schema::now_epoch;
use crate::db::types::*;

pub trait DbTableBase {
    fn get_book_info(&self) -> Result<BookInfo>;
    #[allow(dead_code)]
    fn update_book_info(&self, info: &BookInfo) -> Result<()>;
    fn get_word(&self, id: i64) -> Option<WordEntry>;
    fn create_word(&self, word: &NewWord) -> Result<i64>;
    fn update_word(&self, id: i64, word: &UpdateWord) -> Result<()>;
    fn delete_word(&self, id: i64) -> Result<()>;
    fn clear_practice_stats(&self) -> Result<()>;
    fn update_practice_stats(&self, word_id: i64, correct: bool, practiced_at: i64) -> Result<()>;
}

pub struct DbTableSQLite {
    pub conn: Connection,
}

impl DbTableSQLite {
    pub fn open(path: &std::path::Path) -> std::result::Result<Self, String> {
        let path_buf = path.to_path_buf();
        let conn = crate::db::open_db(&path_buf)?;
        Ok(Self { conn })
    }

    fn load_sub_records(
        &self,
        word_id: i64,
    ) -> Result<(Vec<String>, Vec<WordForm>, Vec<Sentence>)> {
        let mut stmt = self
            .conn
            .prepare("SELECT meaning FROM word_meanings WHERE word_id = ?1 ORDER BY id")?;
        let meanings: Vec<String> = stmt
            .query_map(params![word_id], |row| row.get(0))?
            .collect::<Result<Vec<_>>>()?;

        let mut stmt = self
            .conn
            .prepare("SELECT id, label, value FROM word_forms WHERE word_id = ?1 ORDER BY id")?;
        let forms: Vec<WordForm> = stmt
            .query_map(params![word_id], |row| {
                Ok(WordForm {
                    id: row.get(0)?,
                    label: row.get(1)?,
                    value: row.get(2)?,
                })
            })?
            .collect::<Result<Vec<_>>>()?;

        let mut stmt = self.conn.prepare(
            "SELECT id, sentence, translation FROM sentences WHERE word_id = ?1 ORDER BY id",
        )?;
        let sentences: Vec<Sentence> = stmt
            .query_map(params![word_id], |row| {
                Ok(Sentence {
                    id: row.get(0)?,
                    sentence: row.get(1)?,
                    translation: row.get(2)?,
                })
            })?
            .collect::<Result<Vec<_>>>()?;

        Ok((meanings, forms, sentences))
    }

    fn row_to_entry(
        &self,
        id: i64,
        word: String,
        reading: Option<String>,
        meaning: String,
        part_of_speech: Option<String>,
        note: Option<String>,
        language: String,
        practice_count: i64,
        correct_count: i64,
        created_at: i64,
        practiced_at: Option<i64>,
    ) -> Result<WordEntry> {
        let (meanings, forms, sentences) = self.load_sub_records(id)?;
        Ok(WordEntry {
            id,
            word,
            reading,
            meaning,
            part_of_speech,
            note,
            language,
            practice_count,
            correct_count,
            created_at,
            practiced_at,
            meanings,
            forms,
            sentences,
        })
    }

    fn bump_last_modified(&self) -> Result<()> {
        let now = now_epoch();
        self.conn.execute(
            "UPDATE db_info SET last_modified = ?1 WHERE id = 1",
            params![now],
        )?;
        Ok(())
    }
}

impl DbTableBase for DbTableSQLite {
    fn get_book_info(&self) -> Result<BookInfo> {
        self.conn.query_row(
            "SELECT name, description, default_language, version, last_modified FROM db_info WHERE id = 1",
            [],
            |row| Ok(BookInfo {
                name: row.get(0)?,
                description: row.get(1)?,
                default_language: row.get(2)?,
                version: row.get(3)?,
                last_modified: row.get(4)?,
            }),
        )
    }

    fn update_book_info(&self, info: &BookInfo) -> Result<()> {
        self.conn.execute(
            "UPDATE db_info SET name=?1, description=?2, default_language=?3 WHERE id=1",
            params![info.name, info.description, info.default_language],
        )?;
        Ok(())
    }

    fn get_word(&self, id: i64) -> Option<WordEntry> {
        let row = self
            .conn
            .query_row(
                "SELECT id, word, reading, meaning, part_of_speech, note, language,
                    practice_count, correct_count, created_at, practiced_at
             FROM words WHERE id = ?1",
                params![id],
                |row| {
                    Ok((
                        row.get::<_, i64>(0)?,
                        row.get::<_, String>(1)?,
                        row.get::<_, Option<String>>(2)?,
                        row.get::<_, String>(3)?,
                        row.get::<_, Option<String>>(4)?,
                        row.get::<_, Option<String>>(5)?,
                        row.get::<_, String>(6)?,
                        row.get::<_, i64>(7)?,
                        row.get::<_, i64>(8)?,
                        row.get::<_, i64>(9)?,
                        row.get::<_, Option<i64>>(10)?,
                    ))
                },
            )
            .ok()?;
        self.row_to_entry(
            row.0, row.1, row.2, row.3, row.4, row.5, row.6, row.7, row.8, row.9, row.10,
        )
        .ok()
    }

    fn create_word(&self, word: &NewWord) -> Result<i64> {
        let now = now_epoch();
        let tx = self.conn.unchecked_transaction()?;

        tx.execute(
            "INSERT INTO words (word, reading, meaning, part_of_speech, note, language, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                word.word,
                word.reading,
                word.meaning,
                word.part_of_speech,
                word.note,
                word.language,
                now
            ],
        )?;
        let word_id = tx.last_insert_rowid();

        for meaning in &word.meanings {
            tx.execute(
                "INSERT OR IGNORE INTO word_meanings (word_id, meaning) VALUES (?1, ?2)",
                params![word_id, meaning],
            )?;
        }
        for (label, value) in &word.forms {
            tx.execute(
                "INSERT INTO word_forms (word_id, label, value) VALUES (?1, ?2, ?3)",
                params![word_id, label, value],
            )?;
        }
        for (sentence, translation) in &word.sentences {
            tx.execute(
                "INSERT INTO sentences (word_id, sentence, translation) VALUES (?1, ?2, ?3)",
                params![word_id, sentence, translation],
            )?;
        }

        tx.execute(
            "UPDATE db_info SET last_modified = ?1 WHERE id = 1",
            params![now],
        )?;
        tx.commit()?;
        Ok(word_id)
    }

    fn update_word(&self, id: i64, word: &UpdateWord) -> Result<()> {
        let now = now_epoch();
        let tx = self.conn.unchecked_transaction()?;

        tx.execute(
            "UPDATE words SET word=?1, reading=?2, meaning=?3, part_of_speech=?4,
                     note=?5, language=?6 WHERE id=?7",
            params![
                word.word,
                word.reading,
                word.meaning,
                word.part_of_speech,
                word.note,
                word.language,
                id
            ],
        )?;

        tx.execute("DELETE FROM word_meanings WHERE word_id = ?1", params![id])?;
        for meaning in &word.meanings {
            tx.execute(
                "INSERT OR IGNORE INTO word_meanings (word_id, meaning) VALUES (?1, ?2)",
                params![id, meaning],
            )?;
        }

        tx.execute("DELETE FROM word_forms WHERE word_id = ?1", params![id])?;
        for (label, value) in &word.forms {
            tx.execute(
                "INSERT INTO word_forms (word_id, label, value) VALUES (?1, ?2, ?3)",
                params![id, label, value],
            )?;
        }

        tx.execute("DELETE FROM sentences WHERE word_id = ?1", params![id])?;
        for (sentence, translation) in &word.sentences {
            tx.execute(
                "INSERT INTO sentences (word_id, sentence, translation) VALUES (?1, ?2, ?3)",
                params![id, sentence, translation],
            )?;
        }

        tx.execute(
            "UPDATE db_info SET last_modified = ?1 WHERE id = 1",
            params![now],
        )?;
        tx.commit()?;
        Ok(())
    }

    fn delete_word(&self, id: i64) -> Result<()> {
        self.conn
            .execute("DELETE FROM words WHERE id = ?1", params![id])?;
        self.bump_last_modified()?;
        Ok(())
    }

    fn clear_practice_stats(&self) -> Result<()> {
        self.conn.execute(
            "UPDATE words SET practice_count=0, correct_count=0, practiced_at=NULL",
            [],
        )?;
        self.bump_last_modified()?;
        Ok(())
    }

    fn update_practice_stats(&self, word_id: i64, correct: bool, practiced_at: i64) -> Result<()> {
        if correct {
            self.conn.execute(
                "UPDATE words SET practice_count=practice_count+1,
                                  correct_count=correct_count+1,
                                  practiced_at=?1 WHERE id=?2",
                params![practiced_at, word_id],
            )?;
        } else {
            self.conn.execute(
                "UPDATE words SET practice_count=practice_count+1,
                                  practiced_at=?1 WHERE id=?2",
                params![practiced_at, word_id],
            )?;
        }
        self.bump_last_modified()?;
        Ok(())
    }
}

impl DbTableSQLite {
    /// Load every word row (unfiltered). Used only by `DbTableMemory::load_from`.
    /// Filtering/sorting is handled in memory by `DbTableMemory::list_words`.
    pub fn load_all(&self) -> Vec<WordEntry> {
        let mut stmt = match self.conn.prepare(
            "SELECT id, word, reading, meaning, part_of_speech, note, language,
                    practice_count, correct_count, created_at, practiced_at
             FROM words ORDER BY id",
        ) {
            Ok(s) => s,
            Err(_) => return vec![],
        };

        let rows = match stmt.query_map([], |row| {
            Ok((
                row.get::<_, i64>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, Option<String>>(2)?,
                row.get::<_, String>(3)?,
                row.get::<_, Option<String>>(4)?,
                row.get::<_, Option<String>>(5)?,
                row.get::<_, String>(6)?,
                row.get::<_, i64>(7)?,
                row.get::<_, i64>(8)?,
                row.get::<_, i64>(9)?,
                row.get::<_, Option<i64>>(10)?,
            ))
        }) {
            Ok(r) => r,
            Err(_) => return vec![],
        };

        let mut entries = Vec::new();
        for row in rows.flatten() {
            if let Ok(entry) = self.row_to_entry(
                row.0, row.1, row.2, row.3, row.4, row.5, row.6, row.7, row.8, row.9, row.10,
            ) {
                entries.push(entry);
            }
        }
        entries
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::NamedTempFile;

    fn open_test_db() -> (DbTableSQLite, NamedTempFile) {
        let file = NamedTempFile::new().unwrap();
        let db = DbTableSQLite::open(file.path()).unwrap();
        (db, file) // keep file alive to prevent deletion
    }

    fn sample_word() -> NewWord {
        NewWord {
            word: "abandon".into(),
            reading: None,
            meaning: "放棄".into(),
            part_of_speech: Some("verb".into()),
            note: None,
            language: "en".into(),
            meanings: vec!["拋棄".into()],
            forms: vec![
                ("base_form".into(), "abandon".into()),
                ("past_tense".into(), "abandoned".into()),
            ],
            sentences: vec![("He abandoned the ship.".into(), Some("他放棄了船。".into()))],
        }
    }

    #[test]
    fn create_and_read_word() {
        let (db, _f) = open_test_db();
        let id = db.create_word(&sample_word()).unwrap();
        let entry = db.get_word(id).unwrap();
        assert_eq!(entry.word, "abandon");
        assert_eq!(entry.meaning, "放棄");
        assert_eq!(entry.meanings, vec!["拋棄"]);
        assert_eq!(entry.forms.len(), 2);
        assert_eq!(entry.sentences.len(), 1);
    }

    #[test]
    fn update_word() {
        let (db, _f) = open_test_db();
        let id = db.create_word(&sample_word()).unwrap();

        let update = UpdateWord {
            word: "forsake".into(),
            reading: None,
            meaning: "放棄".into(),
            part_of_speech: Some("verb".into()),
            note: None,
            language: "en".into(),
            meanings: vec![],
            forms: vec![("past_tense".into(), "forsook".into())],
            sentences: vec![],
        };
        db.update_word(id, &update).unwrap();

        let entry = db.get_word(id).unwrap();
        assert_eq!(entry.word, "forsake");
        assert_eq!(entry.meanings.len(), 0);
        assert_eq!(entry.forms.len(), 1);
        assert_eq!(entry.forms[0].value, "forsook");
        assert_eq!(entry.sentences.len(), 0);
    }

    #[test]
    fn delete_word_cascades() {
        let (db, _f) = open_test_db();
        let id = db.create_word(&sample_word()).unwrap();
        db.delete_word(id).unwrap();
        assert!(db.get_word(id).is_none());

        let count: i64 = db
            .conn
            .query_row(
                "SELECT COUNT(*) FROM word_meanings WHERE word_id=?1",
                params![id],
                |r| r.get(0),
            )
            .unwrap();
        assert_eq!(count, 0);
    }

    #[test]
    fn duplicate_meaning_ignored() {
        let (db, _f) = open_test_db();
        let mut w = sample_word();
        w.meanings = vec!["放棄".into(), "放棄".into()]; // duplicate + same as primary meaning
        let id = db.create_word(&w).unwrap();
        let entry = db.get_word(id).unwrap();
        // Both "放棄" strings arrive: one is filtered by UNIQUE(word_id, meaning)
        // Primary meaning is in words.meaning, not word_meanings, so at most one row for "放棄"
        let all_secondary: Vec<_> = entry
            .meanings
            .iter()
            .filter(|m| m.as_str() == "放棄")
            .collect();
        assert!(all_secondary.len() <= 1);
    }

    #[test]
    fn clear_practice_stats() {
        let (db, _f) = open_test_db();
        let id = db.create_word(&sample_word()).unwrap();
        let now = now_epoch();
        db.update_practice_stats(id, true, now).unwrap();
        db.update_practice_stats(id, false, now).unwrap();

        db.clear_practice_stats().unwrap();
        let entry = db.get_word(id).unwrap();
        assert_eq!(entry.practice_count, 0);
        assert_eq!(entry.correct_count, 0);
        assert!(entry.practiced_at.is_none());
    }

    #[test]
    fn last_modified_bumped_on_write() {
        let (db, _f) = open_test_db();
        let before = db.get_book_info().unwrap().last_modified;
        std::thread::sleep(std::time::Duration::from_secs(1));
        db.create_word(&sample_word()).unwrap();
        let after = db.get_book_info().unwrap().last_modified;
        assert!(after > before);
    }

    #[test]
    fn practice_stats_bumps_last_modified() {
        let (db, _f) = open_test_db();
        let id = db.create_word(&sample_word()).unwrap();
        let before = db.get_book_info().unwrap().last_modified;
        std::thread::sleep(std::time::Duration::from_secs(1));
        db.update_practice_stats(id, true, crate::db::schema::now_epoch())
            .unwrap();
        let after = db.get_book_info().unwrap().last_modified;
        assert!(after > before);
    }

    #[test]
    fn load_all_returns_all() {
        let (db, _f) = open_test_db();
        db.create_word(&sample_word()).unwrap();
        let mut w2 = sample_word();
        w2.word = "forfeit".into();
        db.create_word(&w2).unwrap();
        let list = db.load_all();
        assert_eq!(list.len(), 2);
    }
}
