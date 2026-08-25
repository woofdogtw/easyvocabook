package tw.idv.woofdog.easyvocabook

import org.junit.Assert.*
import org.junit.Test
import tw.idv.woofdog.easyvocabook.quiz.WordFormLabels
import tw.idv.woofdog.easyvocabook.ui.Labels

/**
 * Guards the `part_of_speech` vocabulary.
 *
 * The lists are duplicated across Rust, Kotlin and the seed generator with nothing linking the
 * copies. The Kotlin copy once held localized display strings ("動詞" for `verb`), which made
 * every comparison against the key form fail — Japanese verbs silently lost their transitivity
 * and verb group on save. Nothing caught it because neither platform asserted its own list.
 */
class PartOfSpeechTest {

    /** openspec/specs/word-edit-ui/spec.md — Part-of-speech dropdown options per language. */
    private val specEn = listOf(
        "noun", "verb", "adjective", "adverb",
        "pronoun", "preposition", "conjunction", "interjection", "other",
    )
    private val specJa = listOf(
        "noun", "verb", "i-adj", "na-adj", "adverb",
        "particle", "aux-verb", "conjunction", "other",
    )

    @Test
    fun listsMatchTheSpecification() {
        assertEquals("English part-of-speech options", specEn, Labels.EN_POS)
        assertEquals("Japanese part-of-speech options", specJa, Labels.JA_POS)
    }

    /**
     * The check that would have caught the original bug the day it was written: a display string
     * is by definition non-ASCII, a key never is.
     */
    @Test
    fun everyOptionIsAnAsciiKey() {
        for ((language, list) in listOf("en" to Labels.EN_POS, "ja" to Labels.JA_POS)) {
            for (pos in list) {
                assertTrue(
                    "$language part of speech \"$pos\" must be a language-neutral ASCII key, " +
                        "not a display string",
                    pos.all { it.code in 0x20..0x7E },
                )
            }
        }
    }

    @Test
    fun posForLanguageReturnsTheRightList() {
        assertEquals(Labels.JA_POS, Labels.posForLanguage("ja"))
        assertEquals(Labels.EN_POS, Labels.posForLanguage("en"))
        // An unknown language falls back to English rather than returning nothing.
        assertEquals(Labels.EN_POS, Labels.posForLanguage("de"))
    }

    /** `phrase` was offered by Android alone; it is in no spec and in neither Rust constant. */
    @Test
    fun phraseIsNotAnOption() {
        assertFalse(Labels.EN_POS.contains("phrase"))
        assertFalse(Labels.JA_POS.contains("phrase"))
    }

    /**
     * Form suggestions must key off the canonical value only. Accepting a display string here
     * while every other reader required the key is what hid the divergence for six weeks.
     */
    @Test
    fun formSuggestionsRejectDisplayStrings() {
        assertTrue(WordFormLabels.forWord("ja", "verb").isNotEmpty())
        assertTrue(WordFormLabels.forWord("ja", "動詞").isEmpty())
        assertTrue(WordFormLabels.forWord("ja", "i-adj").isNotEmpty())
        assertTrue(WordFormLabels.forWord("ja", "い形容詞").isEmpty())
        assertTrue(WordFormLabels.forWord("ja", "な形容詞").isEmpty())
        // "adj" was an alias for `adjective` that no producer emitted.
        assertTrue(WordFormLabels.forWord("en", "adjective").isNotEmpty())
        assertTrue(WordFormLabels.forWord("en", "adj").isEmpty())
    }
}
