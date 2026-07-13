package tw.idv.woofdog.easyvocabook.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.idv.woofdog.easyvocabook.data.model.*
import java.io.File

class DbTableSQLite(context: Context, dbFile: File) : SQLiteOpenHelper(
    context, dbFile.absolutePath, null, CURRENT_VERSION
), DbTableBase {

    init {
        // Disable WAL so no sidecar files (.wal/.shm) are created — required for atomic file swap
        setWriteAheadLoggingEnabled(false)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS db_info (
                id               INTEGER PRIMARY KEY CHECK (id = 1),
                name             TEXT    NOT NULL,
                description      TEXT,
                default_language TEXT    NOT NULL DEFAULT 'en',
                version          INTEGER NOT NULL,
                last_modified    INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS words (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                word           TEXT    NOT NULL,
                reading        TEXT,
                meaning        TEXT    NOT NULL,
                part_of_speech TEXT,
                note           TEXT,
                language       TEXT    NOT NULL,
                practice_count INTEGER NOT NULL DEFAULT 0,
                correct_count  INTEGER NOT NULL DEFAULT 0,
                created_at     INTEGER NOT NULL,
                practiced_at   INTEGER
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS word_meanings (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                meaning TEXT    NOT NULL,
                UNIQUE(word_id, meaning)
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS word_forms (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                label   TEXT    NOT NULL,
                value   TEXT    NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS sentences (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                word_id     INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
                sentence    TEXT    NOT NULL,
                translation TEXT
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_language_reading ON words(language, reading)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_meanings_word_id  ON word_meanings(word_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_forms_word_id     ON word_forms(word_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sentences_word_id      ON sentences(word_id)")

        db.execSQL(
            """INSERT OR IGNORE INTO db_info (id, name, description, default_language, version, last_modified)
               VALUES (1, 'My Vocabulary Book', NULL, 'en', $CURRENT_VERSION, 0)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion > CURRENT_VERSION) {
            throw IllegalStateException("Database version $oldVersion is too new (app supports $CURRENT_VERSION). Please update the app.")
        }
        // Sequential migrations: run v(oldVersion+1) through v(newVersion)
        // No migrations needed yet (only version 1 exists)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
        // journal_mode returns a row, so rawQuery is required (execSQL would throw under Robolectric)
        db.rawQuery("PRAGMA journal_mode = DELETE", null).close()
    }

    override suspend fun getBookInfo(): BookInfo = withContext(Dispatchers.IO) {
        readableDatabase.use { db ->
            db.rawQuery("SELECT name, description, default_language, version, last_modified FROM db_info WHERE id = 1", null)
                .use { c ->
                    c.moveToFirst()
                    BookInfo(
                        name = c.getString(0),
                        description = if (c.isNull(1)) null else c.getString(1),
                        defaultLanguage = c.getString(2),
                        version = c.getInt(3),
                        lastModified = c.getLong(4),
                    )
                }
        }
    }

    override suspend fun updateBookInfo(data: BookInfo) = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            db.update("db_info", ContentValues().apply {
                put("name", data.name)
                put("description", data.description)
                put("default_language", data.defaultLanguage)
                put("last_modified", System.currentTimeMillis() / 1000)
            }, "id = 1", null)
        }
        Unit
    }

    override suspend fun listWords(filter: WordFilter): List<WordEntry> = withContext(Dispatchers.IO) {
        val db = readableDatabase

        val whereClause = buildString {
            val parts = mutableListOf<String>()
            if (filter.language != null) parts.add("w.language = ?")
            if (filter.query != null) parts.add(
                "(w.word LIKE ? OR w.reading LIKE ? OR w.meaning LIKE ? OR EXISTS (SELECT 1 FROM word_meanings wm WHERE wm.word_id = w.id AND wm.meaning LIKE ?))"
            )
            if (parts.isNotEmpty()) append("WHERE ").append(parts.joinToString(" AND "))
        }
        val args = buildList {
            if (filter.language != null) add(filter.language)
            if (filter.query != null) {
                val q = "%${filter.query}%"
                add(q); add(q); add(q); add(q)
            }
        }.toTypedArray()

        val words = mutableMapOf<Long, WordEntry>()
        val wordIds = mutableListOf<Long>()
        db.rawQuery("SELECT id, word, reading, meaning, part_of_speech, note, language, practice_count, correct_count, created_at, practiced_at FROM words w $whereClause ORDER BY w.word ASC", args)
            .use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    wordIds.add(id)
                    words[id] = WordEntry(
                        id = id,
                        word = c.getString(1),
                        reading = if (c.isNull(2)) null else c.getString(2),
                        meaning = c.getString(3),
                        partOfSpeech = if (c.isNull(4)) null else c.getString(4),
                        note = if (c.isNull(5)) null else c.getString(5),
                        language = c.getString(6),
                        practiceCount = c.getInt(7),
                        correctCount = c.getInt(8),
                        createdAt = c.getLong(9),
                        practicedAt = if (c.isNull(10)) null else c.getLong(10),
                        wordMeanings = emptyList(),
                        wordForms = emptyList(),
                        sentences = emptyList(),
                    )
                }
            }
        if (wordIds.isEmpty()) return@withContext emptyList()

        val idIn = wordIds.joinToString(",")
        val meaningsMap = mutableMapOf<Long, MutableList<WordMeaning>>()
        db.rawQuery("SELECT id, word_id, meaning FROM word_meanings WHERE word_id IN ($idIn)", null)
            .use { c ->
                while (c.moveToNext()) {
                    val wid = c.getLong(1)
                    meaningsMap.getOrPut(wid) { mutableListOf() }.add(WordMeaning(c.getLong(0), c.getString(2)))
                }
            }
        val formsMap = mutableMapOf<Long, MutableList<WordForm>>()
        db.rawQuery("SELECT id, word_id, label, value FROM word_forms WHERE word_id IN ($idIn)", null)
            .use { c ->
                while (c.moveToNext()) {
                    val wid = c.getLong(1)
                    formsMap.getOrPut(wid) { mutableListOf() }.add(WordForm(c.getLong(0), c.getString(2), c.getString(3)))
                }
            }
        val sentencesMap = mutableMapOf<Long, MutableList<Sentence>>()
        db.rawQuery("SELECT id, word_id, sentence, translation FROM sentences WHERE word_id IN ($idIn)", null)
            .use { c ->
                while (c.moveToNext()) {
                    val wid = c.getLong(1)
                    sentencesMap.getOrPut(wid) { mutableListOf() }.add(
                        Sentence(c.getLong(0), c.getString(2), if (c.isNull(3)) null else c.getString(3))
                    )
                }
            }

        wordIds.mapNotNull { id ->
            words[id]?.copy(
                wordMeanings = meaningsMap[id] ?: emptyList(),
                wordForms = formsMap[id] ?: emptyList(),
                sentences = sentencesMap[id] ?: emptyList(),
            )
        }
    }

    override suspend fun getWord(id: Long): WordEntry? = withContext(Dispatchers.IO) {
        listWords().find { it.id == id }
    }

    override suspend fun createWord(data: WordEntry): Long = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                val id = db.insert("words", null, wordContentValues(data))
                insertSubRecords(db, id, data)
                bumpLastModified(db)
                db.setTransactionSuccessful()
                id
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun updateWord(id: Long, data: WordEntry) = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.update("words", editContentValues(data), "id = ?", arrayOf(id.toString()))
                db.delete("word_meanings", "word_id = ?", arrayOf(id.toString()))
                db.delete("word_forms", "word_id = ?", arrayOf(id.toString()))
                db.delete("sentences", "word_id = ?", arrayOf(id.toString()))
                insertSubRecords(db, id, data)
                bumpLastModified(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        Unit
    }

    override suspend fun deleteWord(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.delete("words", "id = ?", arrayOf(id.toString()))
                bumpLastModified(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        Unit
    }

    override suspend fun clearPracticeStats() = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE words SET practice_count = 0, correct_count = 0, practiced_at = NULL")
                bumpLastModified(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        Unit
    }

    override suspend fun updatePracticeStats(wordId: Long, correct: Boolean, practicedAt: Long) =
        withContext(Dispatchers.IO) {
            writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    db.execSQL(
                        "UPDATE words SET practice_count = practice_count + 1, " +
                        "correct_count = correct_count + ?, practiced_at = ? WHERE id = ?",
                        arrayOf(if (correct) 1 else 0, practicedAt, wordId)
                    )
                    bumpLastModified(db)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            Unit
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun wordContentValues(data: WordEntry) = ContentValues().apply {
        put("word", data.word)
        put("reading", data.reading)
        put("meaning", data.meaning)
        put("part_of_speech", data.partOfSpeech)
        put("note", data.note)
        put("language", data.language)
        put("practice_count", data.practiceCount)
        put("correct_count", data.correctCount)
        put("created_at", data.createdAt)
        put("practiced_at", data.practicedAt)
    }

    // Only the user-editable fields; never touches practice_count/correct_count/created_at/practiced_at
    private fun editContentValues(data: WordEntry) = ContentValues().apply {
        put("word", data.word)
        put("reading", data.reading)
        put("meaning", data.meaning)
        put("part_of_speech", data.partOfSpeech)
        put("note", data.note)
        put("language", data.language)
    }

    private fun insertSubRecords(db: SQLiteDatabase, wordId: Long, data: WordEntry) {
        for (m in data.wordMeanings) {
            db.insertWithOnConflict("word_meanings", null, ContentValues().apply {
                put("word_id", wordId)
                put("meaning", m.meaning)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        for (f in data.wordForms) {
            db.insert("word_forms", null, ContentValues().apply {
                put("word_id", wordId)
                put("label", f.label)
                put("value", f.value)
            })
        }
        for (s in data.sentences) {
            db.insert("sentences", null, ContentValues().apply {
                put("word_id", wordId)
                put("sentence", s.sentence)
                put("translation", s.translation)
            })
        }
    }

    private fun bumpLastModified(db: SQLiteDatabase) {
        db.execSQL("UPDATE db_info SET last_modified = ? WHERE id = 1",
            arrayOf(System.currentTimeMillis() / 1000))
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun dbFile(context: Context): File = File(context.filesDir, "easyvocabook.db")
    }
}
