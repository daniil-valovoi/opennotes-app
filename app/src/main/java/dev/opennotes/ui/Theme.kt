package dev.opennotes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00164E),
    secondary = Color(0xFF5B5D72),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFE4E1EC),
    background = Color(0xFFFCFCFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C4FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1240A8),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFC4C5DD),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF46464F),
    background = Color(0xFF121318),
)

@Composable
fun OpenNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
