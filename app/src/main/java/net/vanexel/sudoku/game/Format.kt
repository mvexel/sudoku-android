package net.vanexel.sudoku.game

import java.util.Locale
import net.vanexel.sudoku.engine.Difficulty

/** Player-facing level name: "Hard", "Expert", "Extreme". */
fun Difficulty.label(): String = when (this) {
    Difficulty.HARD -> "Hard"
    Difficulty.EXPERT -> "Expert"
    Difficulty.EXTREME -> "Extreme"
}

/** Technique blurb shown in the home summary line. */
fun Difficulty.blurb(): String = when (this) {
    Difficulty.HARD -> "X-wings, hidden pairs, triples"
    Difficulty.EXPERT -> "Swordfish, XY/XYZ-wings, jellyfish"
    Difficulty.EXTREME -> "Chains, coloring, W-wings, UR"
}

/** "Expert #17" for 0-based [index] 16. */
fun puzzleTitle(level: Difficulty, index: Int): String = "${level.label()} #${index + 1}"

/** "04:12", or "1:04:12" past an hour. */
fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

/** "4 minutes 12 seconds", "1 hour 2 minutes 0 seconds". */
fun spokenElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun unit(n: Long, name: String) = "$n $name${if (n == 1L) "" else "s"}"
    val parts = mutableListOf<String>()
    if (h > 0) parts.add(unit(h, "hour"))
    if (h > 0 || m > 0) parts.add(unit(m, "minute"))
    parts.add(unit(s, "second"))
    return parts.joinToString(" ")
}
