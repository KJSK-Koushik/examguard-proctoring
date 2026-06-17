package com.examguard.proctoring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ExamGuard dark palette (matches the Python Tkinter theme).
val BgDark = Color(0xFF0D1117)
val BgPanel = Color(0xFF161B22)
val BgCard = Color(0xFF21262D)
val AccentBlue = Color(0xFF58A6FF)
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)
val BorderColor = Color(0xFF30363D)

private val ExamGuardColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgPanel,
    onSurface = TextPrimary,
    surfaceVariant = BgCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
)

@Composable
fun ExamGuardTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Always dark — proctoring UI is a fixed dark dashboard.
    MaterialTheme(
        colorScheme = ExamGuardColors,
        typography = Typography(),
        content = content,
    )
}
