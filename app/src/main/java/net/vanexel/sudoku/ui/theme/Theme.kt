package net.vanexel.sudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Board color tokens (DESIGN.md section 6). Composables read these, never hex values. */
@Immutable
data class SudokuColors(
    val boardBackground: Color,
    val gridLineThin: Color,
    val gridLineThick: Color,
    val givenDigit: Color,
    val userDigit: Color,
    val noteDigit: Color,
    val selectedCell: Color,
    val peerCell: Color,
    val sameDigitCell: Color,
    val conflictDigit: Color,
    val mistakeCell: Color,
    val accent: Color,
    val screenBackground: Color,
    val excludedCell: Color,
)

val LightSudokuColors = SudokuColors(
    boardBackground = Color(0xFFFFFFFF),
    gridLineThin = Color(0xFFA0A8B4),
    gridLineThick = Color(0xFF3C4452),
    givenDigit = Color(0xFF1F2933),
    userDigit = Color(0xFF2457C5),
    noteDigit = Color(0xFF525B69),
    selectedCell = Color(0xFFCFE0FF),
    peerCell = Color(0xFFF0F3F8),
    sameDigitCell = Color(0xFFE3EBFA),
    conflictDigit = Color(0xFFB3261E),
    mistakeCell = Color(0xFFFCE4E4),
    accent = Color(0xFF2457C5),
    screenBackground = Color(0xFFF6F7F9),
    excludedCell = Color(0xFFD5DAE1),
)

val DarkSudokuColors = SudokuColors(
    boardBackground = Color(0xFF1A1D23),
    gridLineThin = Color(0xFF5A6270),
    gridLineThick = Color(0xFF9AA3B2),
    givenDigit = Color(0xFFE8ECF2),
    userDigit = Color(0xFF8AB4FF),
    noteDigit = Color(0xFFA3ACBA),
    selectedCell = Color(0xFF263B61),
    peerCell = Color(0xFF232831),
    sameDigitCell = Color(0xFF2E3644),
    conflictDigit = Color(0xFFFF8A80),
    mistakeCell = Color(0xFF4A2326),
    accent = Color(0xFF8AB4FF),
    screenBackground = Color(0xFF121418),
    excludedCell = Color(0xFF363C47),
)

val LocalSudokuColors = staticCompositionLocalOf { LightSudokuColors }

// Chrome color schemes, seeded from the accent on the slate neutrals of the board palette.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2457C5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE0FF),
    onPrimaryContainer = Color(0xFF0B2A5C),
    secondary = Color(0xFF4F5D75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6FA),
    onSecondaryContainer = Color(0xFF1F2933),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF1F2933),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF1F2933),
    surfaceVariant = Color(0xFFE1E5EB),
    onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFF737B88),
    outlineVariant = Color(0xFFC9CED6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F5),
    surfaceContainer = Color(0xFFE9ECF1),
    surfaceContainerHigh = Color(0xFFE3E7ED),
    surfaceContainerHighest = Color(0xFFDDE2E9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF0B2A5C),
    primaryContainer = Color(0xFF263B61),
    onPrimaryContainer = Color(0xFFDCE6FA),
    secondary = Color(0xFFB4C0D6),
    onSecondary = Color(0xFF1F2933),
    secondaryContainer = Color(0xFF2E3A52),
    onSecondaryContainer = Color(0xFFE8ECF2),
    background = Color(0xFF121418),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF2E333C),
    onSurfaceVariant = Color(0xFFA3ACBA),
    outline = Color(0xFF8C95A3),
    outlineVariant = Color(0xFF3A404B),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A2326),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF1A1D23),
    surfaceContainer = Color(0xFF1E2228),
    surfaceContainerHigh = Color(0xFF262A32),
    surfaceContainerHighest = Color(0xFF30353E),
)

@Composable
fun SudokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkSudokuColors else LightSudokuColors
    CompositionLocalProvider(LocalSudokuColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}

object SudokuTheme {
    val colors: SudokuColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSudokuColors.current
}
