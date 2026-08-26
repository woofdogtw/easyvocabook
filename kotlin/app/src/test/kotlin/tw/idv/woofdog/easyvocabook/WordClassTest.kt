package tw.idv.woofdog.easyvocabook

import org.junit.Assert.*
import org.junit.Test
import tw.idv.woofdog.easyvocabook.data.model.*
import tw.idv.woofdog.easyvocabook.ui.Labels

/**
 * The word list's Class and Comparison rules. Mirrors the Rust tests in `db/labels.rs` case for
 * case, since the two platforms must agree on what a row says.
 */
class WordClassTest {

    private fun word(
        language: String = "ja",
        pos: String? = null,
        transitivity: String? = null,
        forms: List<WordForm> = emptyList(),
    ) = WordEntry(
        id = 0, word = "x", reading = null, meaning = "m", partOfSpeech = pos, note = null,
        language = language, practiceCount = 0, correctCount = 0, createdAt = 0, practicedAt = null,
        wordMeanings = emptyList(), wordForms = forms, sentences = emptyList(),
        transitivity = transitivity,
    )

    private fun form(label: String, value: String) = WordForm(0, label, value, null)

    @Test
    fun pairedVerbResolvesToTransitivityAndItsPartner() {
        assertEquals(
            Labels.WordClass("transitivity", "intransitive"),
            Labels.classOf("ja", "verb", "intransitive"),
        )
        val w = word(pos = "verb", transitivity = "intransitive",
            forms = listOf(form("transitive_pair", "上げる"), form("masu_form", "上がります")))
        assertEquals("上げる", Labels.comparisonValue(w))
    }

    @Test
    fun partnerlessVerbKeepsItsClassButHasNoComparison() {
        assertEquals(
            Labels.WordClass("transitivity", "transitive"),
            Labels.classOf("ja", "verb", "transitive"),
        )
        val w = word(pos = "verb", transitivity = "transitive",
            forms = listOf(form("masu_form", "食べます")))
        assertNull(Labels.comparisonValue(w))
    }

    /** Three of the four ambitransitive verbs do record a partner; the class must not hide it. */
    @Test
    fun ambitransitiveVerbShowsAPartnerWhenOneIsRecorded() {
        assertEquals(
            Labels.WordClass("transitivity", "ambitransitive"),
            Labels.classOf("ja", "verb", "ambitransitive"),
        )
        val w = word(pos = "verb", transitivity = "ambitransitive",
            forms = listOf(form("transitive_pair", "開ける")))
        assertEquals("開ける", Labels.comparisonValue(w))
    }

    @Test
    fun nounFallsBackToPartOfSpeechAndHasNoComparison() {
        assertEquals(Labels.WordClass("pos", "noun"), Labels.classOf("ja", "noun", null))
        assertNull(Labels.comparisonValue(word(pos = "noun")))
    }

    /**
     * No seeded word takes this path — all 289 Japanese verbs record a transitivity — so the rule
     * exists for words added by hand, and this test is its only coverage.
     */
    @Test
    fun verbWithoutTransitivityFallsBackToPartOfSpeech() {
        assertEquals(Labels.WordClass("pos", "verb"), Labels.classOf("ja", "verb", null))
        assertEquals(Labels.WordClass("pos", "verb"), Labels.classOf("ja", "verb", ""))
    }

    @Test
    fun wordWithNoPartOfSpeechHasNoClass() {
        assertNull(Labels.classOf("ja", null, null))
        assertNull(Labels.classOf("ja", "", null))
    }

    @Test
    fun englishFormsFollowTheirPartOfSpeech() {
        assertEquals("past_tense", Labels.comparisonLabel("en", "verb"))
        assertEquals("plural", Labels.comparisonLabel("en", "noun"))
        assertEquals("comparative", Labels.comparisonLabel("en", "adjective"))
        // Regular forms are shown too; irregularity is not detected.
        val w = word(language = "en", pos = "verb", forms = listOf(form("past_tense", "walked")))
        assertEquals("walked", Labels.comparisonValue(w))
    }

    /** Transitivity belongs to Japanese verbs; an English verb never takes that branch. */
    @Test
    fun englishVerbNeverResolvesToTransitivity() {
        assertEquals(Labels.WordClass("pos", "verb"), Labels.classOf("en", "verb", "transitive"))
    }

    @Test
    fun languageWithoutARuleHasNoComparison() {
        assertNull(Labels.comparisonLabel("de", "noun"))
        val w = word(language = "de", pos = "noun", forms = listOf(form("plural", "x")))
        assertNull(Labels.comparisonValue(w))
    }
}
