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
    fun buildTypingCard_jaVerb_hasWordFormsAndTransitivity() {
        val w = word(2, "食べる", "ja", pos = "verb",
            forms = listOf("dictionary_form" to "食べる", "masu_form" to "食べます",
                "ta_form" to "食べた", "te_form" to "食べて", "nai_form" to "食べない"))
        val card = engine.buildTypingCard(w)
        // word + 5 conjugations + transitive_pair + the transitivity question
        assertEquals(8, card.fields.size)
        assertEquals(QuizEngine.WORD_FIELD, card.fields.first().label)
        assertEquals(QuizEngine.TRANSITIVITY_FIELD, card.fields.last().label)
        assertTrue(card.fields.any { it.label == "transitive_pair" })
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
    fun gradeTyping_blankFormOnTheSelectedWord_demandsABlankAnswer() {
        val w = word(1, "walk", "en", pos = "verb",
            forms = listOf("base_form" to ""))  // blank value
        val card = engine.buildTypingCard(w)
        // Nothing is recorded for base_form, so nothing is the answer.
        assertFalse(engine.gradeTyping(card, listOf("walk", "anything"), listOf(w)).fieldResults[1].correct)
        assertTrue(engine.gradeTyping(card, listOf("walk", ""), listOf(w)).fieldResults[1].correct)
    }

    @Test
    fun gradeTyping_synonymWithoutThatForm_stillAcceptsAnything() {
        // The leniency that survives the stricter rule: answering with a legitimate synonym must
        // not be penalised for data the synonym happens not to carry.
        val target = word(1, "walk", "en", pos = "verb", meaning = "走路",
            forms = listOf("base_form" to "walk", "past_tense" to "walked"))
        val synonym = word(2, "stroll", "en", pos = "verb", meaning = "走路")
        val card = engine.buildTypingCard(target)
        val past = card.fields.indexOfFirst { it.label == "past_tense" }
        val inputs = MutableList(card.fields.size) { "" }.also { it[0] = "stroll"; it[past] = "whatever" }
        assertTrue(engine.gradeTyping(card, inputs, listOf(target, synonym)).fieldResults[past].correct)
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

    // ── gradeTyping: per-form readings ────────────────────────────────────────

    @Test
    fun gradeTyping_formAnsweredWithItsReading_isCorrect() {
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる",
            formsWithReading = listOf(Triple("masu_form", "食べます", "たべます")))
        val card = engine.buildTypingCard(w)
        val masu = card.fields.indexOfFirst { it.label == "masu_form" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "食べる"; inputs[masu] = "たべます"
        val result = engine.gradeTyping(card, inputs, emptyList())
        assertTrue(result.fieldResults[masu].correct)
    }

    @Test
    fun gradeTyping_formAnsweredWithItsValue_isCorrect() {
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる",
            formsWithReading = listOf(Triple("masu_form", "食べます", "たべます")))
        val card = engine.buildTypingCard(w)
        val masu = card.fields.indexOfFirst { it.label == "masu_form" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "食べる"; inputs[masu] = "食べます"
        val result = engine.gradeTyping(card, inputs, emptyList())
        assertTrue(result.fieldResults[masu].correct)
    }

    @Test
    fun gradeTyping_formWithoutReading_matchesValueOnly() {
        val w = word(1, "walk", "en", pos = "verb", forms = listOf("past_tense" to "walked"))
        val card = engine.buildTypingCard(w)
        val past = card.fields.indexOfFirst { it.label == "past_tense" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "walk"; inputs[past] = "something else"
        val result = engine.gradeTyping(card, inputs, emptyList())
        assertFalse(result.fieldResults[past].correct)
    }

    @Test
    fun gradeTyping_readingOnlyForm_rejectsEmptyAnswer() {
        // A form may carry only a reading; an empty answer must not satisfy its empty value.
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる",
            formsWithReading = listOf(Triple("masu_form", "", "たべます")))
        val card = engine.buildTypingCard(w)
        val masu = card.fields.indexOfFirst { it.label == "masu_form" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "食べる"
        assertFalse(engine.gradeTyping(card, inputs, emptyList()).fieldResults[masu].correct)

        inputs[masu] = "たべます"
        assertTrue(engine.gradeTyping(card, inputs, emptyList()).fieldResults[masu].correct)
    }

    @Test
    fun gradeTyping_surroundingWhitespaceIgnored() {
        val w = word(1, "walk", "en", pos = "verb", forms = listOf("past_tense" to "walked"))
        val card = engine.buildTypingCard(w)
        val past = card.fields.indexOfFirst { it.label == "past_tense" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "  walk  "; inputs[past] = "\u3000walked\u3000"  // ASCII and full-width spaces
        val result = engine.gradeTyping(card, inputs, emptyList())
        assertTrue(result.fieldResults[0].correct)
        assertTrue(result.fieldResults[past].correct)
    }

    @Test
    fun gradeTyping_caseFoldingCoversNonAscii() {
        val w = word(1, "café", "en")
        val card = engine.buildTypingCard(w)
        val result = engine.gradeTyping(card, listOf("CAFÉ"), emptyList())
        assertTrue(result.fieldResults[0].correct)
    }

    @Test
    fun gradeTyping_kanaScriptsAreNotFolded() {
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる",
            formsWithReading = listOf(Triple("masu_form", "食べます", "たべます")))
        val card = engine.buildTypingCard(w)
        val masu = card.fields.indexOfFirst { it.label == "masu_form" }
        val inputs = MutableList(card.fields.size) { "" }
        inputs[0] = "食べる"; inputs[masu] = "タベマス"
        val result = engine.gradeTyping(card, inputs, emptyList())
        assertFalse(result.fieldResults[masu].correct)
    }

    @Test
    fun gradeTyping_revealExposesFormReading() {
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる",
            formsWithReading = listOf(Triple("masu_form", "食べます", "たべます")))
        val card = engine.buildTypingCard(w)
        val masu = card.fields.indexOfFirst { it.label == "masu_form" }
        val result = engine.gradeTyping(card, MutableList(card.fields.size) { "" }, emptyList())
        assertEquals("食べます", result.fieldResults[masu].correctValue)
        assertEquals("たべます", result.fieldResults[masu].correctReading)
        assertEquals("たべる", result.fieldResults[0].correctReading)
    }

    /// The Rust UI once graded the base field its own way and rejected every reading; Kotlin
    /// routes it through the same predicate as any other field. Pinned so it stays that way.
    @Test
    fun baseWordField_acceptsTheReading() {
        val adj = word(1, "長い", "ja", meaning = "長", pos = "i-adj", reading = "ながい")
        val card = engine.buildTypingCard(adj)
        val inputs = MutableList(card.fields.size) { "" }

        inputs[0] = "長い"
        assertTrue(engine.gradeTyping(card, inputs, listOf(adj)).fieldResults[0].correct)

        inputs[0] = "ながい"
        assertTrue(
            "the reading must be accepted for the base word",
            engine.gradeTyping(card, inputs, listOf(adj)).fieldResults[0].correct,
        )

        inputs[0] = "  ながい  "
        assertTrue(engine.gradeTyping(card, inputs, listOf(adj)).fieldResults[0].correct)

        inputs[0] = "みじかい"
        assertFalse(engine.gradeTyping(card, inputs, listOf(adj)).fieldResults[0].correct)
    }

    @Test
    fun baseWordField_acceptsTheReadingForANoun() {
        val noun = word(1, "自分", "ja", meaning = "自己", pos = "noun", reading = "じぶん")
        val card = engine.buildTypingCard(noun)
        val inputs = MutableList(card.fields.size) { "じぶん" }
        assertTrue(engine.gradeTyping(card, inputs, listOf(noun)).fieldResults[0].correct)
    }

    // ── verb transitivity and the stricter empty rule ─────────────────────────

    /** Inputs sized to a card, with the base word filled in. */
    private fun inputsFor(card: tw.idv.woofdog.easyvocabook.quiz.TypingCard, base: String) =
        MutableList(card.fields.size) { "" }.also { it[0] = base }

    private fun jaVerb(
        pairValue: String? = null,
        transitivity: String? = "intransitive",
    ) = word(1, "食べる", "ja", pos = "verb", reading = "たべる", transitivity = transitivity,
        forms = buildList {
            add("dictionary_form" to "食べる")
            if (pairValue != null) add("transitive_pair" to pairValue)
        })

    @Test
    fun partnerlessVerb_typingAnything_isIncorrect() {
        val w = jaVerb(pairValue = null)
        val card = engine.buildTypingCard(w)
        val idx = card.fields.indexOfFirst { it.label == "transitive_pair" }
        val inputs = inputsFor(card, "食べる").also { it[idx] = "食べさせる" }
        val r = engine.gradeTyping(card, inputs, listOf(w))
        assertFalse(r.fieldResults[idx].correct)
        assertFalse(r.allCorrect)
    }

    @Test
    fun partnerlessVerb_leftBlank_isCorrect() {
        val w = jaVerb(pairValue = null)
        val card = engine.buildTypingCard(w)
        val idx = card.fields.indexOfFirst { it.label == "transitive_pair" }
        val inputs = inputsFor(card, "食べる").also { it[card.fields.lastIndex] = "intransitive" }
        assertTrue(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
    }

    @Test
    fun verbWithPartner_requiresThatPartner() {
        val w = jaVerb(pairValue = "食べさせる")
        val card = engine.buildTypingCard(w)
        val idx = card.fields.indexOfFirst { it.label == "transitive_pair" }
        val inputs = inputsFor(card, "食べる")
        assertFalse(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
        inputs[idx] = "食べさせる"
        assertTrue(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
    }

    @Test
    fun emptyExpectationAppliesToAnyField() {
        // A conjugation the word has no value for now demands a blank answer.
        val w = word(1, "食べる", "ja", pos = "verb", reading = "たべる", transitivity = "transitive",
            forms = listOf("dictionary_form" to "食べる"))
        val card = engine.buildTypingCard(w)
        val idx = card.fields.indexOfFirst { it.label == "nai_form" }
        val inputs = inputsFor(card, "食べる").also { it[idx] = "anything" }
        assertFalse(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
    }

    @Test
    fun wrongTransitivityType_failsTheAnswer() {
        val w = jaVerb(transitivity = "transitive")
        val card = engine.buildTypingCard(w)
        val idx = card.fields.lastIndex
        val inputs = inputsFor(card, "食べる").also { it[idx] = "intransitive" }
        val r = engine.gradeTyping(card, inputs, listOf(w))
        assertFalse(r.fieldResults[idx].correct)
        assertFalse(r.allCorrect)
    }

    @Test
    fun ambitransitiveIsADistinctAnswer() {
        val w = jaVerb(transitivity = "ambitransitive")
        val card = engine.buildTypingCard(w)
        val idx = card.fields.lastIndex
        val inputs = inputsFor(card, "食べる").also { it[idx] = "intransitive" }
        assertFalse(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
        inputs[idx] = "ambitransitive"
        assertTrue(engine.gradeTyping(card, inputs, listOf(w)).fieldResults[idx].correct)
    }

    @Test
    fun verbQuestionsAreJapaneseOnly() {
        val en = word(1, "walk", "en", pos = "verb", forms = listOf("base_form" to "walk"))
        val card = engine.buildTypingCard(en)
        assertTrue(card.fields.none { it.label == QuizEngine.TRANSITIVITY_FIELD })
        assertTrue(card.fields.none { it.label == "transitive_pair" })
    }

    @Test
    fun japaneseNounIsQuizzedOnTheWordAlone() {
        // The suggestion table lists nothing for ja/noun, and there is no fallback to the word's
        // own forms, so a custom row must not become a question.
        val n = word(1, "本", "ja", pos = "noun", reading = "ほん",
            forms = listOf("counter" to "冊"))
        val card = engine.buildTypingCard(n)
        assertEquals(1, card.fields.size)
        assertEquals(QuizEngine.WORD_FIELD, card.fields.first().label)
    }

    private fun word(
        id: Long, wordStr: String, lang: String,
        meaning: String = "意思",
        pos: String? = null,
        reading: String? = null,
        forms: List<Pair<String, String>> = emptyList(),
        formsWithReading: List<Triple<String, String, String?>> = emptyList(),
        meanings: List<String> = emptyList(),
        practiceCount: Int = 0,
        correctCount: Int = 0,
        transitivity: String? = null,
    ) = WordEntry(
        id = id, word = wordStr, reading = reading, meaning = meaning,
        partOfSpeech = pos, note = null, language = lang,
        practiceCount = practiceCount, correctCount = correctCount,
        createdAt = 0L, practicedAt = null,
        wordMeanings = meanings.mapIndexed { i, m -> WordMeaning(i.toLong(), m) },
        wordForms = forms.mapIndexed { i, (l, v) ->
            tw.idv.woofdog.easyvocabook.data.model.WordForm(i.toLong(), l, v)
        } + formsWithReading.mapIndexed { i, (l, v, r) ->
            tw.idv.woofdog.easyvocabook.data.model.WordForm((forms.size + i).toLong(), l, v, r)
        },
        sentences = emptyList(),
        transitivity = transitivity,
    )
}
