#![allow(dead_code)]

/// Metadata stored in `db_info`.
#[derive(Debug, Clone)]
pub struct BookInfo {
    pub name: String,
    pub description: Option<String>,
    pub default_language: String,
    pub version: i64,
    pub last_modified: i64,
}

/// A fully-aggregated vocabulary entry (word row + all sub-records).
#[derive(Debug, Clone)]
pub struct WordEntry {
    pub id: i64,
    pub word: String,
    pub reading: Option<String>,
    pub meaning: String,
    pub part_of_speech: Option<String>,
    pub note: Option<String>,
    pub language: String,
    pub practice_count: i64,
    pub correct_count: i64,
    pub created_at: i64,
    pub practiced_at: Option<i64>,
    /// Additional meanings (not including the primary `meaning` field).
    pub meanings: Vec<String>,
    /// Conjugation / inflection forms.
    pub forms: Vec<WordForm>,
    /// Example sentences.
    pub sentences: Vec<Sentence>,
}

impl WordEntry {
    /// Union of primary meaning and all secondary meanings.
    pub fn all_meanings(&self) -> Vec<&str> {
        let mut v = vec![self.meaning.as_str()];
        for m in &self.meanings {
            v.push(m.as_str());
        }
        v
    }

    /// Quiz weight: new words get NEW_WEIGHT; practiced words get base + incorrect_rate × multiplier.
    pub fn quiz_weight(&self) -> f64 {
        const NEW_WEIGHT: f64 = 3.0;
        const BASE: f64 = 1.0;
        const MULTIPLIER: f64 = 3.0;
        if self.practice_count == 0 {
            NEW_WEIGHT
        } else {
            let incorrect_rate =
                (self.practice_count - self.correct_count) as f64 / self.practice_count as f64;
            BASE + incorrect_rate * MULTIPLIER
        }
    }

    /// True if the meaning set of this entry intersects with `other`'s meaning set (synonym check).
    pub fn is_synonym_of(&self, other: &WordEntry) -> bool {
        for m in self.all_meanings() {
            if other.all_meanings().contains(&m) {
                return true;
            }
        }
        false
    }
}

#[derive(Debug, Clone)]
pub struct WordForm {
    pub id: i64,
    pub label: String,
    pub value: String,
}

#[derive(Debug, Clone)]
pub struct Sentence {
    pub id: i64,
    pub sentence: String,
    pub translation: Option<String>,
}

/// Filter applied to `list_words`.
#[derive(Debug, Clone, Default)]
pub struct WordFilter {
    /// If `Some`, only words with this language code are returned.
    pub language: Option<String>,
    /// If `Some`, words matching this text in word/reading/any meaning are returned.
    pub text: Option<String>,
    pub sort: SortField,
    pub sort_desc: bool,
}

#[derive(Debug, Clone, Copy, Default, PartialEq)]
pub enum SortField {
    #[default]
    Word,
    Reading,
    Meaning,
    CorrectRate,
}

/// Payload for creating a new word.
#[derive(Debug, Clone)]
pub struct NewWord {
    pub word: String,
    pub reading: Option<String>,
    pub meaning: String,
    pub part_of_speech: Option<String>,
    pub note: Option<String>,
    pub language: String,
    pub meanings: Vec<String>,
    pub forms: Vec<(String, String)>,
    pub sentences: Vec<(String, Option<String>)>,
}

/// Payload for updating an existing word.
#[derive(Debug, Clone)]
pub struct UpdateWord {
    pub word: String,
    pub reading: Option<String>,
    pub meaning: String,
    pub part_of_speech: Option<String>,
    pub note: Option<String>,
    pub language: String,
    pub meanings: Vec<String>,
    pub forms: Vec<(String, String)>,
    pub sentences: Vec<(String, Option<String>)>,
}
