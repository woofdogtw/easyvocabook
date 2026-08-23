#![allow(dead_code)]

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

/// Display name for a `part_of_speech` key in a given locale.
/// Falls back to the raw key if no translation is known.
pub fn pos_display(language: &str, pos: &str, locale: &str) -> String {
    match (language, pos, locale) {
        ("ja", "i-adj", "zh-TW" | "zh-CN") => "い形容詞".into(),
        ("ja", "na-adj", "zh-TW" | "zh-CN") => "な形容詞".into(),
        ("ja", "noun", "zh-TW" | "zh-CN") => "名詞".into(),
        ("ja", "verb", "zh-TW" | "zh-CN") => "動詞".into(),
        ("ja", "adverb", "zh-TW" | "zh-CN") => "副詞".into(),
        ("ja", "particle", "zh-TW" | "zh-CN") => "助詞".into(),
        ("ja", "aux-verb", "zh-TW" | "zh-CN") => "助動詞".into(),
        ("ja", "conjunction", "zh-TW" | "zh-CN") => "接続詞".into(),
        _ => pos.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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
