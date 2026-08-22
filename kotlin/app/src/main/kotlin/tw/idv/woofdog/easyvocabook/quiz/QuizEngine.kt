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
            "verb", "動詞" -> listOf("dictionary_form", "masu_form", "ta_form", "te_form", "nai_form")
            "i-adj", "い形容詞" -> listOf("te_form", "negative", "past")
            "na-adj", "な形容詞" -> listOf("te_form", "negative")
            "particle", "助詞" -> listOf("particle")
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
        val formFields = if (suggestedLabels.isNotEmpty()) {
            suggestedLabels.map { label ->
                val form = word.wordForms.find { it.label == label }
                TypingField(label, form?.value ?: "", form?.reading)
            }
        } else {
            word.wordForms.map { TypingField(it.label, it.value, it.reading) }
        }
        // Always test the word itself first; ensures at least one graded field
        val fields = listOf(TypingField("word", word.word, word.reading)) + formFields
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

    fun gradeTyping(card: TypingCard, userInputs: List<String>, allWords: List<WordEntry>): TypingResult {
        val word = card.word
        val synonyms = findSynonyms(word, allWords)

        val fieldResults = card.fields.mapIndexed { idx, field ->
            val input = userInputs.getOrElse(idx) { "" }
            val expected = field.value
            val expectedReading = field.reading
            // A field is unspecified — and accepts anything — only when it carries neither a
            // value nor a reading. That subsumes the "synonym has no row for this label" case.
            val correct = if (expected.isBlank() && expectedReading.isNullOrBlank()) {
                true
            } else {
                matches(input, expected, expectedReading) ||
                // Any synonym of the prompt may be answered instead of the selected word; for
                // the base field its own word/reading count, for the others its matching form.
                synonyms.any { syn ->
                    val synWord = allWords.find { matches(syn, it.word, null) } ?: return@any false
                    if (field.label == "word") {
                        matches(input, synWord.word, synWord.reading)
                    } else {
                        val synForm = synWord.wordForms.find { it.label == field.label }
                        synForm != null && matches(input, synForm.value, synForm.reading)
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
