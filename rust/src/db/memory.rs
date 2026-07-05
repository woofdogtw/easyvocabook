use rusqlite::Result;

use crate::db::sqlite::{DbTableBase, DbTableSQLite};
use crate::db::types::*;

#[derive(Debug, Clone)]
pub struct DbTableMemory {
    entries: Vec<WordEntry>,
    book_info: Option<BookInfo>,
}

impl DbTableMemory {
    pub fn new() -> Self {
        Self {
            entries: Vec::new(),
            book_info: None,
        }
    }

    /// Load all words with full aggregates from a SQLite source.
    pub fn load_from(sqlite: &DbTableSQLite) -> Self {
        let entries = sqlite.load_all();
        let book_info = sqlite.get_book_info().ok();
        Self { entries, book_info }
    }

    /// All entries (unfiltered) — used by the quiz engine.
    pub fn all_entries(&self) -> &[WordEntry] {
        &self.entries
    }
}

impl DbTableBase for DbTableMemory {
    fn get_book_info(&self) -> Result<BookInfo> {
        self.book_info
            .clone()
            .ok_or(rusqlite::Error::QueryReturnedNoRows)
    }

    fn update_book_info(&self, _info: &BookInfo) -> Result<()> {
        Ok(())
    }

    fn get_word(&self, id: i64) -> Option<WordEntry> {
        self.entries.iter().find(|e| e.id == id).cloned()
    }

    fn create_word(&self, _word: &NewWord) -> Result<i64> {
        Err(rusqlite::Error::InvalidQuery)
    }

    fn update_word(&self, _id: i64, _word: &UpdateWord) -> Result<()> {
        Err(rusqlite::Error::InvalidQuery)
    }

    fn delete_word(&self, _id: i64) -> Result<()> {
        Err(rusqlite::Error::InvalidQuery)
    }

    fn clear_practice_stats(&self) -> Result<()> {
        Err(rusqlite::Error::InvalidQuery)
    }

    fn update_practice_stats(
        &self,
        _word_id: i64,
        _correct: bool,
        _practiced_at: i64,
    ) -> Result<()> {
        Err(rusqlite::Error::InvalidQuery)
    }
}

impl DbTableMemory {
    /// Filtered, sorted word list — the primary read path used by the UI.
    pub fn list_words(&self, filter: &WordFilter) -> Vec<WordEntry> {
        let mut results: Vec<WordEntry> = self
            .entries
            .iter()
            .filter(|e| {
                if let Some(lang) = &filter.language {
                    if &e.language != lang {
                        return false;
                    }
                }
                if let Some(text) = &filter.text {
                    let t = text.to_lowercase();
                    let matches = e.word.to_lowercase().contains(&t)
                        || e.reading
                            .as_deref()
                            .unwrap_or("")
                            .to_lowercase()
                            .contains(&t)
                        || e.meaning.to_lowercase().contains(&t)
                        || e.meanings.iter().any(|m| m.to_lowercase().contains(&t));
                    if !matches {
                        return false;
                    }
                }
                true
            })
            .cloned()
            .collect();

        results.sort_by(|a, b| {
            let cmp = match filter.sort {
                SortField::Word => a.word.cmp(&b.word),
                SortField::Reading => {
                    let ra = a.reading.as_deref().unwrap_or("");
                    let rb = b.reading.as_deref().unwrap_or("");
                    ra.cmp(rb)
                }
                SortField::Meaning => a.meaning.cmp(&b.meaning),
                SortField::CorrectRate => {
                    let rate_a = correct_rate(a);
                    let rate_b = correct_rate(b);
                    rate_a
                        .partial_cmp(&rate_b)
                        .unwrap_or(std::cmp::Ordering::Equal)
                }
            };
            if filter.sort_desc { cmp.reverse() } else { cmp }
        });

        results
    }
}

/// Write-through helpers — called after SQLite write succeeds.
impl DbTableMemory {
    pub fn insert_entry(&mut self, entry: WordEntry) {
        self.entries.push(entry);
    }

    pub fn replace_entry(&mut self, entry: WordEntry) {
        if let Some(pos) = self.entries.iter().position(|e| e.id == entry.id) {
            self.entries[pos] = entry;
        }
    }

    pub fn remove_entry(&mut self, id: i64) {
        self.entries.retain(|e| e.id != id);
    }

