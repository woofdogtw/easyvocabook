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
                word_display: target.word.clone(),
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
        .filter_map(|label| {
            let expected = entry
                .forms
                .iter()
                .find(|f| f.label == *label)
                .map(|f| f.value.clone())
                .unwrap_or_default();
            Some(ConjugationField {
                label: label.to_string(),
                expected,
            })
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
pub fn grade_typing(
    question: &QuizQuestion,
    typed_word: &str,
    typed_fields: &[(String, String)], // (label, user_input)
    pool: &[WordEntry],
) -> (bool, Vec<(String, bool, String)>) {
    // Find which synonym the user typed (or the original target).
    // For words with a reading (e.g. Japanese kana), either the word or the reading is accepted.
    let matched = pool.iter().find(|e| {
        e.is_related_to(question)
            && (e.word.eq_ignore_ascii_case(typed_word)
                || e.reading
                    .as_deref()
                    .map(|r| r == typed_word)
                    .unwrap_or(false))
    });

    // Grade each conjugation field.
    let mut field_results = Vec::new();
    let mut all_correct = true;

    for field in &question.conjugation_fields {
        let user_input = typed_fields
            .iter()
            .find(|(l, _)| l == &field.label)
            .map(|(_, v)| v.as_str())
            .unwrap_or("");

        // Expected: from the matched synonym's word_forms (if synonym has no form for this
        // label, accept any input per spec Option A).
        let (correct, expected) = match matched {
            Some(m) => {
                let form = m.forms.iter().find(|f| f.label == field.label);
                match form {
                    Some(f) => {
                        let ok = f.value.eq_ignore_ascii_case(user_input);
                        (ok, f.value.clone())
                    }
                    None => (true, String::new()), // synonym has no form — accept anything
                }
            }
            None => (false, field.expected.clone()), // unknown word typed
        };

        if !correct {
            all_correct = false;
        }
        field_results.push((field.label.clone(), correct, expected));
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
                })
                .collect(),
            sentences: vec![],
        }
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
    fn give_up_is_handled_by_counter_update_not_engine() {
        // The engine doesn't special-case give-up; the UI passes correct=false
        // to update_practice_stats. This test just verifies the weight increases.
        let mut e = entry(1, "test", "en", &["測試"], &[]);
        e.practice_count = 1;
        e.correct_count = 0;
        assert_eq!(e.quiz_weight(), 4.0); // 1.0 + 1.0 * 3.0
    }
}
