package tw.idv.woofdog.easyvocabook.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.idv.woofdog.easyvocabook.data.model.BookInfo
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter

class DbTableMemory : DbTableBase {

    private var bookInfo: BookInfo = BookInfo("", null, "en", 1, 0)
    private val words = mutableListOf<WordEntry>()

    suspend fun loadAll(sqlite: DbTableSQLite) = withContext(Dispatchers.IO) {
        bookInfo = sqlite.getBookInfo()
        words.clear()
        words.addAll(sqlite.listWords())
    }

    override suspend fun getBookInfo(): BookInfo = bookInfo

    override suspend fun updateBookInfo(data: BookInfo) {
        bookInfo = data
    }

    override suspend fun listWords(filter: WordFilter): List<WordEntry> {
        var result = words.toList()
        if (filter.language != null) result = result.filter { it.language == filter.language }
        if (!filter.query.isNullOrBlank()) {
            val q = filter.query.lowercase()
            result = result.filter { w ->
                w.word.contains(q, ignoreCase = true) ||
                w.reading?.contains(q, ignoreCase = true) == true ||
                w.meaning.contains(q, ignoreCase = true) ||
                w.wordMeanings.any { it.meaning.contains(q, ignoreCase = true) }
            }
        }
        return result
    }

    override suspend fun getWord(id: Long): WordEntry? = words.find { it.id == id }

    override suspend fun createWord(data: WordEntry): Long {
        val id = (words.maxOfOrNull { it.id } ?: 0L) + 1L
        words.add(data.copy(id = id))
        return id
    }

    override suspend fun updateWord(id: Long, data: WordEntry) {
        val idx = words.indexOfFirst { it.id == id }
        if (idx >= 0) words[idx] = data.copy(id = id)
    }

    override suspend fun deleteWord(id: Long) {
        words.removeAll { it.id == id }
    }

    override suspend fun clearPracticeStats() {
        val cleared = words.map { it.copy(practiceCount = 0, correctCount = 0, practicedAt = null) }
        words.clear()
        words.addAll(cleared)
    }

    override suspend fun updatePracticeStats(wordId: Long, correct: Boolean, practicedAt: Long) {
        val idx = words.indexOfFirst { it.id == wordId }
        if (idx >= 0) {
            val w = words[idx]
            words[idx] = w.copy(
                practiceCount = w.practiceCount + 1,
                correctCount = if (correct) w.correctCount + 1 else w.correctCount,
                practicedAt = practicedAt,
            )
        }
    }

    // Incremental updates (called after SQLite write succeeds)
    fun addWord(entry: WordEntry) { words.add(entry) }
    fun replaceWord(entry: WordEntry) {
        val idx = words.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            val existing = words[idx]
            // Preserve stats that the editor must not overwrite
            words[idx] = entry.copy(
                practiceCount = existing.practiceCount,
                correctCount = existing.correctCount,
                createdAt = existing.createdAt,
                practicedAt = existing.practicedAt,
            )
        } else {
            words.add(entry)
        }
    }
    fun removeWord(id: Long) { words.removeAll { it.id == id } }
    fun allWords(): List<WordEntry> = words.toList()
}
