package tw.idv.woofdog.easyvocabook.ui

import java.util.Locale
import tw.idv.woofdog.easyvocabook.R
import tw.idv.woofdog.easyvocabook.data.model.WordEntry

object Labels {

    // ── Form label resource IDs ───────────────────────────────────────────────

    fun formLabelResId(key: String): Int? = when (key) {
        "word"            -> R.string.form_word
        "base_form"       -> R.string.form_base_form
        "past_tense"      -> R.string.form_past_tense
        "past_participle" -> R.string.form_past_participle
        "gerund"          -> R.string.form_gerund
        "singular"        -> R.string.form_singular
        "plural"          -> R.string.form_plural
        "comparative"     -> R.string.form_comparative
        "superlative"     -> R.string.form_superlative
        "dictionary_form" -> R.string.form_dictionary_form
        "masu_form"       -> R.string.form_masu_form
        "ta_form"         -> R.string.form_ta_form
        "te_form"         -> R.string.form_te_form
        "nai_form"        -> R.string.form_nai_form
        "negative"        -> R.string.form_negative
        "past"            -> R.string.form_past
        "particle"        -> R.string.form_particle
        "phonetic"        -> R.string.form_phonetic
        "collocation"     -> R.string.form_collocation
        "kanji"           -> R.string.form_kanji
        "hiragana"        -> R.string.form_hiragana
        "pitch_accent"    -> R.string.form_pitch_accent
        "counter"         -> R.string.form_counter
        "transitive_pair" -> R.string.form_transitive_pair
        "origin"          -> R.string.form_origin
        else              -> null
    }

    // ── Supported languages ───────────────────────────────────────────────────

    val SUPPORTED_LANGUAGES = listOf("en", "ja")

    fun langResId(code: String): Int = when (code) {
        "ja" -> R.string.lang_ja
        else -> R.string.lang_en
    }

    // ── Verb attributes (Japanese) ────────────────────────────────────────────

    /** Language-neutral keys, like part_of_speech; translated for display. */
    val TRANSITIVITY_KEYS = listOf("intransitive", "transitive", "ambitransitive")
    val VERB_GROUP_KEYS = listOf("godan", "ichidan", "irregular")

    fun transitivityResId(key: String): Int? = when (key) {
        "intransitive"   -> R.string.transitivity_intransitive
        "transitive"     -> R.string.transitivity_transitive
        "ambitransitive" -> R.string.transitivity_ambitransitive
        else -> null
    }

    fun verbGroupResId(key: String): Int? = when (key) {
        "godan"     -> R.string.verb_group_godan
        "ichidan"   -> R.string.verb_group_ichidan
        "irregular" -> R.string.verb_group_irregular
        else -> null
    }

    // ── Canonical label lists per language ────────────────────────────────────

    // "phonetic" and "hiragana" are intentionally absent: their only purpose was to carry a
    // pronunciation, which every word form now records in its own `reading`. "kanji" (a written
    // form) and "pitch_accent" (an accent pattern) are not readings and stay. Retired labels keep
    // their formLabelResId() mappings so existing rows still show a translated name.
    val EN_FORM_LABELS = listOf(
        "base_form", "past_tense", "past_participle", "gerund",
        "singular", "plural", "comparative", "superlative",
        "collocation",
    )

    val JA_FORM_LABELS = listOf(
        "dictionary_form", "masu_form", "ta_form", "te_form", "nai_form",
        "negative", "past", "particle",
        "kanji", "pitch_accent", "counter", "transitive_pair", "origin",
    )

    // Canonical `part_of_speech` keys, one list per language, exactly as
    // openspec/specs/word-edit-ui/spec.md fixes them. They live here rather than in the edit
    // screen so a test can reach them: a copy kept private inside a Compose file drifted from
    // these keys for six weeks without anything noticing.
    //
    // The stored value is ALWAYS the key. Never put a display string in these lists — the value
    // reaches the database, the desktop and the seed, all of which compare against the key form.
    val EN_POS = listOf(
        "noun", "verb", "adjective", "adverb",
        "pronoun", "preposition", "conjunction", "interjection", "other",
    )

    val JA_POS = listOf(
        "noun", "verb", "i-adj", "na-adj", "adverb",
        "particle", "aux-verb", "conjunction", "other",
    )

    fun posForLanguage(lang: String): List<String> = when (lang) {
        "ja" -> JA_POS
        else -> EN_POS
    }

    fun formLabelsForLanguage(lang: String): List<String> = when (lang) {
        "ja" -> JA_FORM_LABELS
        else -> EN_FORM_LABELS
    }

    // ── Word list Class and Comparison ────────────────────────────────────────

    /** A word's Class: the namespace and key that decide what its Comparison cell holds. */
    data class WordClass(val namespace: String, val key: String)

    /**
     * The classification that decides what the Comparison column holds.
     *
     * A Japanese verb resolves to its transitivity, because that is what its companion derives
     * from — the opposite verb. Everything else resolves to its part of speech. Null when the word
     * records neither, which the list renders as an empty cell.
     */
    fun classOf(language: String, partOfSpeech: String?, transitivity: String?): WordClass? {
        if (language == "ja" && partOfSpeech == "verb" && !transitivity.isNullOrEmpty()) {
            return WordClass("transitivity", transitivity)
        }
        return partOfSpeech?.takeIf { it.isNotEmpty() }?.let { WordClass("pos", it) }
    }

