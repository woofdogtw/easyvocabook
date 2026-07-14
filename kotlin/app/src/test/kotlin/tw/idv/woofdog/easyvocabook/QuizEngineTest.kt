package tw.idv.woofdog.easyvocabook

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tw.idv.woofdog.easyvocabook.data.model.*
import tw.idv.woofdog.easyvocabook.quiz.QuizEngine
import kotlin.random.Random

class QuizEngineTest {

    private lateinit var engine: QuizEngine
    private val seededRandom = Random(42)

    @Before
    fun setUp() {
        engine = QuizEngine(seededRandom)
    }

    // ── nextWord ──────────────────────────────────────────────────────────────

    @Test
    fun nextWord_emptyPool_returnsNull() {
        assertNull(engine.nextWord(emptyList(), WordFilter()))
    }

    @Test
    fun nextWord_languageFilter_restrictsPool() {
        val words = listOf(
            word(1, "walk", "en"),
            word(2, "猫", "ja"),
        )
        repeat(10) {
            val w = engine.nextWord(words, WordFilter(language = "en"))
            assertEquals("en", w?.language)
        }
    }

    @Test
    fun nextWord_newWords_higherWeightThanPracticed() {
        // New word has weight 3.0; practiced word with 0% correct has weight 4.0;
        // perfect word has weight 1.0. Just verify that selection works for all cases.
        val newWord = word(1, "new", "en", practiceCount = 0)
        val perfectWord = word(2, "perfect", "en", practiceCount = 10, correctCount = 10)
        val pool = listOf(newWord, perfectWord)
        val counts = mutableMapOf(1L to 0, 2L to 0)
        repeat(1000) {
            val w = QuizEngine(Random(it)).nextWord(pool, WordFilter())!!
            counts[w.id] = counts.getOrDefault(w.id, 0) + 1
        }
        assertTrue("New word should appear more than perfect word",
            counts[1L]!! > counts[2L]!!)
    }

    // ── buildTypingCard ───────────────────────────────────────────────────────

    @Test
    fun buildTypingCard_alwaysStartsWithWordField() {
        val w = word(1, "cat", "en")
        val card = engine.buildTypingCard(w)
        assertEquals("word", card.fields[0].label)
        assertEquals("cat", card.fields[0].value)
    }

