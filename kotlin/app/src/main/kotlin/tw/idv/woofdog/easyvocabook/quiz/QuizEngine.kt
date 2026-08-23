package tw.idv.woofdog.easyvocabook.quiz

import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter
import kotlin.random.Random

// ── Result types ──────────────────────────────────────────────────────────────

data class TypingField(val label: String, val value: String, val reading: String? = null)

data class TypingCard(
    val word: WordEntry,
    val meaningPrompt: String,
    val fields: List<TypingField>,
)

data class TypingFieldResult(
    val label: String,
    val userInput: String,
    val correct: Boolean,
    val correctValue: String,
    val correctReading: String? = null,
)

data class TypingResult(
    val card: TypingCard,
    val fieldResults: List<TypingFieldResult>,
    val synonyms: List<String>,
    val allCorrect: Boolean,
)

data class McqOption(val meaning: String, val isCorrect: Boolean)

data class McqCard(
    val word: WordEntry,
    val options: List<McqOption>,
)

data class McqResult(
    val card: McqCard,
    val selected: Set<String>,
    val allCorrect: Boolean,
)

// Word form labels per (language, partOfSpeech)
object WordFormLabels {
    fun forWord(language: String, pos: String?): List<String> = when (language) {
        "en" -> when (pos) {
            "verb" -> listOf("base_form", "past_tense", "past_participle", "gerund")
            "noun" -> listOf("singular", "plural")
            "adjective", "adj" -> listOf("comparative", "superlative")
            else -> emptyList()
        }
        "ja" -> when (pos) {
            "verb", "動詞" -> listOf(
                "dictionary_form", "masu_form", "ta_form", "te_form", "nai_form", "transitive_pair",
            )
            "i-adj", "い形容詞" -> listOf("te_form", "negative", "past")
            "na-adj", "な形容詞" -> listOf("te_form", "negative")
            // Japanese nouns suggest nothing: they have no plural, a counter is not unique for
            // most nouns, and a particle depends on sentence role rather than the noun itself.
            else -> emptyList()
        }
        else -> emptyList()
    }
}

// ── Engine ────────────────────────────────────────────────────────────────────

class QuizEngine(private val random: Random = Random.Default) {

    fun nextWord(pool: List<WordEntry>, filter: WordFilter): WordEntry? {
        val filtered = if (filter.language != null) pool.filter { it.language == filter.language } else pool
        if (filtered.isEmpty()) return null
        val weights = filtered.map { w ->
            if (w.practiceCount == 0) 3.0
            else 1.0 + (w.practiceCount - w.correctCount).toDouble() / w.practiceCount * 3.0
        }
        val total = weights.sum()
        var pick = random.nextDouble() * total
        for ((word, weight) in filtered.zip(weights)) {
            pick -= weight
            if (pick <= 0) return word
        }
        return filtered.last()
    }

    fun buildTypingCard(word: WordEntry): TypingCard {
        val prompt = if (word.wordMeanings.isNotEmpty()) {
            val pool = listOf(word.meaning) + word.wordMeanings.map { it.meaning }
            pool[random.nextInt(pool.size)]
        } else {
            word.meaning
        }
        val suggestedLabels = WordFormLabels.forWord(word.language, word.partOfSpeech)
        // Fields come from the table alone. There is deliberately no fallback to "whatever forms
        // this word happens to have": an unlisted combination shows no form fields, exactly as on
        // the desktop, so a custom form added to a Japanese noun cannot reintroduce a divergence.
        val formFields = suggestedLabels.map { label ->
            val form = word.wordForms.find { it.label == label }
            TypingField(label, form?.value ?: "", form?.reading)
        }
        // Always test the word itself first; ensures at least one graded field
        // Japanese verbs are additionally asked their transitivity, chosen rather than typed.
        val verbFields =
            if (word.language == "ja" && word.partOfSpeech == "verb")
                listOf(TypingField(TRANSITIVITY_FIELD, word.transitivity.orEmpty()))
            else emptyList()
        val fields = listOf(TypingField(WORD_FIELD, word.word, word.reading)) + formFields + verbFields
        return TypingCard(word = word, meaningPrompt = prompt, fields = fields)
    }

    /**
     * The single answer-matching rule: an input matches a field when it equals either the value
     * or the reading, comparing trimmed and case-insensitively.
     *
     * Both sides are guarded on emptiness. The guard on [value] matters as much as the one on
     * [reading]: a form may carry only a reading, and an unguarded comparison would let an empty
     * answer match an empty value and mark such a field correct.
     *
     * Case folding is Unicode (so `café` matches `CAFÉ`); kana are deliberately not folded, so
     * katakana never satisfies a hiragana reading.
     */
    private fun matches(input: String, value: String?, reading: String?): Boolean {
        val typed = input.trim()
        val v = value?.trim().orEmpty()
        val r = reading?.trim().orEmpty()
        return (v.isNotEmpty() && typed.equals(v, ignoreCase = true)) ||
            (r.isNotEmpty() && typed.equals(r, ignoreCase = true))
    }

