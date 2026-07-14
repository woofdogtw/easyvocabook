package tw.idv.woofdog.easyvocabook

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import tw.idv.woofdog.easyvocabook.ui.quiz.QuizScreen
import tw.idv.woofdog.easyvocabook.ui.settings.SettingsScreen
import tw.idv.woofdog.easyvocabook.ui.theme.EasyVocaBookTheme
import tw.idv.woofdog.easyvocabook.ui.wordlist.WordListScreen

private const val ROUTE_QUIZ = "quiz"
private const val ROUTE_WORDS = "wordlist"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun EasyVocaBookApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    val theme = prefs.getString(MainActivity.SP_THEME, "auto") ?: "auto"
    val darkTheme = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    EasyVocaBookTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val currentEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentEntry?.destination?.route ?: ROUTE_QUIZ

        val items = listOf(
            Triple(ROUTE_QUIZ, R.string.tab_quiz, Icons.Default.Star),
            Triple(ROUTE_WORDS, R.string.tab_word_list, Icons.Default.List),
            Triple(ROUTE_SETTINGS, R.string.tab_settings, Icons.Default.Settings),
        )

        Scaffold(
            bottomBar = {
                NavigationBar {
                    items.forEach { (route, labelRes, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(ROUTE_QUIZ) { saveState = true }
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_QUIZ,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(ROUTE_QUIZ) { QuizScreen() }
                composable(ROUTE_WORDS) { WordListScreen() }
                composable(ROUTE_SETTINGS) { SettingsScreen() }
            }
        }
    }
}
