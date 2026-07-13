package tw.idv.woofdog.easyvocabook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7B4EAB)
private val PurpleLight = Color(0xFFD0BCFF)
private val Teal = Color(0xFF00897B)
private val TealLight = Color(0xFF80CBC4)

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    secondary = TealLight,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF00504A),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
)

@Composable
fun EasyVocaBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