    companion object {
        /** Label of the pseudo-field that holds the base word itself. */
        const val WORD_FIELD = "word"

        /**
         * Label of the pseudo-field holding the verb's transitivity. Not a word form: it
         * describes the verb itself and is answered by choosing one of three keys, not by typing.
         */
        const val TRANSITIVITY_FIELD = "transitivity"
    }

    fun gradeTyping(card: TypingCard, userInputs: List<String>, allWords: List<WordEntry>): TypingResult {
        val word = card.word
        val synonyms = findSynonyms(word, allWords)

        // Decide which word is being graded against *before* grading any field. The user may
        // answer with a synonym, in which case that word's own forms are the expectation.
        // Grading each field independently against "the selected word or any synonym" would let
        // a string that happens to equal some synonym's field satisfy an empty expectation.
        val baseInput = userInputs.getOrElse(0) { "" }
        val matched: WordEntry? =
            if (matches(baseInput, word.word, word.reading)) word
            else synonyms.firstNotNullOfOrNull { syn ->
                allWords.find { matches(syn, it.word, null) }
                    ?.takeIf { matches(baseInput, it.word, it.reading) }
            }
        val answeredWithSynonym = matched != null && matched !== word

        val fieldResults = card.fields.mapIndexed { idx, field ->
            val input = userInputs.getOrElse(idx) { "" }
            val expected = field.value
            val expectedReading = field.reading

            val correct = when {
                // The base field is right exactly when the typed word was recognised at all.
                field.label == WORD_FIELD -> matched != null
                matched == null -> false
                // A chosen key, compared against what the matched word records.
                field.label == TRANSITIVITY_FIELD -> {
                    val expectedKey = matched.transitivity.orEmpty()
                    if (expectedKey.isBlank()) input.isBlank() else input == expectedKey
                }
                else -> {
                    val form = matched.wordForms.find { it.label == field.label }
                    when {
                        // A synonym with no data for this label is not the user's mistake.
                        form == null && answeredWithSynonym -> true
                        // Nothing recorded to answer: the answer is nothing.
                        form == null -> input.isBlank()
                        form.value.isBlank() && form.reading.isNullOrBlank() -> input.isBlank()
                        else -> matches(input, form.value, form.reading)
                    }
                }
            }
            TypingFieldResult(
                label = field.label,
                userInput = input.trim(),
                correct = correct,
                correctValue = expected,
                correctReading = expectedReading,
            )
        }

        return TypingResult(
            card = card,
            fieldResults = fieldResults,
            synonyms = synonyms,
            allCorrect = fieldResults.all { it.correct },
        )
    }

    fun buildMcqCard(word: WordEntry, allWords: List<WordEntry>): McqCard {
        val correctMeanings = buildSet {
            add(word.meaning)
            word.wordMeanings.forEach { add(it.meaning) }
        }
        val distractors = allWords.asSequence()
            .filter { it.id != word.id }
            .flatMap { w -> listOf(w.meaning) + w.wordMeanings.map { it.meaning } }
            .filter { it !in correctMeanings }
            .distinct()
            .shuffled(random)
            .take(maxOf(correctMeanings.size + 3, 4) - correctMeanings.size)
            .toList()

        val options = (correctMeanings.map { McqOption(it, true) } + distractors.map { McqOption(it, false) })
            .shuffled(random)
        return McqCard(word = word, options = options)
    }

    fun gradeMcq(card: McqCard, selectedMeanings: Set<String>): McqResult {
        val correctSet = card.options.filter { it.isCorrect }.map { it.meaning }.toSet()
        return McqResult(
            card = card,
            selected = selectedMeanings,
            allCorrect = selectedMeanings == correctSet,
        )
    }

    private fun findSynonyms(word: WordEntry, allWords: List<WordEntry>): List<String> {
        val allMeanings = buildSet {
            add(word.meaning)
            word.wordMeanings.forEach { add(it.meaning) }
        }
        return allWords
            .filter { it.id != word.id && it.language == word.language }
            .filter { other ->
                val otherMeanings = buildSet {
                    add(other.meaning)
                    other.wordMeanings.forEach { add(it.meaning) }
                }
                otherMeanings.intersect(allMeanings).isNotEmpty()
            }
            .map { it.word }
    }
}
