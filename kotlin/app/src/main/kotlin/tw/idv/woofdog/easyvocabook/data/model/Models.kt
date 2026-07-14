package tw.idv.woofdog.easyvocabook.data.model

data class BookInfo(
    val name: String,
    val description: String?,
    val defaultLanguage: String,
    val version: Int,
    val lastModified: Long,
)

data class WordMeaning(
    val id: Long,
    val meaning: String,
)

data class WordForm(
    val id: Long,
    val label: String,
    val value: String,
)

data class Sentence(
    val id: Long,
    val sentence: String,
    val translation: String?,
)

data class WordEntry(
    val id: Long,
    val word: String,
    val reading: String?,
    val meaning: String,
    val partOfSpeech: String?,
    val note: String?,
    val language: String,
    val practiceCount: Int,
    val correctCount: Int,
    val createdAt: Long,
    val practicedAt: Long?,
    val wordMeanings: List<WordMeaning>,
    val wordForms: List<WordForm>,
    val sentences: List<Sentence>,
)

data class WordFilter(
    val language: String? = null,
    val query: String? = null,
)
