package tw.idv.woofdog.easyvocabook

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.idv.woofdog.easyvocabook.data.model.*
import tw.idv.woofdog.easyvocabook.ui.quiz.QuizScreen
import tw.idv.woofdog.easyvocabook.ui.quiz.QuizViewModel
import tw.idv.woofdog.easyvocabook.ui.theme.EasyVocaBookTheme

@RunWith(RobolectricTestRunner::class)
class QuizScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Close and null the singleton first (closes the SQLite connection) so the file
        // can be deleted, then wipe the on-disk database to keep tests independent.
        AppRepository.resetForTest()
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        SQLiteDatabase.deleteDatabase(
            tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite.dbFile(ctx)
        )
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppRepository.resetForTest()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    private fun seedEnVerb() = runBlocking {
        val repo = AppRepository.get(app())
        repo.createWord(
            WordEntry(
                id = 0, word = "walk", reading = null,
                meaning = "to move on foot", partOfSpeech = "verb", note = null,
                language = "en", practiceCount = 0, correctCount = 0,
                createdAt = 0L, practicedAt = null,
                wordMeanings = emptyList(),
                wordForms = listOf(
                    WordForm(0, "base_form", "walk"),
                    WordForm(0, "past_tense", "walked"),
                    WordForm(0, "past_participle", "walked"),
                    WordForm(0, "gerund", "walking"),
                ),
                sentences = emptyList(),
            )
        )
    }

    /** Wait until a quiz card is visible — both card types show "Give Up". */
    private fun waitForCard() = composeTestRule.waitUntil(10_000) {
        composeTestRule.onAllNodesWithText("Give Up").fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * Skip until the quiz shows a typing card (identified by its label).
     * Each Skip click is a real UI interaction that advances the Compose frame,
     * so the StateFlow → Compose state loop is driven correctly.
     */
    private fun forceTypingCard() {
        val typingLabel = "Translate and fill in the word forms:"
        repeat(30) {
            waitForCard()
            if (composeTestRule.onAllNodesWithText(typingLabel)
                    .fetchSemanticsNodes().isNotEmpty()) return
            composeTestRule.onNodeWithContentDescription("Skip").performClick()
        }
        error("no TypingCard after 30 skips")
    }

    private fun forceMcqCard() {
        val mcqLabel = "Select all correct meanings:"
        repeat(30) {
            waitForCard()
            if (composeTestRule.onAllNodesWithText(mcqLabel)
                    .fetchSemanticsNodes().isNotEmpty()) return
            composeTestRule.onNodeWithContentDescription("Skip").performClick()
        }
        error("no McqCard after 30 skips")
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun emptyDb_showsEmptyState() {
        // Use an explicit vm so the ViewModel starts initializing before setContent,
        // reducing the chance of a race between IO init and Compose frame rendering.
        val vm = QuizViewModel(app())
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen(vm = vm) } }
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("No words to practice", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("No words to practice", substring = true).assertIsDisplayed()
    }

    @Test
    fun typingCard_enVerb_showsFourFormFields() {
        seedEnVerb()
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen() } }
        forceTypingCard()

        // The EN verb should produce field labels: word, base_form, past_tense, past_participle, gerund.
        // assertExists() rather than assertIsDisplayed() because the lower fields may be off-screen
        // on Robolectric's default 320×480dp layout (all 5 text fields exceed the viewport).
        val ctx = app()
        fun enterLabel(resId: Int) = ctx.getString(R.string.quiz_enter_form, ctx.getString(resId))
        composeTestRule.onNodeWithText(enterLabel(R.string.form_word)).assertExists()
        composeTestRule.onNodeWithText(enterLabel(R.string.form_base_form)).assertExists()
        composeTestRule.onNodeWithText(enterLabel(R.string.form_past_tense)).assertExists()
        composeTestRule.onNodeWithText(enterLabel(R.string.form_past_participle)).assertExists()
        composeTestRule.onNodeWithText(enterLabel(R.string.form_gerund)).assertExists()
    }

    @Test
    fun giveUp_showsWrongMarkersAndNextButton() {
        seedEnVerb()
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen() } }
        forceTypingCard()

        // Give Up button may be below the fold; scroll it into view before clicking.
        composeTestRule.onNodeWithText("Give Up").performScrollTo()
        composeTestRule.onNodeWithText("Give Up").performClick()

        // All fields were empty → every field result shows ✗.
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("✗").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithText("✗").onFirst().assertExists()
        composeTestRule.onNodeWithText("Next").assertExists()
    }

    @Test
    fun correctTyping_showsAllCheckmarksAndNoWrongMarkers() {
        seedEnVerb()
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen() } }
        forceTypingCard()

        // Scroll to each field before typing (fields may be off-screen on small test layout).
        val ctx = app()
        fun enterLabel(resId: Int) = ctx.getString(R.string.quiz_enter_form, ctx.getString(resId))
        fun typeInto(resId: Int, value: String) {
            val label = enterLabel(resId)
            val node = composeTestRule.onNode(hasSetTextAction() and hasText(label))
            node.performScrollTo()
            node.performTextInput(value)
        }
        typeInto(R.string.form_word, "walk")
        typeInto(R.string.form_base_form, "walk")
        typeInto(R.string.form_past_tense, "walked")
        typeInto(R.string.form_past_participle, "walked")
        typeInto(R.string.form_gerund, "walking")

        composeTestRule.onNodeWithText("Submit").performScrollTo()
        composeTestRule.onNodeWithText("Submit").performClick()

        // All fields correct → all ✓, no ✗.
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("✓").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithText("✗").fetchSemanticsNodes().size)
    }

    @Test
    fun skip_remainsOnCardWithoutRecordingStats() {
        seedEnVerb()
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen() } }
        waitForCard()

        composeTestRule.onNodeWithContentDescription("Skip").performClick()

        // After a skip the quiz must show another card, not a result view.
        waitForCard()
        assertEquals(0, composeTestRule.onAllNodesWithText("Next").fetchSemanticsNodes().size)

        // No practice stat must have been recorded.
        val repo = AppRepository.get(app())
        assertEquals(0, repo.memory.allWords().first().practiceCount)
    }

    @Test
    fun mcqCard_selectCorrectMeaning_showsCheckmark() {
        seedEnVerb()
        composeTestRule.setContent { EasyVocaBookTheme { QuizScreen() } }
        forceMcqCard()

        // The seeded word has one meaning: "to move on foot". Select it and submit.
        // Note: in McqCardView the Submit button is in a fixed Row below the LazyColumn,
        // so performScrollTo() must NOT be used (no scrollable parent → AssertionError).
        composeTestRule.onNodeWithText("to move on foot").performClick()
        composeTestRule.onNodeWithText("Submit").performClick()

        // Result screen shows ✓ for the correct meaning and the Next button.
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("✓").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Next").assertExists()
    }
}
