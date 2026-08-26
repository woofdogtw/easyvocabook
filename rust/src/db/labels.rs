use super::types::WordForm;

/// Canonical `part_of_speech` keys for English words.
pub const EN_POS: &[&str] = &[
    "noun",
    "verb",
    "adjective",
    "adverb",
    "pronoun",
    "preposition",
    "conjunction",
    "interjection",
    "other",
];

/// Canonical `part_of_speech` keys for Japanese words.
pub const JA_POS: &[&str] = &[
    "noun",
    "verb",
    "i-adj",
    "na-adj",
    "adverb",
    "particle",
    "aux-verb",
    "conjunction",
    "other",
];

/// Suggested `word_forms` labels per (language, part_of_speech) combination.
/// Returns an empty slice if no suggestions are defined for the combination.
pub fn suggested_labels(language: &str, pos: &str) -> &'static [&'static str] {
    match (language, pos) {
        ("en", "verb") => &["base_form", "past_tense", "past_participle", "gerund"],
        ("en", "noun") => &["singular", "plural"],
        ("en", "adjective") => &["comparative", "superlative"],
        ("ja", "verb") => &[
            "dictionary_form",
            "masu_form",
            "ta_form",
            "te_form",
            "nai_form",
            "transitive_pair",
        ],
        ("ja", "i-adj") => &["te_form", "negative", "past"],
        ("ja", "na-adj") => &["te_form", "negative"],
        // Japanese nouns suggest nothing: they have no plural, a counter is not unique for most
        // nouns, and a particle depends on sentence role rather than the noun itself.
        _ => &[],
    }
}

/// All canonical English `word_forms` labels.
/// `phonetic` is intentionally absent: its only purpose was to carry a pronunciation, which every
/// word form now records in its own `reading`. Its locale mapping is kept so existing rows still
/// display a translated name.
pub const EN_FORM_LABELS: &[&str] = &[
    "singular",
    "plural",
    "base_form",
    "past_tense",
    "past_participle",
    "gerund",
    "comparative",
    "superlative",
    "collocation",
];

/// All canonical Japanese `word_forms` labels.
/// `hiragana` is retired for the same reason as `phonetic`. `kanji` (a written form) and
/// `pitch_accent` (an accent pattern) are not readings and stay. `negative` and `past` are listed
/// because the i-adjective suggestions propose them — every suggestable label must be selectable.
pub const JA_FORM_LABELS: &[&str] = &[
    "masu_form",
    "ta_form",
    "te_form",
    "nai_form",
    "dictionary_form",
    "negative",
    "past",
    "kanji",
    "pitch_accent",
    "counter",
    "particle",
    "transitive_pair",
    "origin",
];

/// Transitivity keys for Japanese verbs. Language-neutral, like `part_of_speech`.
pub const TRANSITIVITY_KEYS: &[&str] = &["intransitive", "transitive", "ambitransitive"];

/// Verb group keys for Japanese verbs.
pub const VERB_GROUP_KEYS: &[&str] = &["godan", "ichidan", "irregular"];

/// Maps a transitivity key to its locale string key.
pub fn transitivity_locale_key(key: &str) -> &'static str {
    match key {
        "intransitive" => "transitivity.intransitive",
        "transitive" => "transitivity.transitive",
        "ambitransitive" => "transitivity.ambitransitive",
        _ => "",
    }
}

/// The word list's Class: the classification that decides what the Comparison column holds.
///
/// A Japanese verb resolves to its transitivity, because that is what its companion is derived
/// from — the opposite verb. Everything else resolves to its part of speech. Returns `None` when
/// the word records neither, which the list renders as an empty cell.
///
/// The returned pair is `(namespace, key)`: `("transitivity", "intransitive")` or
/// `("pos", "noun")`. Callers use it for the badge string and, in `DbTableMemory`, as a sort key.
pub fn class_of(
    language: &str,
    part_of_speech: Option<&str>,
    transitivity: Option<&str>,
) -> Option<(&'static str, String)> {
    if language == "ja" && part_of_speech == Some("verb") {
        if let Some(t) = transitivity.filter(|t| !t.is_empty()) {
            return Some(("transitivity", t.to_owned()));
        }
    }
    part_of_speech
        .filter(|p| !p.is_empty())
        .map(|p| ("pos", p.to_owned()))
}

