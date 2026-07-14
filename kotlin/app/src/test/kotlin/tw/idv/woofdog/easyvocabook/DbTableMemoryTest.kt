package tw.idv.woofdog.easyvocabook

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tw.idv.woofdog.easyvocabook.data.db.DbTableMemory
import tw.idv.woofdog.easyvocabook.data.model.*

class DbTableMemoryTest {

    private lateinit var mem: DbTableMemory

    @Before
    fun setUp() {
        mem = DbTableMemory()
        mem.addWord(WordEntry(1L, "walk", null, "走路", "verb", null, "en", 0, 0, 0L, null,
            listOf(WordMeaning(1, "步行")), emptyList(), emptyList()))
        mem.addWord(WordEntry(2L, "猫", "ねこ", "貓", "noun", null, "ja", 2, 1, 0L, 1000L,
            emptyList(), emptyList(), emptyList()))
        mem.addWord(WordEntry(3L, "run", null, "跑", "verb", null, "en", 0, 0, 0L, null,
            listOf(WordMeaning(2, "automobile")), emptyList(), emptyList()))
    }

    @Test
    fun listWords_noFilter_returnsAll() = runTest {
        val result = mem.listWords()
        assertEquals(3, result.size)
    }

    @Test
    fun listWords_languageFilter_returnsOnlyMatching() = runTest {
        val en = mem.listWords(WordFilter(language = "en"))
        assertEquals(2, en.size)
        assertTrue(en.all { it.language == "en" })
        val ja = mem.listWords(WordFilter(language = "ja"))
        assertEquals(1, ja.size)
    }

    @Test
    fun listWords_textSearch_matchesPrimaryMeaning() = runTest {
        val result = mem.listWords(WordFilter(query = "跑"))
        assertEquals(1, result.size)
        assertEquals(3L, result[0].id)
    }

    @Test
    fun listWords_textSearch_matchesSecondaryMeaning() = runTest {
        val result = mem.listWords(WordFilter(query = "automobile"))
        assertEquals(1, result.size)
        assertEquals(3L, result[0].id)
    }

    @Test
    fun listWords_textSearch_matchesWord() = runTest {
        val result = mem.listWords(WordFilter(query = "walk"))
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun addWord_visibleInList() = runTest {
        mem.addWord(WordEntry(4L, "cat", null, "貓咪", "noun", null, "en", 0, 0, 0L, null,
            emptyList(), emptyList(), emptyList()))
        assertEquals(4, mem.listWords().size)
    }

    @Test
    fun replaceWord_updatesEntry() = runTest {
        mem.replaceWord(WordEntry(1L, "WALK", null, "Walking", "verb", null, "en", 0, 0, 0L, null,
            emptyList(), emptyList(), emptyList()))
        val updated = mem.getWord(1L)!!
        assertEquals("WALK", updated.word)
    }

    @Test
    fun removeWord_notVisibleInList() = runTest {
        mem.removeWord(2L)
        val result = mem.listWords()
        assertEquals(2, result.size)
        assertTrue(result.none { it.id == 2L })
    }

    @Test
    fun clearPracticeStats_resetsCounters() = runTest {
        mem.clearPracticeStats()
        val all = mem.listWords()
        assertTrue(all.all { it.practiceCount == 0 && it.correctCount == 0 && it.practicedAt == null })
    }
}
