use crate::db::types::WordEntry;

/// Available quiz modes.
#[derive(Debug, Clone, PartialEq)]
pub enum QuizMode {
    /// 中翻英 / 中翻日 — show a meaning, user types the word and conjugation fields.
    Typing,
    /// 英翻中 / 日翻中 — show the word, user selects all correct meanings.
    MultipleChoice,
}

/// A conjugation field shown in typing mode.
#[derive(Debug, Clone)]
pub struct ConjugationField {
    pub label: String,
    /// The expected correct value (from the word_forms of the target word).
    pub expected: String,
    /// The expected reading for this form, when it has one.
    pub expected_reading: Option<String>,
}

/// One fully-specified quiz question.
#[derive(Debug, Clone)]
pub struct QuizQuestion {
    pub word_id: i64,
    pub mode: QuizMode,
    /// The meaning string shown as the prompt (typing) or question (choice).
    pub prompt_meaning: String,
    /// The word string shown (for multiple-choice mode).
    pub word_display: String,
    /// Conjugation fields (typing mode only; empty for multiple-choice).
    pub conjugation_fields: Vec<ConjugationField>,
    /// All correct meanings (for multiple-choice: the full correct selection set).
    pub correct_meanings: Vec<String>,
    /// Options shown in multiple-choice (correct_meanings + distractors), shuffled.
    pub options: Vec<String>,
    /// All synonym words for reveal after answer.
    pub synonym_words: Vec<SynonymWord>,
}

#[derive(Debug, Clone)]
pub struct SynonymWord {
    pub word: String,
    pub forms: Vec<(String, String)>,
}

/// Build a quiz question for `target` from the full pool.
pub fn build_question(
    target: &WordEntry,
    pool: &[WordEntry],
    mode: QuizMode,
    rng_seed: u64,
) -> QuizQuestion {
    let prompt_meaning = pick_prompt_meaning(target, rng_seed);
    let correct_meanings = target
        .all_meanings()
        .iter()
        .map(|s| s.to_string())
        .collect::<Vec<_>>();
    let synonym_words = collect_synonyms(target, pool);

    match mode {
        QuizMode::Typing => {
            let conjugation_fields = conjugation_fields_for(target);
            QuizQuestion {
                word_id: target.id,
                mode: QuizMode::Typing,
                prompt_meaning,
                // Include the reading: a given-up card must teach how the answer is read.
                word_display: format_word_display(target),
                conjugation_fields,
                correct_meanings,
                options: vec![],
                synonym_words,
            }
        }
        QuizMode::MultipleChoice => {
            let options = build_choice_options(target, pool, &correct_meanings, rng_seed);
            QuizQuestion {
                word_id: target.id,
                mode: QuizMode::MultipleChoice,
                prompt_meaning: String::new(),
                word_display: format_word_display(target),
                conjugation_fields: vec![],
                correct_meanings,
                options,
                synonym_words,
            }
        }
    }
}

/// Randomly choose one meaning from the word's full meaning set.
fn pick_prompt_meaning(entry: &WordEntry, seed: u64) -> String {
    let all = entry.all_meanings();
    let idx = (seed as usize) % all.len();
    all[idx].to_string()
}

/// Format word + reading for display.
fn format_word_display(entry: &WordEntry) -> String {
    match &entry.reading {
        Some(r) => format!("{}（{}）", entry.word, r),
        None => entry.word.clone(),
    }
}

/// Determine conjugation fields to show based on language + POS.
fn conjugation_fields_for(entry: &WordEntry) -> Vec<ConjugationField> {
    use crate::db::labels::suggested_labels;

    let lang = entry.language.as_str();
    let pos = entry.part_of_speech.as_deref().unwrap_or("");
    let labels = suggested_labels(lang, pos);

    labels
        .iter()
        .map(|label| {
            let form = entry.forms.iter().find(|f| f.label == *label);
            ConjugationField {
                label: label.to_string(),
                expected: form.map(|f| f.value.clone()).unwrap_or_default(),
                expected_reading: form.and_then(|f| f.reading.clone()),
            }
        })
        .collect()
}

/// Build shuffled option list: all correct meanings + distractors.
fn build_choice_options(
    target: &WordEntry,
    pool: &[WordEntry],
    correct: &[String],
    seed: u64,
) -> Vec<String> {
    const MAX_DISTRACTORS: usize = 3;

    let correct_set: std::collections::HashSet<&str> = correct.iter().map(|s| s.as_str()).collect();

    // Collect distractor meanings from non-synonym words.
    let mut distractors: Vec<String> = pool
        .iter()
        .filter(|e| e.id != target.id && !e.is_synonym_of(target))
        .flat_map(|e| e.all_meanings().into_iter().map(|s| s.to_string()))
        .filter(|m| !correct_set.contains(m.as_str()))
        .collect();

    // Pseudo-shuffle using seed.
    pseudo_shuffle(&mut distractors, seed);
    distractors.truncate(MAX_DISTRACTORS);

    let mut options = correct.to_vec();
    options.extend(distractors);
    pseudo_shuffle(&mut options, seed.wrapping_add(1));
    options
}