/// The `word_forms` label whose value the Comparison column shows, given a word's Class.
///
/// The companion is the form a learner has to memorise alongside the word: for a Japanese verb the
/// verb of opposite transitivity, for an adjective its negative, for an English verb or noun the
/// past tense or plural. Whatever is recorded is shown, irregular or not — detecting irregularity
/// would need rules and data this does not have.
pub fn comparison_label(language: &str, part_of_speech: Option<&str>) -> Option<&'static str> {
    match (language, part_of_speech?) {
        ("ja", "verb") => Some("transitive_pair"),
        ("ja", "i-adj") | ("ja", "na-adj") => Some("negative"),
        ("en", "verb") => Some("past_tense"),
        ("en", "noun") => Some("plural"),
        ("en", "adjective") => Some("comparative"),
        _ => None,
    }
}

/// The Comparison cell's text for a word, or `None` when it has no companion recorded.
/// The list renders `None` as `—`.
pub fn comparison_value<'a>(
    language: &str,
    part_of_speech: Option<&str>,
    forms: &'a [WordForm],
) -> Option<&'a str> {
    let label = comparison_label(language, part_of_speech)?;
    forms
        .iter()
        .find(|f| f.label == label)
        .map(|f| f.value.as_str())
        .filter(|v| !v.is_empty())
}

/// Locale key for a transitivity abbreviation, used by the word list's Class badge.
pub fn transitivity_abbr_key(key: &str) -> &'static str {
    match key {
        "intransitive" => "transitivity.abbr.intransitive",
        "transitive" => "transitivity.abbr.transitive",
        "ambitransitive" => "transitivity.abbr.ambitransitive",
        _ => "",
    }
}

/// Locale key for a part-of-speech abbreviation, keyed by the **word's** language rather than the
/// interface locale: a Japanese word gets a CJK badge and an English word a Latin one, whichever
/// language the UI is in. Only the CJK variant tracks the interface locale, and the locale table
/// handles that.
pub fn class_abbr_key(word_language: &str, pos: &str) -> &'static str {
    match (word_language, pos) {
        ("ja", "noun") => "pos.abbr.ja.noun",
        ("ja", "verb") => "pos.abbr.ja.verb",
        ("ja", "i-adj") => "pos.abbr.ja.i-adj",
        ("ja", "na-adj") => "pos.abbr.ja.na-adj",
        ("ja", "adverb") => "pos.abbr.ja.adverb",
        ("ja", "particle") => "pos.abbr.ja.particle",
        ("ja", "aux-verb") => "pos.abbr.ja.aux-verb",
        ("ja", "conjunction") => "pos.abbr.ja.conjunction",
        ("ja", "other") => "pos.abbr.ja.other",
        // A Japanese word with a part of speech outside JA_POS gets no badge rather than a Latin
        // one. Unreachable through the UI, but falling through to the English table would put
        // `Adj` on a Japanese word.
        ("ja", _) => "",
        (_, "noun") => "pos.abbr.en.noun",
        (_, "verb") => "pos.abbr.en.verb",
        (_, "adjective") => "pos.abbr.en.adjective",
        (_, "adverb") => "pos.abbr.en.adverb",
        (_, "pronoun") => "pos.abbr.en.pronoun",
        (_, "preposition") => "pos.abbr.en.preposition",
        (_, "conjunction") => "pos.abbr.en.conjunction",
        (_, "interjection") => "pos.abbr.en.interjection",
        (_, "other") => "pos.abbr.en.other",
        _ => "",
    }
}

/// Maps a verb group key to its locale string key.
pub fn verb_group_locale_key(key: &str) -> &'static str {
    match key {
        "godan" => "verb_group.godan",
        "ichidan" => "verb_group.ichidan",
        "irregular" => "verb_group.irregular",
        _ => "",
    }
}

/// Maps a canonical `part_of_speech` key to the corresponding locale string key.
pub fn pos_locale_key(pos: &str) -> &'static str {
    match pos {
        "noun" => "pos.noun",
        "verb" => "pos.verb",
        "adjective" => "pos.adjective",
        "adverb" => "pos.adverb",
        "pronoun" => "pos.pronoun",
        "preposition" => "pos.preposition",
        "conjunction" => "pos.conjunction",
        "interjection" => "pos.interjection",
        "i-adj" => "pos.i-adj",
        "na-adj" => "pos.na-adj",
        "particle" => "pos.particle",
        "aux-verb" => "pos.aux-verb",
        _ => "pos.other",
    }
}