    pub fn apply_clear_stats(&mut self) {
        for e in &mut self.entries {
            e.practice_count = 0;
            e.correct_count = 0;
            e.practiced_at = None;
        }
    }

    pub fn apply_practice_update(&mut self, word_id: i64, correct: bool, practiced_at: i64) {
        if let Some(entry) = self.entries.iter_mut().find(|e| e.id == word_id) {
            entry.practice_count += 1;
            if correct {
                entry.correct_count += 1;
            }
            entry.practiced_at = Some(practiced_at);
        }
    }
}

fn correct_rate(e: &WordEntry) -> f64 {
    if e.practice_count == 0 {
        -1.0 // unpracticed sorts first
    } else {
        e.correct_count as f64 / e.practice_count as f64
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::sqlite::DbTableSQLite;
    use tempfile::NamedTempFile;

    fn make_entry(id: i64, word: &str, lang: &str, meanings: &[&str]) -> WordEntry {
        WordEntry {
            id,
            word: word.into(),
            reading: None,
            meaning: meanings[0].into(),
            part_of_speech: None,
            note: None,
            language: lang.into(),
            practice_count: 0,
            correct_count: 0,
            created_at: 0,
            practiced_at: None,
            meanings: meanings[1..].iter().map(|s| s.to_string()).collect(),
            forms: vec![],
            sentences: vec![],
        }
    }

    fn populated_memory() -> DbTableMemory {
        let mut m = DbTableMemory::new();
        m.insert_entry(make_entry(1, "bank", "en", &["銀行", "河岸"]));
        m.insert_entry(make_entry(2, "abandon", "en", &["放棄", "拋棄"]));
        m.insert_entry(make_entry(3, "雨", "ja", &["あめ / 雨"]));
        m
    }

    #[test]
    fn filter_by_language() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            language: Some("ja".into()),
            ..Default::default()
        });
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].word, "雨");
    }

    #[test]
    fn text_search_matches_secondary_meaning() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            text: Some("河岸".into()),
            ..Default::default()
        });
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].word, "bank");
    }

    #[test]
    fn text_search_no_match() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            text: Some("xyz999".into()),
            ..Default::default()
        });
        assert!(results.is_empty());
    }

    #[test]
    fn sort_by_word_ascending() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            sort: SortField::Word,
            ..Default::default()
        });
        let words: Vec<_> = results.iter().map(|e| e.word.as_str()).collect();
        assert_eq!(words[0], "abandon");
        assert_eq!(words[1], "bank");
    }

    #[test]
    fn sort_by_correct_rate_unpracticed_first() {
        let mut m = DbTableMemory::new();
        m.insert_entry(make_entry(1, "a", "en", &["a"]));
        m.apply_practice_update(1, true, 1);
        m.apply_practice_update(1, true, 1);
        m.insert_entry(make_entry(2, "b", "en", &["b"])); // unpracticed
        let results = m.list_words(&WordFilter {
            sort: SortField::CorrectRate,
            ..Default::default()
        });
        assert_eq!(results[0].word, "b"); // unpracticed first
    }

    #[test]
    fn empty_state() {
        let m = DbTableMemory::new();
        let results = m.list_words(&WordFilter::default());
        assert!(results.is_empty());
    }

    #[test]
    fn load_from_sqlite() {
        let file = NamedTempFile::new().unwrap();
        let sqlite = DbTableSQLite::open(file.path()).unwrap();
        let _keep = &file; // keep alive
        let word = crate::db::types::NewWord {
            word: "test".into(),
            reading: None,
            meaning: "測試".into(),
            part_of_speech: None,
            note: None,
            language: "en".into(),
            meanings: vec![],
            forms: vec![],
            sentences: vec![],
        };
        sqlite.create_word(&word).unwrap();
        let mem = DbTableMemory::load_from(&sqlite);
        assert_eq!(mem.entries.len(), 1);
        assert_eq!(mem.entries[0].word, "test");
    }

    #[test]
    fn all_entries_returns_all() {
        let m = populated_memory();
        assert_eq!(m.all_entries().len(), 3);
    }

    #[test]
    fn get_book_info_none_returns_err() {
        let m = DbTableMemory::new();
        assert!(m.get_book_info().is_err());
    }

    #[test]
    fn get_book_info_some_returns_ok() {
        let file = NamedTempFile::new().unwrap();
        let sqlite = DbTableSQLite::open(file.path()).unwrap();
        let _keep = &file;
        let m = DbTableMemory::load_from(&sqlite);
        assert!(m.get_book_info().is_ok());
    }

    #[test]
    fn update_book_info_is_noop() {
        let m = DbTableMemory::new();
        let info = crate::db::types::BookInfo {
            name: "x".into(),
            description: None,
            default_language: "en".into(),
            version: 1,
            last_modified: 0,
        };
        assert!(m.update_book_info(&info).is_ok());
    }

    #[test]
    fn get_word_found_and_not_found() {
        let m = populated_memory();
        assert!(m.get_word(1).is_some());
        assert!(m.get_word(999).is_none());
    }

    #[test]
    fn write_methods_return_err() {
        let m = DbTableMemory::new();
        let nw = crate::db::types::NewWord {
            word: "x".into(),
            reading: None,
            meaning: "y".into(),
            part_of_speech: None,
            note: None,
            language: "en".into(),
            meanings: vec![],
            forms: vec![],
            sentences: vec![],
        };
        let uw = crate::db::types::UpdateWord {
            word: "x".into(),
            reading: None,
            meaning: "y".into(),
            part_of_speech: None,
            note: None,
            language: "en".into(),
            meanings: vec![],
            forms: vec![],
            sentences: vec![],
        };
        assert!(m.create_word(&nw).is_err());
        assert!(m.update_word(1, &uw).is_err());
        assert!(m.delete_word(1).is_err());
        assert!(m.clear_practice_stats().is_err());
        assert!(m.update_practice_stats(1, true, 0).is_err());
    }

    #[test]
    fn sort_by_reading() {
        let mut m = DbTableMemory::new();
        let mut e1 = make_entry(1, "bank", "en", &["銀行"]);
        e1.reading = Some("bank".into());
        let mut e2 = make_entry(2, "abandon", "en", &["放棄"]);
        e2.reading = Some("abandon".into());
        m.insert_entry(e1);
        m.insert_entry(e2);
        let results = m.list_words(&WordFilter {
            sort: SortField::Reading,
            ..Default::default()
        });
        assert_eq!(results[0].word, "abandon");
    }

    #[test]
    fn sort_by_meaning() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            sort: SortField::Meaning,
            ..Default::default()
        });
        // meanings: "あめ / 雨", "放棄", "銀行" — sorted ascending
        assert_eq!(results[0].meaning, "あめ / 雨");
    }

    #[test]
    fn sort_descending() {
        let m = populated_memory();
        let results = m.list_words(&WordFilter {
            sort: SortField::Word,
            sort_desc: true,
            ..Default::default()
        });
        assert_eq!(results[0].word, "雨");
    }

    #[test]
    fn replace_entry() {
        let mut m = populated_memory();
        let mut updated = make_entry(1, "bank_updated", "en", &["銀行"]);
        updated.id = 1;
        m.replace_entry(updated);
        assert_eq!(m.get_word(1).unwrap().word, "bank_updated");
        // replace non-existent id is a no-op
        m.replace_entry(make_entry(999, "ghost", "en", &["?"]));
        assert_eq!(m.all_entries().len(), 3);
    }

    #[test]
    fn remove_entry() {
        let mut m = populated_memory();
        m.remove_entry(2);
        assert_eq!(m.all_entries().len(), 2);
        assert!(m.get_word(2).is_none());
    }

    #[test]
    fn apply_clear_stats_resets_counters() {
        let mut m = DbTableMemory::new();
        m.insert_entry(make_entry(1, "a", "en", &["a"]));
        m.apply_practice_update(1, true, 100);
        m.apply_clear_stats();
        let e = m.get_word(1).unwrap();
        assert_eq!(e.practice_count, 0);
        assert_eq!(e.correct_count, 0);
        assert_eq!(e.practiced_at, None);
    }

    #[test]
    fn apply_practice_update_incorrect() {
        let mut m = DbTableMemory::new();
        m.insert_entry(make_entry(1, "a", "en", &["a"]));
        m.apply_practice_update(1, false, 50);
        let e = m.get_word(1).unwrap();
        assert_eq!(e.practice_count, 1);
        assert_eq!(e.correct_count, 0);
    }

    #[test]
    fn apply_practice_update_missing_id_is_noop() {
        let mut m = DbTableMemory::new();
        m.apply_practice_update(999, true, 0); // should not panic
    }
}
