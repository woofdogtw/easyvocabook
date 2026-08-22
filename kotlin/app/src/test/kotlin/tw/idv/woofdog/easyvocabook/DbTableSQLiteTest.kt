package tw.idv.woofdog.easyvocabook

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

    // ── v1 → v2 migration ─────────────────────────────────────────────────────

    /**
     * Write a version-1 database by hand: word_forms without the `reading` column.
     * [userVersion] models PRAGMA user_version, which the desktop app never sets — a file
     * synced from it arrives as 0 and must still be migrated off db_info.version.
     */
    private fun writeV1Database(file: File, userVersion: Int) {
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL(
            """CREATE TABLE db_info (
                id INTEGER PRIMARY KEY CHECK (id = 1), name TEXT NOT NULL, description TEXT,
                default_language TEXT NOT NULL DEFAULT 'en', version INTEGER NOT NULL,
                last_modified INTEGER NOT NULL)"""
        )
        raw.execSQL(
            """CREATE TABLE words (
                id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL, reading TEXT,
                meaning TEXT NOT NULL, part_of_speech TEXT, note TEXT, language TEXT NOT NULL,
                practice_count INTEGER NOT NULL DEFAULT 0, correct_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL, practiced_at INTEGER)"""
        )
        raw.execSQL(
            """CREATE TABLE word_meanings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                meaning TEXT NOT NULL, UNIQUE(word_id, meaning))"""
        )
        // v1 shape: no reading column
        raw.execSQL(
            """CREATE TABLE word_forms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                label TEXT NOT NULL, value TEXT NOT NULL)"""
        )
        raw.execSQL(
            """CREATE TABLE sentences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                sentence TEXT NOT NULL, translation TEXT)"""
        )
        raw.execSQL(
            "INSERT INTO db_info (id, name, description, default_language, version, last_modified) " +
                "VALUES (1, 'Book', NULL, 'en', 1, 0)"
        )
        raw.execSQL(
            "INSERT INTO words (id, word, meaning, language, created_at) VALUES (1, '食べる', '吃', 'ja', 0)"
        )
        // A legacy row whose label carried a reading — it must survive untouched.
        raw.execSQL("INSERT INTO word_forms (word_id, label, value) VALUES (1, 'hiragana', 'たべる')")
        raw.execSQL("INSERT INTO word_forms (word_id, label, value) VALUES (1, 'masu_form', '食べます')")
        raw.version = userVersion
        raw.close()
    }

    private fun readVersion(file: File): Int =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT version FROM db_info WHERE id = 1", null).use { c ->
                c.moveToFirst(); c.getInt(0)
            }
        }

    private fun columnNames(file: File, table: String): List<String> =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val idx = c.getColumnIndex("name")
                buildList { while (c.moveToNext()) add(c.getString(idx)) }
            }
        }

    @Test
    fun v1Database_migratesToV2WithRowsIntact() = runTest {
        val f = File(context.cacheDir, "v1_${System.nanoTime()}.db")
        try {
            writeV1Database(f, userVersion = 1)
            val migrated = DbTableSQLite(context, f)
            migrated.writableDatabase
            val word = migrated.getWord(1)
            migrated.close()

            assertEquals(2, readVersion(f))
            assertTrue("reading" in columnNames(f, "word_forms"))
            // Existing rows keep label and value, and gain a null reading.
            val forms = word!!.wordForms
            assertEquals(setOf("hiragana", "masu_form"), forms.map { it.label }.toSet())
            assertEquals("たべる", forms.first { it.label == "hiragana" }.value)
            assertNull(forms.first { it.label == "masu_form" }.reading)
        } finally {
            f.delete()
        }
    }

    @Test
    fun v1DatabaseFromDesktop_withZeroUserVersion_stillMigrates() = runTest {
        // The desktop app never writes PRAGMA user_version, so a synced file reports 0.
        // SQLiteOpenHelper would route this to onCreate (all no-ops); db_info.version must win.
        val f = File(context.cacheDir, "desktop_${System.nanoTime()}.db")
        try {
            writeV1Database(f, userVersion = 0)
            val migrated = DbTableSQLite(context, f)
            migrated.writableDatabase
            migrated.close()

            assertTrue("reading" in columnNames(f, "word_forms"))
            assertEquals(2, readVersion(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun migratedDatabase_reopensWithoutError() = runTest {
        val f = File(context.cacheDir, "reopen_${System.nanoTime()}.db")
        try {
            writeV1Database(f, userVersion = 0)
            DbTableSQLite(context, f).use { it.writableDatabase }
            // Second open must not re-run ALTER TABLE (duplicate column name).
            val again = DbTableSQLite(context, f)
            again.writableDatabase
            val word = again.getWord(1)
            again.close()
            assertEquals(2, readVersion(f))
            assertEquals(2, word!!.wordForms.size)
        } finally {
            f.delete()
        }
    }

    // ── word_form readings ────────────────────────────────────────────────────

    @Test
    fun wordFormReading_roundTrips() = runTest {
        val id = db.createWord(
            sampleWord().copy(wordForms = listOf(WordForm(0, "masu_form", "食べます", "たべます")))
        )
        val form = db.getWord(id)!!.wordForms.single()
        assertEquals("食べます", form.value)
        assertEquals("たべます", form.reading)
    }

    @Test
    fun wordFormWithoutReading_returnsNull() = runTest {
        val id = db.createWord(sampleWord().copy(wordForms = listOf(WordForm(0, "past_tense", "walked"))))
        assertNull(db.getWord(id)!!.wordForms.single().reading)
    }

    @Test
    fun blankReadingNormalizesToAbsent() = runTest {
        val id = db.createWord(
            sampleWord().copy(wordForms = listOf(WordForm(0, "past_tense", "walked", "   ")))
        )
        assertNull(db.getWord(id)!!.wordForms.single().reading)
    }

    @Test
    fun formValueIsTrimmedOnSave() = runTest {
        val id = db.createWord(
            sampleWord().copy(wordForms = listOf(WordForm(0, "past_tense", "  walked  ", "  wɔːkt  ")))
        )
        val form = db.getWord(id)!!.wordForms.single()
        assertEquals("walked", form.value)
        assertEquals("wɔːkt", form.reading)
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