    /**
     * The `word_forms` label whose value the Comparison column shows. Whatever is recorded is
     * shown, irregular or not — detecting irregularity would need rules and data this lacks.
     */
    fun comparisonLabel(language: String, partOfSpeech: String?): String? =
        when (language to partOfSpeech) {
            "ja" to "verb"   -> "transitive_pair"
            "ja" to "i-adj"  -> "negative"
            "ja" to "na-adj" -> "negative"
            "en" to "verb"   -> "past_tense"
            "en" to "noun"   -> "plural"
            "en" to "adjective" -> "comparative"
            else -> null
        }

    /** The Comparison cell's text, or null when the word records no companion (rendered `—`). */
    fun comparisonValue(word: WordEntry): String? {
        val label = comparisonLabel(word.language, word.partOfSpeech) ?: return null
        return word.wordForms.firstOrNull { it.label == label }?.value?.takeIf { it.isNotEmpty() }
    }

    // ── Class badge abbreviations ──────────────────────────────────────────────
    //
    // These live in strings.xml rather than in a `when` like posDisplay below, because the
    // traditional and simplified forms differ for three of them (動/动, 連/连, 助動/助动) and
    // `Locale.getDefault().language == "zh"` cannot tell the two apart. values-zh-rTW and
    // values-zh-rCN can.

    /** Resource for a transitivity abbreviation, or null for an unknown key. */
    fun transitivityAbbrResId(key: String): Int? = when (key) {
        "intransitive"   -> R.string.transitivity_abbr_intransitive
        "transitive"     -> R.string.transitivity_abbr_transitive
        "ambitransitive" -> R.string.transitivity_abbr_ambitransitive
        else             -> null
    }

    /**
     * Resource for a part-of-speech abbreviation, chosen by the **word's** language rather than
     * the interface locale: a Japanese word gets a CJK badge and an English word a Latin one,
     * whichever language the UI is in. Only the CJK variant tracks the interface locale, and the
     * resource system handles that.
     */
    fun classAbbrResId(wordLanguage: String, pos: String): Int? = when {
        wordLanguage == "ja" -> when (pos) {
            "noun"        -> R.string.pos_abbr_ja_noun
            "verb"        -> R.string.pos_abbr_ja_verb
            "i-adj"       -> R.string.pos_abbr_ja_i_adj
            "na-adj"      -> R.string.pos_abbr_ja_na_adj
            "adverb"      -> R.string.pos_abbr_ja_adverb
            "particle"    -> R.string.pos_abbr_ja_particle
            "aux-verb"    -> R.string.pos_abbr_ja_aux_verb
            "conjunction" -> R.string.pos_abbr_ja_conjunction
            "other"       -> R.string.pos_abbr_ja_other
            else          -> null
        }
        else -> when (pos) {
            "noun"         -> R.string.pos_abbr_en_noun
            "verb"         -> R.string.pos_abbr_en_verb
            "adjective"    -> R.string.pos_abbr_en_adjective
            "adverb"       -> R.string.pos_abbr_en_adverb
            "pronoun"      -> R.string.pos_abbr_en_pronoun
            "preposition"  -> R.string.pos_abbr_en_preposition
            "conjunction"  -> R.string.pos_abbr_en_conjunction
            "interjection" -> R.string.pos_abbr_en_interjection
            "other"        -> R.string.pos_abbr_en_other
            else           -> null
        }
    }

    // ── POS display (locale-aware, no resource needed — already short strings) ─

    fun posDisplay(pos: String): String {
        if (pos.isBlank()) return pos
        val isZh = Locale.getDefault().language == "zh"
        return when (pos) {
            "noun"         -> if (isZh) "名詞 (noun)"          else "Noun"
            "verb"         -> if (isZh) "動詞 (verb)"          else "Verb"
            "adjective"    -> if (isZh) "形容詞 (adjective)"   else "Adjective"
            "adverb"       -> if (isZh) "副詞 (adverb)"        else "Adverb"
            "pronoun"      -> if (isZh) "代名詞 (pronoun)"     else "Pronoun"
            "preposition"  -> if (isZh) "介系詞 (preposition)" else "Preposition"
            "conjunction"  -> if (isZh) "連接詞 (conjunction)" else "Conjunction"
            "interjection" -> if (isZh) "感嘆詞 (interjection)" else "Interjection"
            "i-adj"        -> if (isZh) "い形容詞 (i-adj)"     else "i-adjective"
            "na-adj"       -> if (isZh) "な形容詞 (na-adj)"    else "na-adjective"
            "particle"     -> if (isZh) "助詞 (particle)"      else "Particle"
            "aux-verb"     -> if (isZh) "助動詞 (aux-verb)"    else "Aux. verb"
            "other"        -> if (isZh) "其他 (other)"         else "Other"
            "phrase"       -> if (isZh) "片語 (phrase)"        else "Phrase"
            else           -> pos
        }
    }
}