/// Collect synonym words for reveal.
fn collect_synonyms(target: &WordEntry, pool: &[WordEntry]) -> Vec<SynonymWord> {
    pool.iter()
        .filter(|e| e.id != target.id && e.is_synonym_of(target))
        .map(|e| SynonymWord {
            word: e.word.clone(),
            forms: e
                .forms
                .iter()
                .map(|f| (f.label.clone(), f.value.clone()))
                .collect(),
        })
        .collect()
}

fn pseudo_shuffle<T>(v: &mut Vec<T>, seed: u64) {
    const A: u64 = 6364136223846793005;
    const C: u64 = 1442695040888963407;
    let mut s = seed;
    for i in (1..v.len()).rev() {
        s = s.wrapping_mul(A).wrapping_add(C);
        let j = (s >> 33) as usize % (i + 1);
        v.swap(i, j);
    }
}

/// Grade a typing answer.
/// Returns `(overall_correct, per_field_results)`.
/// The single answer-matching rule: an input matches when it equals either the value or the
/// reading, compared trimmed and case-insensitively.
///
/// Both sides are guarded on emptiness. The guard on `value` matters as much as the one on
/// `reading`: a form may carry only a reading, and an unguarded comparison would let an empty
/// answer match an empty value and mark such a field correct.
///
/// Case folding is Unicode (`café` matches `CAFÉ`); kana are deliberately not folded, so
/// katakana never satisfies a hiragana reading.
fn matches(input: &str, value: &str, reading: Option<&str>) -> bool {
    fn eq(a: &str, b: &str) -> bool {
        a.trim().to_lowercase() == b.trim().to_lowercase()
    }
    let typed = input.trim();
    let value_ok = !value.trim().is_empty() && eq(typed, value);
    let reading_ok = reading
        .map(|r| !r.trim().is_empty() && eq(typed, r))
        .unwrap_or(false);
    value_ok || reading_ok
}

/// Whether a field carries nothing to match against, in which case any input is accepted.
/// This subsumes the "matched synonym has no row for this label" case rather than replacing it.
fn is_unspecified(value: &str, reading: Option<&str>) -> bool {
    value.trim().is_empty() && reading.map(|r| r.trim().is_empty()).unwrap_or(true)
}

/// Per-field verdict returned by [`grade_typing`], carrying enough to reveal the answer with
/// its reading.
#[derive(Debug, Clone)]
pub struct FieldResult {
    pub label: String,
    pub correct: bool,
    pub expected: String,
    pub expected_reading: Option<String>,
}

pub fn grade_typing(
    question: &QuizQuestion,
    typed_word: &str,
    typed_fields: &[(String, String)], // (label, user_input)
    pool: &[WordEntry],
) -> (bool, Vec<FieldResult>) {
    // Find which synonym the user typed (or the original target): its word or its reading counts.
    let matched = pool
        .iter()
        .find(|e| e.is_related_to(question) && matches(typed_word, &e.word, e.reading.as_deref()));

    // Grade each conjugation field.
    let mut field_results = Vec::new();
    let mut all_correct = true;

    for field in &question.conjugation_fields {
        let user_input = typed_fields
            .iter()
            .find(|(l, _)| l == &field.label)
            .map(|(_, v)| v.as_str())
            .unwrap_or("");

        // Expected values come from the word the user actually typed, not the selected one.
        let (correct, expected, expected_reading) = match matched {
            Some(m) => match m.forms.iter().find(|f| f.label == field.label) {
                Some(f) => {
                    let reading = f.reading.as_deref();
                    let ok = is_unspecified(&f.value, reading)
                        || matches(user_input, &f.value, reading);
                    (ok, f.value.clone(), f.reading.clone())
                }
                None => (true, String::new(), None), // synonym has no such form — accept anything
            },
            None => (
                false,
                field.expected.clone(),
                field.expected_reading.clone(),
            ), // unknown word typed
        };

        if !correct {
            all_correct = false;
        }
        field_results.push(FieldResult {
            label: field.label.clone(),
            correct,
            expected,
            expected_reading,
        });
    }

    // Also require that the typed base word matches a known synonym (or the original).
    let base_correct = matched.is_some();
    if !base_correct {
        all_correct = false;
    }

    (all_correct, field_results)
}

