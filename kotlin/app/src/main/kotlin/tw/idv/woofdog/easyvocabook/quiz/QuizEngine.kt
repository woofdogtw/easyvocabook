package tw.idv.woofdog.easyvocabook.quiz

import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter
import kotlin.random.Random

// ── Result types ──────────────────────────────────────────────────────────────

data class TypingField(val label: String, val value: String)

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
        val formMap = word.wordForms.associate { it.label to it.value }
        val formFields = if (suggestedLabels.isNotEmpty()) {
            suggestedLabels.map { label -> TypingField(label, formMap[label] ?: "") }
        } else {
            word.wordForms.map { TypingField(it.label, it.value) }
        }
        // Always test the word itself first; ensures at least one graded field
        val fields = listOf(TypingField("word", word.word)) + formFields
        return TypingCard(word = word, meaningPrompt = prompt, fields = fields)
    }

    fun gradeTyping(card: TypingCard, userInputs: List<String>, allWords: List<WordEntry>): TypingResult {
        val word = card.word
        val synonyms = findSynonyms(word, allWords)

        // Grade the base word (first field is the base/dictionary form)
        val fieldResults = card.fields.mapIndexed { idx, field ->
            val input = userInputs.getOrElse(idx) { "" }.trim()
            val expected = field.value
            val correct = if (expected.isBlank()) {
                true // missing form: accept anything
            } else {
                input.equals(expected, ignoreCase = true) ||
                // For the "word" field: also accept reading (e.g. hiragana for kanji),
                // synonym word strings, and synonym readings — any is sufficient.
                (field.label == "word" && (
                    (!word.reading.isNullOrBlank() && input.equals(word.reading, ignoreCase = true)) ||
                    synonyms.any { it.equals(input, ignoreCase = true) } ||
                    synonyms.any { syn ->
                        allWords.find { it.word.equals(syn, ignoreCase = true) }
                            ?.reading?.equals(input, ignoreCase = true) == true
                    }
                )) ||
                synonyms.any { syn ->
                    val synWord = allWords.find { it.word.equals(syn, ignoreCase = true) }
                    synWord?.wordForms?.find { it.label == field.label }?.value
                        ?.equals(input, ignoreCase = true) == true
                }
            }
            TypingFieldResult(label = field.label, userInput = input, correct = correct, correctValue = expected)
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
