package tw.idv.woofdog.easyvocabook.data.db

import tw.idv.woofdog.easyvocabook.data.model.BookInfo
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import tw.idv.woofdog.easyvocabook.data.model.WordFilter

interface DbTableBase {
    suspend fun getBookInfo(): BookInfo
    suspend fun updateBookInfo(data: BookInfo)
    suspend fun listWords(filter: WordFilter = WordFilter()): List<WordEntry>
    suspend fun getWord(id: Long): WordEntry?
    suspend fun createWord(data: WordEntry): Long
    suspend fun updateWord(id: Long, data: WordEntry)
    suspend fun deleteWord(id: Long)
    suspend fun clearPracticeStats()
    suspend fun updatePracticeStats(wordId: Long, correct: Boolean, practicedAt: Long)
}