/// Grade a multiple-choice answer.
pub fn grade_choice(question: &QuizQuestion, selected: &[String]) -> bool {
    let mut selected_sorted = selected.to_vec();
    selected_sorted.sort();
    let mut correct_sorted = question.correct_meanings.clone();
    correct_sorted.sort();
    selected_sorted == correct_sorted
}

trait SynonymRelated {
    fn is_related_to(&self, question: &QuizQuestion) -> bool;
}

impl SynonymRelated for WordEntry {
    fn is_related_to(&self, question: &QuizQuestion) -> bool {
        let prompt = &question.prompt_meaning;
        self.all_meanings().contains(&prompt.as_str())
    }
}

/// Select a quiz mode for the given word based on language.
/// Modes alternate pseudo-randomly.
pub fn select_mode(_entry: &WordEntry, seed: u64) -> QuizMode {
    match seed % 2 {
        0 => QuizMode::Typing,
        _ => QuizMode::MultipleChoice,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::types::{WordEntry, WordForm};

    fn entry(
        id: i64,
        word: &str,
        lang: &str,
        meanings: &[&str],
        forms: &[(&str, &str)],
    ) -> WordEntry {
        WordEntry {
            id,
            word: word.into(),
            reading: None,
            meaning: meanings[0].into(),
            part_of_speech: Some("verb".into()),
            note: None,
            language: lang.into(),
            practice_count: 0,
            correct_count: 0,
            created_at: 0,
            practiced_at: None,
            meanings: meanings[1..].iter().map(|s| s.to_string()).collect(),
            forms: forms
                .iter()
                .map(|(l, v)| WordForm {
                    id: 0,
                    label: l.to_string(),
                    value: v.to_string(),
                    reading: None,
                })
                .collect(),
            sentences: vec![],
        }
    }

    /// Build a JA verb whose forms carry readings: `(label, value, reading)`.
    fn ja_entry(word: &str, reading: &str, forms: &[(&str, &str, &str)]) -> WordEntry {
        let mut e = entry(1, word, "ja", &["吃"], &[]);
        e.reading = Some(reading.into());
        e.forms = forms
            .iter()
            .map(|(l, v, r)| WordForm {
                id: 0,
                label: l.to_string(),
                value: v.to_string(),
                reading: (!r.is_empty()).then(|| r.to_string()),
            })
            .collect();
        e
    }

    fn field_of<'a>(res: &'a [FieldResult], label: &str) -> &'a FieldResult {
        res.iter().find(|f| f.label == label).expect("field present")
    }

    #[test]
    fn synonym_excluded_from_distractors() {
        let target = entry(1, "abandon", "en", &["放棄"], &[]);
        let synonym = entry(2, "forsake", "en", &["放棄"], &[]); // same meaning = synonym
        let unrelated = entry(3, "run", "en", &["跑"], &[]);

        let pool = vec![target.clone(), synonym.clone(), unrelated.clone()];
        let q = build_question(&target, &pool, QuizMode::MultipleChoice, 0);

        // "放棄" is correct; distractors must NOT include "放棄" again
        let distractor_meanings: Vec<_> = q
            .options
            .iter()
            .filter(|o| !q.correct_meanings.contains(*o))
            .collect();
        assert!(!distractor_meanings.iter().any(|m| m.as_str() == "放棄"));
    }

    #[test]
    fn all_correct_meanings_in_options() {
        let target = entry(
            1,
            "bank",
            "en",
            &["銀行", "河岸", "堤防", "存款", "依靠"],
            &[],
        );
        let unrelated = entry(2, "run", "en", &["跑"], &[]);
        let pool = vec![target.clone(), unrelated.clone()];
        let q = build_question(&target, &pool, QuizMode::MultipleChoice, 42);

        // All 5 correct meanings must appear in options (never truncated)
        for meaning in &q.correct_meanings {
            assert!(
                q.options.contains(meaning),
                "Missing correct meaning: {meaning}"
            );
        }
    }

    #[test]
    fn grade_choice_all_correct() {
        let target = entry(1, "bank", "en", &["銀行", "河岸"], &[]);
        let q = build_question(&target, &[target.clone()], QuizMode::MultipleChoice, 0);
        assert!(grade_choice(&q, &["銀行".to_string(), "河岸".to_string()]));
        assert!(!grade_choice(&q, &["銀行".to_string()]));
    }

    #[test]
    fn typing_synonym_grading_option_a() {
        // "forsake" has past_tense="forsook"; user types "forsake" + "forsook" → correct
        let abandon = entry(
            1,
            "abandon",
            "en",
            &["放棄"],
            &[("past_tense", "abandoned"), ("base_form", "abandon")],
        );
        let forsake = entry(
            2,
            "forsake",
            "en",
            &["放棄"],
            &[("past_tense", "forsook"), ("base_form", "forsake")],
        );
        let pool = vec![abandon.clone(), forsake.clone()];

        let mut q = build_question(&abandon, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "放棄".into();

        let (ok, _fields) = grade_typing(
            &q,
            "forsake",
            &[
                ("base_form".into(), "forsake".into()),
                ("past_tense".into(), "forsook".into()),
            ],
            &pool,
        );
        assert!(ok);
    }

    #[test]
    fn ja_reading_accepted_as_correct_answer() {
        let mut rain = entry(1, "雨", "ja", &["あめ / 雨"], &[]);
        rain.reading = Some("あめ".into());
        let pool = vec![rain.clone()];
        let q = build_question(&rain, &pool, QuizMode::Typing, 0);
        // Typing the kana reading should be accepted
        let (ok, _) = grade_typing(&q, "あめ", &[], &pool);
        assert!(ok, "hiragana reading should be accepted as correct");
        // Typing the kanji should also be accepted
        let (ok2, _) = grade_typing(&q, "雨", &[], &pool);
        assert!(ok2, "kanji word should still be accepted");
    }

    #[test]
    fn form_answered_with_its_reading_is_correct() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        let (_, fields) = grade_typing(
            &q,
            "食べる",
            &[("masu_form".into(), "たべます".into())],
            &pool,
        );
        assert!(field_of(&fields, "masu_form").correct);
    }

    #[test]
    fn form_answered_with_its_value_is_correct() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        let (_, fields) = grade_typing(
            &q,
            "食べる",
            &[("masu_form".into(), "食べます".into())],
            &pool,
        );
        assert!(field_of(&fields, "masu_form").correct);
    }

    #[test]
    fn form_without_reading_matches_value_only() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        let (_, fields) = grade_typing(&q, "食べる", &[("masu_form".into(), "たべます".into())], &pool);
        assert!(!field_of(&fields, "masu_form").correct);
    }

    #[test]
    fn reading_only_form_rejects_empty_answer() {
        // A form may carry only a reading; an empty answer must not satisfy its empty value.
        let e = ja_entry("食べる", "たべる", &[("masu_form", "", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();

        let (_, empty) = grade_typing(&q, "食べる", &[("masu_form".into(), "".into())], &pool);
        assert!(!field_of(&empty, "masu_form").correct);

        let (_, typed) = grade_typing(&q, "食べる", &[("masu_form".into(), "たべます".into())], &pool);
        assert!(field_of(&typed, "masu_form").correct);
    }

    #[test]
    fn surrounding_whitespace_is_ignored() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        // ASCII and full-width (U+3000) spaces are both trimmed.
        let (ok, fields) = grade_typing(
            &q,
            "  食べる  ",
            &[("masu_form".into(), "\u{3000}たべます\u{3000}".into())],
            &pool,
        );
        assert!(ok);
        assert!(field_of(&fields, "masu_form").correct);
    }

    #[test]
    fn case_folding_covers_non_ascii() {
        let mut e = entry(1, "café", "en", &["咖啡"], &[]);
        e.part_of_speech = None;
        let pool = vec![e.clone()];
        let q = build_question(&e, &pool, QuizMode::Typing, 0);
        let (ok, _) = grade_typing(&q, "CAFÉ", &[], &pool);
        assert!(ok, "Unicode case folding should accept CAFÉ for café");
    }

    #[test]
    fn kana_scripts_are_not_folded() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        let (_, fields) = grade_typing(&q, "食べる", &[("masu_form".into(), "タベマス".into())], &pool);
        assert!(!field_of(&fields, "masu_form").correct);
    }

    #[test]
    fn reveal_exposes_form_reading_and_base_reading() {
        let e = ja_entry("食べる", "たべる", &[("masu_form", "食べます", "たべます")]);
        let pool = vec![e.clone()];
        let mut q = build_question(&e, &pool, QuizMode::Typing, 0);
        q.prompt_meaning = "吃".into();
        // Typing mode must show the base word with its reading, like multiple choice does.
        assert_eq!(q.word_display, "食べる（たべる）");
        let (_, fields) = grade_typing(&q, "食べる", &[], &pool);
        let f = field_of(&fields, "masu_form");
        assert_eq!(f.expected, "食べます");
        assert_eq!(f.expected_reading.as_deref(), Some("たべます"));
    }

    #[test]
    fn give_up_is_handled_by_counter_update_not_engine() {
        // The engine doesn't special-case give-up; the UI passes correct=false
        // to update_practice_stats. This test just verifies the weight increases.
        let mut e = entry(1, "test", "en", &["測試"], &[]);
        e.practice_count = 1;
        e.correct_count = 0;
        assert_eq!(e.quiz_weight(), 4.0); // 1.0 + 1.0 * 3.0
    }
}