    @Test
    fun buildTypingCard_enVerb_hasFiveFields() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to "walk", "past_tense" to "walked",
                "past_participle" to "walked", "gerund" to "walking"))
        val card = engine.buildTypingCard(w)
        // word + 4 verb form fields
        assertEquals(5, card.fields.size)
        assertEquals("word", card.fields[0].label)
        assertEquals("base_form", card.fields[1].label)
    }

    @Test
    fun buildTypingCard_jaVerb_hasSixFields() {
        val w = word(2, "食べる", "ja", pos = "verb",
            forms = listOf("dictionary_form" to "食べる", "masu_form" to "食べます",
                "ta_form" to "食べた", "te_form" to "食べて", "nai_form" to "食べない"))
        val card = engine.buildTypingCard(w)
        // word + 5 ja-verb form fields
        assertEquals(6, card.fields.size)
    }

    // ── gradeTyping ───────────────────────────────────────────────────────────

    @Test
    fun gradeTyping_allCorrect() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to "walk", "past_tense" to "walked"))
        val card = engine.buildTypingCard(w)
        // fields: [word, base_form, past_tense, past_participle(blank), gerund(blank)]
        val result = engine.gradeTyping(card, listOf("walk", "walk", "walked"), emptyList())
        assertTrue(result.allCorrect)
    }

    @Test
    fun gradeTyping_partialCorrect() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to "walk", "past_tense" to "walked"))
        val card = engine.buildTypingCard(w)
        // inputs: word="walk"(correct), base_form="wrong"(incorrect)
        val result = engine.gradeTyping(card, listOf("walk", "wrong"), emptyList())
        assertFalse(result.allCorrect)
        assertTrue(result.fieldResults[0].correct)   // word field
        assertFalse(result.fieldResults[1].correct)  // base_form wrong
    }

    @Test
    fun gradeTyping_caseInsensitive() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to "Walk"))
        val card = engine.buildTypingCard(w)
        // fields[0] = "word" expecting "walk"; "WALK" matches case-insensitively
        val result = engine.gradeTyping(card, listOf("WALK", "WALK"), emptyList())
        assertTrue(result.fieldResults[0].correct) // word field
        assertTrue(result.fieldResults[1].correct) // base_form field
    }

    @Test
    fun gradeTyping_jaWordAnsweredWithReading_isCorrect() {
        val w = word(1, "食べる", "ja", reading = "たべる")
        val card = engine.buildTypingCard(w)
        // User types hiragana reading instead of kanji → should be accepted
        val result = engine.gradeTyping(card, listOf("たべる"), emptyList())
        assertTrue(result.fieldResults[0].correct)
    }

    @Test
    fun gradeTyping_missingFormAcceptsAnything() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to ""))  // blank value
        val card = engine.buildTypingCard(w)
        // fields[0]="word"(walk), fields[1]="base_form"(blank→accept anything)
        val result = engine.gradeTyping(card, listOf("walk", "anything"), emptyList())
        assertTrue(result.fieldResults[1].correct) // blank form accepts anything
    }

    // ── buildMcqCard ──────────────────────────────────────────────────────────

    @Test
    fun buildMcqCard_hasAtLeastFourOptions() {
        val w = word(1, "cat", "en")
        val others = listOf(
            word(2, "dog", "en", meaning = "狗"),
            word(3, "bird", "en", meaning = "鳥"),
            word(4, "fish", "en", meaning = "魚"),
        )
        val card = engine.buildMcqCard(w, others + listOf(w))
        assertTrue(card.options.size >= 4)
    }

    @Test
    fun buildMcqCard_correctMeaningPresent() {
        val w = word(1, "cat", "en", meaning = "貓",
            meanings = listOf("小貓"))
        val card = engine.buildMcqCard(w, listOf(w))
        val correctMeanings = card.options.filter { it.isCorrect }.map { it.meaning }
        assertTrue(correctMeanings.contains("貓"))
        assertTrue(correctMeanings.contains("小貓"))
    }

    // ── gradeMcq ──────────────────────────────────────────────────────────────

    @Test
    fun gradeMcq_exactMatchIsCorrect() {
        val w = word(1, "cat", "en", meaning = "貓")
        val card = engine.buildMcqCard(w, listOf(w, word(2, "dog", "en", meaning = "狗"),
            word(3, "bird", "en", meaning = "鳥"), word(4, "fish", "en", meaning = "魚")))
        val correctSet = card.options.filter { it.isCorrect }.map { it.meaning }.toSet()
        val result = engine.gradeMcq(card, correctSet)
        assertTrue(result.allCorrect)
    }

    @Test
    fun gradeMcq_missingMeaningIsIncorrect() {
        val w = word(1, "cat", "en", meaning = "貓", meanings = listOf("小貓"))
        val card = engine.buildMcqCard(w, listOf(w, word(2, "dog", "en", meaning = "狗"),
            word(3, "bird", "en", meaning = "鳥"), word(4, "fish", "en", meaning = "魚")))
        val result = engine.gradeMcq(card, setOf("貓"))
        assertFalse(result.allCorrect)
    }

    @Test
    fun gradeMcq_extraSelectionIsIncorrect() {
        val w = word(1, "cat", "en", meaning = "貓")
        val card = engine.buildMcqCard(w, listOf(w, word(2, "dog", "en", meaning = "狗"),
            word(3, "bird", "en", meaning = "鳥"), word(4, "fish", "en", meaning = "魚")))
        val result = engine.gradeMcq(card, setOf("貓", "狗"))
        assertFalse(result.allCorrect)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun word(
        id: Long, wordStr: String, lang: String,
        meaning: String = "意思",
        pos: String? = null,
        reading: String? = null,
        forms: List<Pair<String, String>> = emptyList(),
        meanings: List<String> = emptyList(),
        practiceCount: Int = 0,
        correctCount: Int = 0,
    ) = WordEntry(
        id = id, word = wordStr, reading = reading, meaning = meaning,
        partOfSpeech = pos, note = null, language = lang,
        practiceCount = practiceCount, correctCount = correctCount,
        createdAt = 0L, practicedAt = null,
        wordMeanings = meanings.mapIndexed { i, m -> WordMeaning(i.toLong(), m) },
        wordForms = forms.mapIndexed { i, (l, v) -> tw.idv.woofdog.easyvocabook.data.model.WordForm(i.toLong(), l, v) },
        sentences = emptyList(),
    )
}
