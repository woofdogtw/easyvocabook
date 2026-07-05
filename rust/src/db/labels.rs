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
        ],
        ("ja", "i-adj") => &["te_form", "negative", "past"],
        ("ja", "na-adj") => &["te_form", "negative"],
        ("ja", "noun") => &["counter", "particle"],
        _ => &[],
    }
}

/// All canonical English `word_forms` labels.
pub const EN_FORM_LABELS: &[&str] = &[
    "singular",
    "plural",
    "base_form",
    "past_tense",
    "past_participle",
    "gerund",
    "comparative",
    "superlative",
    "phonetic",
    "collocation",
];

/// All canonical Japanese `word_forms` labels.
pub const JA_FORM_LABELS: &[&str] = &[
    "masu_form",
    "ta_form",
    "te_form",
    "nai_form",
    "dictionary_form",
    "kanji",
    "hiragana",
    "pitch_accent",
    "counter",
    "particle",
    "transitive_pair",
    "origin",
];

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
