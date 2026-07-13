package tw.idv.woofdog.easyvocabook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.data.model.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DbTableSQLiteTest {

    private lateinit var db: DbTableSQLite
    private lateinit var dbFile: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "test_${System.nanoTime()}.db")
        db = DbTableSQLite(context, dbFile)
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    @Test
    fun freshDb_lastModifiedIsZero() = runTest {
        val info = db.getBookInfo()
        assertEquals(0L, info.lastModified)
    }

    @Test
    fun createWord_returnsNewId() = runTest {
        val id = db.createWord(sampleWord())
        assertTrue(id > 0)
    }

    @Test
    fun createWord_subRecordsInserted() = runTest {
        val id = db.createWord(sampleWord())
        val fetched = db.getWord(id)
        assertNotNull(fetched)
        assertEquals(1, fetched!!.wordMeanings.size)
        assertEquals(1, fetched.wordForms.size)
        assertEquals(1, fetched.sentences.size)
    }

    @Test
    fun updateWord_replacesSubRecords() = runTest {
        val id = db.createWord(sampleWord())
        val updated = sampleWord().copy(
            word = "run",
            wordMeanings = listOf(WordMeaning(0, "跑步"), WordMeaning(0, "運行")),
            wordForms = emptyList(),
            sentences = emptyList(),
        )
        db.updateWord(id, updated)
        val fetched = db.getWord(id)!!
        assertEquals("run", fetched.word)
        assertEquals(2, fetched.wordMeanings.size)
        assertTrue(fetched.wordForms.isEmpty())
    }

    @Test
    fun updateWord_preservesPracticeStats() = runTest {
        // Create with stats already set
        val id = db.createWord(sampleWord().copy(practiceCount = 7, correctCount = 5))
        // Edit the word text — must NOT reset stats
        val edited = sampleWord().copy(word = "run", practiceCount = 0, correctCount = 0)
        db.updateWord(id, edited)
        val fetched = db.getWord(id)!!
        assertEquals("run", fetched.word)
        assertEquals(7, fetched.practiceCount)
        assertEquals(5, fetched.correctCount)
    }

    @Test
    fun deleteWord_cascadesToSubTables() = runTest {
        val id = db.createWord(sampleWord())
        db.deleteWord(id)
        assertNull(db.getWord(id))
        val all = db.listWords()
        assertTrue(all.none { it.id == id })
    }

    @Test
    fun clearPracticeStats_resetsCounters() = runTest {
        val id = db.createWord(sampleWord().copy(practiceCount = 5, correctCount = 3))
        db.clearPracticeStats()
        val fetched = db.getWord(id)!!
        assertEquals(0, fetched.practiceCount)
        assertEquals(0, fetched.correctCount)
        assertNull(fetched.practicedAt)
    }

    @Test
    fun listWords_languageFilter() = runTest {
        db.createWord(sampleWord().copy(language = "en"))
        db.createWord(sampleWord().copy(word = "猫", language = "ja"))
        val en = db.listWords(WordFilter(language = "en"))
        val ja = db.listWords(WordFilter(language = "ja"))
        assertEquals(1, en.size)
        assertEquals(1, ja.size)
    }

    @Test
    fun listWords_textSearchCoversSecondaryMeanings() = runTest {
        val id = db.createWord(sampleWord().copy(
            wordMeanings = listOf(WordMeaning(0, "automobile"))
        ))
        val results = db.listWords(WordFilter(query = "automobile"))
        assertTrue(results.any { it.id == id })
    }

    @Test
    fun versionTooNew_throwsOnUpgrade() {
        val newerFile = File(context.cacheDir, "newer_${System.nanoTime()}.db")
        try {
            val older = DbTableSQLite(context, newerFile)
            older.writableDatabase
            older.close()

            assertThrows(IllegalStateException::class.java) {
                val tooNew = DbTableSQLite(context, newerFile)
                // Force onUpgrade by simulating old version — tested via direct call
                tooNew.onUpgrade(tooNew.writableDatabase, DbTableSQLite.CURRENT_VERSION + 1, DbTableSQLite.CURRENT_VERSION)
            }
        } finally {
            newerFile.delete()
        }
    }

    @Test
    fun updatePracticeStats_correctAnswer_incrementsBothCounters() = runTest {
        val id = db.createWord(sampleWord().copy(practiceCount = 2, correctCount = 1))
        db.updatePracticeStats(id, correct = true, practicedAt = 1_000_000L)
        val fetched = db.getWord(id)!!
        assertEquals(3, fetched.practiceCount)
        assertEquals(2, fetched.correctCount)
        assertEquals(1_000_000L, fetched.practicedAt)
        assertTrue(db.getBookInfo().lastModified > 0)
    }

    @Test
    fun updatePracticeStats_wrongAnswer_incrementsPracticeOnlyNotCorrect() = runTest {
        val id = db.createWord(sampleWord().copy(practiceCount = 1, correctCount = 1))
        db.updatePracticeStats(id, correct = false, practicedAt = 2_000_000L)
        val fetched = db.getWord(id)!!
        assertEquals(2, fetched.practiceCount)
        assertEquals(1, fetched.correctCount)   // unchanged
        assertEquals(2_000_000L, fetched.practicedAt)
    }

    @Test
    fun updatePracticeStats_doesNotTouchWordContent() = runTest {
        val id = db.createWord(sampleWord())
        db.updatePracticeStats(id, correct = true, practicedAt = 3_000_000L)
        val fetched = db.getWord(id)!!
        // Content fields must be untouched
        assertEquals("walk", fetched.word)
        assertEquals(1, fetched.wordMeanings.size)
        assertEquals(1, fetched.wordForms.size)
    }

    private fun sampleWord() = WordEntry(
        id = 0,
        word = "walk",
        reading = null,
        meaning = "走路",
        partOfSpeech = "verb",
        note = null,
        language = "en",
        practiceCount = 0,
        correctCount = 0,
        createdAt = System.currentTimeMillis() / 1000,
        practicedAt = null,
        wordMeanings = listOf(WordMeaning(0, "步行")),
        wordForms = listOf(WordForm(0, "past_tense", "walked")),
        sentences = listOf(Sentence(0, "I walk every day.", "我每天走路。")),
    )
}