/// Maps a canonical `word_forms` label to the corresponding locale string key.
pub fn form_locale_key(label: &str) -> &'static str {
    match label {
        "dictionary_form" => "form.dictionary_form",
        "masu_form" => "form.masu_form",
        "ta_form" => "form.ta_form",
        "te_form" => "form.te_form",
        "negative" => "form.negative",
        "past" => "form.past",
        "nai_form" => "form.nai_form",
        "singular" => "form.singular",
        "plural" => "form.plural",
        "base_form" => "form.base_form",
        "past_tense" => "form.past_tense",
        "past_participle" => "form.past_participle",
        "gerund" => "form.gerund",
        "comparative" => "form.comparative",
        "superlative" => "form.superlative",
        "phonetic" => "form.phonetic",
        "collocation" => "form.collocation",
        "kanji" => "form.kanji",
        "hiragana" => "form.hiragana",
        "pitch_accent" => "form.pitch_accent",
        "counter" => "form.counter",
        "particle" => "form.particle",
        "transitive_pair" => "form.transitive_pair",
        "origin" => "form.origin",
        _ => "", // empty = unknown; caller falls back to the raw label
    }
}

/// Maps a language code to the corresponding locale string key.
pub fn lang_locale_key(code: &str) -> &'static str {
    match code {
        "en" => "lang.en",
        "ja" => "lang.ja",
        _ => "lang.en",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn form(label: &str, value: &str) -> WordForm {
        WordForm { id: 0, label: label.into(), value: value.into(), reading: None }
    }

    #[test]
    fn paired_verb_resolves_to_transitivity_and_its_partner() {
        assert_eq!(
            class_of("ja", Some("verb"), Some("intransitive")),
            Some(("transitivity", "intransitive".to_owned()))
        );
        let forms = [form("transitive_pair", "上げる"), form("masu_form", "上がります")];
        assert_eq!(comparison_value("ja", Some("verb"), &forms), Some("上げる"));
    }

    #[test]
    fn partnerless_verb_keeps_its_class_but_has_no_comparison() {
        assert_eq!(
            class_of("ja", Some("verb"), Some("transitive")),
            Some(("transitivity", "transitive".to_owned()))
        );
        let forms = [form("masu_form", "食べます")];
        assert_eq!(comparison_value("ja", Some("verb"), &forms), None);
    }

    /// Three of the four ambitransitive verbs in the vocabulary do record a partner, so the class
    /// must not suppress it.
    #[test]
    fn ambitransitive_verb_shows_a_partner_when_one_is_recorded() {
        assert_eq!(
            class_of("ja", Some("verb"), Some("ambitransitive")),
            Some(("transitivity", "ambitransitive".to_owned()))
        );
        let forms = [form("transitive_pair", "開ける")];
        assert_eq!(comparison_value("ja", Some("verb"), &forms), Some("開ける"));
    }

    #[test]
    fn noun_falls_back_to_part_of_speech_and_has_no_comparison() {
        assert_eq!(class_of("ja", Some("noun"), None), Some(("pos", "noun".to_owned())));
        assert_eq!(comparison_value("ja", Some("noun"), &[]), None);
    }

    /// A verb whose transitivity was never filled in falls back rather than showing nothing. No
    /// seeded word takes this path — all 289 Japanese verbs record one — so the rule exists for
    /// words the user adds by hand, and this test is its only coverage.
    #[test]
    fn verb_without_transitivity_falls_back_to_part_of_speech() {
        assert_eq!(class_of("ja", Some("verb"), None), Some(("pos", "verb".to_owned())));
        assert_eq!(class_of("ja", Some("verb"), Some("")), Some(("pos", "verb".to_owned())));
    }

    #[test]
    fn word_with_no_part_of_speech_has_no_class() {
        assert_eq!(class_of("ja", None, None), None);
        assert_eq!(class_of("ja", Some(""), None), None);
    }

    #[test]
    fn english_forms_follow_their_part_of_speech() {
        assert_eq!(comparison_label("en", Some("verb")), Some("past_tense"));
        assert_eq!(comparison_label("en", Some("noun")), Some("plural"));
        assert_eq!(comparison_label("en", Some("adjective")), Some("comparative"));
        // Regular forms are shown too; irregularity is not detected.
        let forms = [form("past_tense", "walked")];
        assert_eq!(comparison_value("en", Some("verb"), &forms), Some("walked"));
    }

    /// Transitivity belongs to Japanese verbs; an English verb never takes that branch.
    #[test]
    fn english_verb_never_resolves_to_transitivity() {
        assert_eq!(
            class_of("en", Some("verb"), Some("transitive")),
            Some(("pos", "verb".to_owned()))
        );
    }

    #[test]
    fn language_without_a_rule_has_no_comparison() {
        assert_eq!(comparison_label("de", Some("noun")), None);
        assert_eq!(comparison_value("de", Some("noun"), &[form("plural", "x")]), None);
    }

    /// The canonical `part_of_speech` lists, exactly as
    /// `openspec/specs/word-edit-ui/spec.md` fixes them.
    ///
    /// `every_suggested_label_is_canonical` below iterates these constants but only checks the
    /// labels they suggest — a display string here would make `suggested_labels` return empty and
    /// that test would still pass. Rust held the right values by having been written correctly,
    /// not because anything verified them. The Kotlin copy, unverified in the same way, drifted
    /// to localized display strings and silently dropped verb attributes for six weeks.
    #[test]
    fn pos_lists_match_the_specification() {
        assert_eq!(
            EN_POS,
            [
                "noun",
                "verb",
                "adjective",
                "adverb",
                "pronoun",
                "preposition",
                "conjunction",
                "interjection",
                "other"
            ]
        );
        assert_eq!(
            JA_POS,
            [
                "noun",
                "verb",
                "i-adj",
                "na-adj",
                "adverb",
                "particle",
                "aux-verb",
                "conjunction",
                "other"
            ]
        );
    }

    /// A key is ASCII by definition; a localized display string never is. This is the check that
    /// would have caught the Android divergence the day it was introduced.
    #[test]
    fn every_pos_is_an_ascii_key() {
        for (lang, list) in [("en", EN_POS), ("ja", JA_POS)] {
            for pos in list {
                assert!(
                    pos.is_ascii(),
                    "{lang} part of speech {pos:?} must be a language-neutral ASCII key"
                );
            }
        }
    }

    /// Every label the edit dialog can suggest must be selectable in the label dropdown,
    /// otherwise a suggested row cannot be re-picked after the user changes it.
    #[test]
    fn every_suggested_label_is_canonical() {
        for (lang, pos_list, canonical) in [
            ("en", EN_POS, EN_FORM_LABELS),
            ("ja", JA_POS, JA_FORM_LABELS),
        ] {
            for pos in pos_list {
                for label in suggested_labels(lang, pos) {
                    assert!(
                        canonical.contains(label),
                        "{lang}/{pos} suggests {label}, which is not in the canonical list"
                    );
                }
            }
        }
    }

    #[test]
    fn retired_labels_are_not_canonical_but_still_translate() {
        for retired in ["phonetic", "hiragana"] {
            assert!(!EN_FORM_LABELS.contains(&retired) || !JA_FORM_LABELS.contains(&retired));
            assert!(!form_locale_key(retired).is_empty(), "{retired} lost its locale key");
        }
        assert!(!EN_FORM_LABELS.contains(&"phonetic"));
        assert!(!JA_FORM_LABELS.contains(&"hiragana"));
        // Not readings — these stay.
        assert!(JA_FORM_LABELS.contains(&"kanji"));
        assert!(JA_FORM_LABELS.contains(&"pitch_accent"));
    }

    /// Every canonical label must have a translatable name, or the UI falls back to showing the
    /// raw English key (as `negative` and `past` once did in the quiz and the label dropdown).
    #[test]
    fn every_canonical_label_has_a_locale_key() {
        for label in EN_FORM_LABELS.iter().chain(JA_FORM_LABELS.iter()) {
            assert!(
                !form_locale_key(label).is_empty(),
                "canonical label {label} has no locale key"
            );
        }
    }

    #[test]
    fn ja_noun_suggests_nothing() {
        assert!(suggested_labels("ja", "noun").is_empty());
        assert!(suggested_labels("ja", "particle").is_empty());
    }

    #[test]
    fn ja_verb_suggests_the_paired_verb() {
        assert!(suggested_labels("ja", "verb").contains(&"transitive_pair"));
    }

    #[test]
    fn verb_attribute_keys_have_locale_keys() {
        for k in TRANSITIVITY_KEYS {
            assert!(!transitivity_locale_key(k).is_empty(), "{k} has no locale key");
        }
        for k in VERB_GROUP_KEYS {
            assert!(!verb_group_locale_key(k).is_empty(), "{k} has no locale key");
        }
    }
}
