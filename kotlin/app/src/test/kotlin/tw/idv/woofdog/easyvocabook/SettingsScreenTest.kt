package tw.idv.woofdog.easyvocabook

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.idv.woofdog.easyvocabook.BuildConfig
import tw.idv.woofdog.easyvocabook.data.db.DbTableSQLite
import tw.idv.woofdog.easyvocabook.ui.settings.SettingsScreen
import tw.idv.woofdog.easyvocabook.ui.theme.EasyVocaBookTheme

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        AppRepository.resetForTest()
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        SQLiteDatabase.deleteDatabase(DbTableSQLite.dbFile(ctx))
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppRepository.resetForTest()
    }

    @Test
    fun syncDisabled_syncNowButton_isDisabled() {
        composeTestRule.setContent {
            EasyVocaBookTheme { SettingsScreen() }
        }
        // Default sync method is DISABLED
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithText("Sync Now").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Sync Now").assertIsNotEnabled()
    }

    @Test
    fun aboutSection_showsVersionName() {
        composeTestRule.setContent {
            EasyVocaBookTheme { SettingsScreen() }
        }
        // Wait for settings list to render (Sync Now is near the top)
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithText("Sync Now").fetchSemanticsNodes().isNotEmpty()
        }
        // Scroll the LazyColumn to bring the version text into view
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(BuildConfig.VERSION_NAME))
        composeTestRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
    }

    @Test
    fun clearStats_confirmDialog_cancelsOnCancel() {
        composeTestRule.setContent {
            EasyVocaBookTheme { SettingsScreen() }
        }
        // Scroll to the Practice section so the button is well within the viewport before clicking.
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Clear Practice Statistics"))
        composeTestRule.onNodeWithText("Clear Practice Statistics").performClick()

        // After the click the LazyColumn item grows (button → text + two buttons). The item may
        // scroll out of view. Ask the LazyColumn to scroll back to bring it into the viewport.
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Clear all practice statistics", substring = true))
        composeTestRule.onNodeWithText("Clear all practice statistics?", substring = true).assertIsDisplayed()

        // Cancel dismisses the confirmation
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Clear Practice Statistics"))
        composeTestRule.onNodeWithText("Clear Practice Statistics").assertIsDisplayed()
    }
}
