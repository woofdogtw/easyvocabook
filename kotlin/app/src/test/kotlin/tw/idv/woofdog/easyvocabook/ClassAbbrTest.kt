package tw.idv.woofdog.easyvocabook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.idv.woofdog.easyvocabook.ui.Labels

/**
 * Guards the word list's Class badge strings.
 *
 * Every locale is exercised explicitly. Checking only the default would miss exactly what this
 * protects: during `word-form-reading`, 32 zh-CN strings silently fell back to Traditional Chinese
 * and nothing reported it.
 */
@RunWith(RobolectricTestRunner::class)
class ClassAbbrTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun allAbbrs(): List<Pair<String, String>> {
        val ctx = context()
        val out = mutableListOf<Pair<String, String>>()
        for (key in Labels.TRANSITIVITY_KEYS) {
            val id = Labels.transitivityAbbrResId(key)
            assertNotNull("transitivity $key has no abbreviation resource", id)
            out += "transitivity.$key" to ctx.getString(id!!)
        }
        for ((language, list) in listOf("ja" to Labels.JA_POS, "en" to Labels.EN_POS)) {
            for (pos in list) {
                val id = Labels.classAbbrResId(language, pos)
                assertNotNull("$language/$pos has no abbreviation resource", id)
                out += "$language.$pos" to ctx.getString(id!!)
            }
        }
        return out
    }

    private fun assertCoveredAndUnique() {
        val abbrs = allAbbrs()
        for ((key, value) in abbrs) {
            assertTrue("$key resolves to an empty abbreviation", value.isNotBlank())
        }
        // The badge draws from the transitivity and part-of-speech namespaces without saying
        // which, so a repeated value would mean two things on two rows.
        val seen = mutableMapOf<String, String>()
        for ((key, value) in abbrs) {
            val prev = seen.put(value, key)
            assertNull("$key and $prev both render \"$value\"", prev)
        }
    }

    @Test
    fun defaultLocaleCoversEveryClassAndKeepsThemUnique() = assertCoveredAndUnique()

    @Test
    @Config(qualifiers = "zh-rTW")
    fun traditionalChineseCoversEveryClassAndKeepsThemUnique() = assertCoveredAndUnique()

    @Test
    @Config(qualifiers = "zh-rCN")
    fun simplifiedChineseCoversEveryClassAndKeepsThemUnique() = assertCoveredAndUnique()

    /** Three abbreviations differ between the Chinese variants; the rest are shared. */
    @Test
    @Config(qualifiers = "zh-rCN")
    fun simplifiedChineseUsesSimplifiedForms() {
        val ctx = context()
        assertEquals("动", ctx.getString(Labels.classAbbrResId("ja", "verb")!!))
        assertEquals("连", ctx.getString(Labels.classAbbrResId("ja", "conjunction")!!))
        assertEquals("助动", ctx.getString(Labels.classAbbrResId("ja", "aux-verb")!!))
    }

    /**
     * Under an English interface a Japanese word keeps the unsimplified forms. That is Japanese
     * orthography — 動詞, 助動詞 — not a copy of the Traditional Chinese table.
     */
    @Test
    @Config(qualifiers = "en")
    fun englishInterfaceKeepsJapaneseOrthographyForJapaneseWords() {
        val ctx = context()
        assertEquals("動", ctx.getString(Labels.classAbbrResId("ja", "verb")!!))
        assertEquals("助動", ctx.getString(Labels.classAbbrResId("ja", "aux-verb")!!))
        // An English word is Latin in every locale.
        assertEquals("V", ctx.getString(Labels.classAbbrResId("en", "verb")!!))
    }

    /** The badge script follows the word, not the interface. */
    @Test
    @Config(qualifiers = "zh-rTW")
    fun scriptFollowsTheWordNotTheInterface() {
        val ctx = context()
        assertEquals("名", ctx.getString(Labels.classAbbrResId("ja", "noun")!!))
        assertEquals("N", ctx.getString(Labels.classAbbrResId("en", "noun")!!))
    }
}
