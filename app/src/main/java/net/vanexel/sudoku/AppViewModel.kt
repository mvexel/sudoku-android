package net.vanexel.sudoku

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.vanexel.sudoku.data.PuzzleProgress
import net.vanexel.sudoku.data.Settings
import net.vanexel.sudoku.engine.Difficulty

sealed interface Screen {
    data object Home : Screen
    data object Game : Screen
}

enum class PuzzleFilter(val label: String) {
    ALL("All"),
    UNSOLVED("Unsolved"),
    IN_PROGRESS("In progress"),
    SOLVED("Solved"),
}

/** A puzzle to open: level and 0-based bank index. */
data class PuzzleRef(val level: Difficulty, val index: Int)

/** Navigation, home-screen state, and access to the shared stores. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val services = (app as SudokuApplication).services

    val settings: StateFlow<Settings> = services.settings.settings
    val progress: StateFlow<Map<String, PuzzleProgress>> = services.progress.progress

    private val _bankIds = MutableStateFlow<Map<Difficulty, List<String>>>(emptyMap())
    /** Puzzle ids per level, in bank order. A level is absent until its bank is loaded. */
    val bankIds: StateFlow<Map<Difficulty, List<String>>> = _bankIds.asStateFlow()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var selectedTab by mutableStateOf(services.settings.lastTab)
        private set

    var filter by mutableStateOf(PuzzleFilter.ALL)
        private set

    init {
        viewModelScope.launch {
            services.progress.load()
            // Selected tab first, so the visible grid fills as early as possible.
            val order = listOf(selectedTab) + Difficulty.values().filter { it != selectedTab }
            for (level in order) {
                val ids = services.banks.ids(level)
                _bankIds.update { it + (level to ids) }
            }
        }
    }

    fun selectTab(level: Difficulty) {
        selectedTab = level
        services.settings.lastTab = level
    }

    fun selectFilter(f: PuzzleFilter) {
        filter = f
    }

    fun updateSettings(s: Settings) = services.settings.update(s)

    fun showGame() {
        screen = Screen.Game
    }

    fun showHome(tab: Difficulty? = null) {
        if (tab != null) selectTab(tab)
        screen = Screen.Home
    }

    /** The next puzzle after [index] at [level] that is not solved (wrapping around), or null. */
    fun nextUnsolved(level: Difficulty, index: Int): PuzzleRef? {
        val ids = _bankIds.value[level] ?: return null
        val current = progress.value
        for (step in 1 until ids.size) {
            val i = (index + step) % ids.size
            if (current[ids[i]]?.solved != true) return PuzzleRef(level, i)
        }
        return null
    }
}

/** The most recently played puzzle with a saved game, or null. */
fun continueTarget(
    progress: Map<String, PuzzleProgress>,
    bankIds: Map<Difficulty, List<String>>,
): PuzzleRef? {
    var best: PuzzleRef? = null
    var bestTime = Long.MIN_VALUE
    for ((id, p) in progress) {
        if (!p.hasSavedGame || p.lastPlayed < bestTime) continue
        val index = bankIds[p.level]?.indexOf(id) ?: -1
        if (index < 0) continue
        best = PuzzleRef(p.level, index)
        bestTime = p.lastPlayed
    }
    return best
}
