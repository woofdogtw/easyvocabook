package tw.idv.woofdog.easyvocabook

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.idv.woofdog.easyvocabook.data.db.DbTableMemory
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.data.model.WordEntry
import java.io.File

class AppRepository(context: Context) {

    val sqlite = DbTableSQLite(context, DbTableSQLite.dbFile(context))
    val memory = DbTableMemory()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        memory.loadAll(sqlite)
    }

    // Write-through: commit to SQLite, update memory, then sync bookInfo so
    // SyncOrchestrator.doSync() sees the correct last_modified for upload decisions.
    suspend fun createWord(data: WordEntry): Long {
        val id = sqlite.createWord(data)
        val stored = sqlite.getWord(id) ?: data.copy(id = id)
        memory.addWord(stored)
        memory.updateBookInfo(sqlite.getBookInfo())
        return id
    }

    suspend fun updateWord(id: Long, data: WordEntry) {
        sqlite.updateWord(id, data)
        val stored = sqlite.getWord(id) ?: data.copy(id = id)
        memory.replaceWord(stored)
        memory.updateBookInfo(sqlite.getBookInfo())
    }

    suspend fun deleteWord(id: Long) {
        sqlite.deleteWord(id)
        memory.removeWord(id)
        memory.updateBookInfo(sqlite.getBookInfo())
    }

    suspend fun clearPracticeStats() {
        sqlite.clearPracticeStats()
        memory.clearPracticeStats()
        memory.updateBookInfo(sqlite.getBookInfo())
    }

    suspend fun updatePracticeStats(wordId: Long, correct: Boolean) {
        val now = System.currentTimeMillis() / 1000
        sqlite.updatePracticeStats(wordId, correct, now)
        memory.updatePracticeStats(wordId, correct, now)
        memory.updateBookInfo(sqlite.getBookInfo())
    }

    // Used by sync: atomically replace the live DB file, then reload memory
    suspend fun reloadAfterSync(context: Context, newDbFile: File) = withContext(Dispatchers.IO) {
        sqlite.close()
        val dest = DbTableSQLite.dbFile(context)
        try {
            java.nio.file.Files.move(
                newDbFile.toPath(), dest.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(newDbFile.toPath(), dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        memory.loadAll(sqlite)
    }

    companion object {
        @Volatile private var instance: AppRepository? = null

        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext).also { instance = it }
            }

        fun resetForTest() {
            instance?.sqlite?.close()
            instance = null
        }
    }
}
