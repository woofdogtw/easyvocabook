package tw.idv.woofdog.easyvocabook.ui

import java.util.Locale
import tw.idv.woofdog.easyvocabook.R

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

    // ── Canonical label lists per language ────────────────────────────────────

    val EN_FORM_LABELS = listOf(
        "base_form", "past_tense", "past_participle", "gerund",
        "singular", "plural", "comparative", "superlative",
        "phonetic", "collocation",
    )

    val JA_FORM_LABELS = listOf(
        "dictionary_form", "masu_form", "ta_form", "te_form", "nai_form",
        "negative", "past", "particle",
        "kanji", "hiragana", "pitch_accent", "counter", "transitive_pair", "origin",
    )

    fun formLabelsForLanguage(lang: String): List<String> = when (lang) {
        "ja" -> JA_FORM_LABELS
        else -> EN_FORM_LABELS
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
