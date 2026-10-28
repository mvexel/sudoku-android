package net.vanexel.sudoku

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.vanexel.sudoku.data.SavedGame
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.engine.Puzzle
import net.vanexel.sudoku.game.GameState
import net.vanexel.sudoku.game.commitPending
import net.vanexel.sudoku.game.deselect
import net.vanexel.sudoku.game.enterDigit
import net.vanexel.sudoku.game.erase
import net.vanexel.sudoku.game.focusDigit
import net.vanexel.sudoku.game.hint
import net.vanexel.sudoku.game.select
import net.vanexel.sudoku.game.toggleNote
import net.vanexel.sudoku.game.toggleNotesMode
import net.vanexel.sudoku.game.toggleRuleOut
import net.vanexel.sudoku.game.undo

/** What a pad tap did, so the UI can pick the haptic and the TalkBack announcement. */
enum class DigitOutcome {
    NONE,
    /** A value was placed. */
    VALUE,
    /** A note was toggled (Notes mode or long-press). */
    NOTE,
    /** A rule-out mark was toggled (Rule-out mode). */
    RULE_OUT,
    /** The second tap turned the cell's value into a note. */
    CONVERTED_TO_NOTE,
    /** The digit was focused or unfocused (nothing selected). */
    FOCUS,
    /** Not allowed (a given is selected). */
    REJECTED,
}

data class WinInfo(
    val level: Difficulty,
    val index: Int,
    val technique: String,
    val timeMs: Long,
    val newBest: Boolean,
    val hintsUsed: Int,
    val mistakes: Int,
)

/**
 * Owns the current game: applies reducers, runs the timer (only while the screen is resumed and the
 * puzzle unsolved), autosaves (debounced 500ms after each change, and on stop/close), and handles the win.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val services = (app as SudokuApplication).services

    private val _state = MutableStateFlow<GameState?>(null)
    /** Null while a puzzle is loading. */
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _win = MutableStateFlow<WinInfo?>(null)
    val win: StateFlow<WinInfo?> = _win.asStateFlow()

    private var puzzle: Puzzle? = null
    private var resumed = false
    private var timerJob: Job? = null
    private var saveJob: Job? = null
    private var openJob: Job? = null

    /** Opens puzzle [index] of [level], resuming its saved game if there is one. */
    fun open(level: Difficulty, index: Int) {
        saveNow()
        openJob?.cancel()
        stopTimer()
        puzzle = null
        _state.value = null
        _win.value = null
        _elapsedMs.value = 0L
        openJob = viewModelScope.launch {
            val p = services.banks.puzzle(level, index)
            val base = GameState.newGame(p.id, level, index, p.givens.toList(), p.solution.toList())
            val saved = services.progress.loadGame(p.id)
            val restored = saved?.toState(base)
            if (saved != null && restored != null) {
                _elapsedMs.value = saved.elapsedMs
                _state.value = restored
            } else {
                services.progress.startNew(p.id, level)
                _state.value = base
            }
            puzzle = p
            updateTimer()
        }
    }

    /** Leaving the game screen: save now and stop the timer. */
    fun close() {
        saveNow()
        resumed = false
        updateTimer()
    }

    fun restart() {
        val p = puzzle ?: return
        val old = _state.value ?: return
        saveJob?.cancel()
        _win.value = null
        _elapsedMs.value = 0L
        _state.value = GameState.newGame(p.id, old.level, old.index, p.givens.toList(), p.solution.toList())
        viewModelScope.launch { services.progress.startNew(p.id, old.level) }
        updateTimer()
    }

    // ---- lifecycle ----

    fun onResume() {
        resumed = true
        updateTimer()
    }

    fun onPause() {
        resumed = false
        updateTimer()
        dispatch { it.commitPending() }
    }

    fun onStop() = saveNow()

    // ---- input ----

    /** Tap on a cell: selects it, or deselects it if it is already selected. */
    fun selectCell(cell: Int) {
        dispatch { it.select(cell) }
    }

    /** Tap outside the board. */
    fun deselect() {
        dispatch { it.deselect() }
    }

    /** True once: the first time a game opens with "Tap digit twice for note" on. */
    fun consumeEntryTip(): Boolean {
        val settings = services.settings
        if (settings.entryTipSeen || !settings.settings.value.tapTwiceForNote) return false
        settings.entryTipSeen = true
        return true
    }

    fun pressDigit(digit: Int): DigitOutcome {
        val st = _state.value ?: return DigitOutcome.NONE
        if (st.solved) return DigitOutcome.NONE
        return when {
            st.selected < 0 -> {
                dispatch { it.focusDigit(digit) }
                DigitOutcome.FOCUS
            }
            st.isGiven(st.selected) -> DigitOutcome.REJECTED
            st.ruleOutMode && services.settings.settings.value.ruleOutMarks -> toggleRuleOutOutcome(st, digit)
            st.notesMode -> toggleNoteOutcome(st, digit)
            else -> {
                val tapTwice = services.settings.settings.value.tapTwiceForNote
                if (!dispatch { it.enterDigit(digit, tapTwice) }) {
                    DigitOutcome.NONE
                } else if (_state.value?.values?.get(st.selected) == digit) {
                    DigitOutcome.VALUE
                } else {
                    DigitOutcome.CONVERTED_TO_NOTE
                }
            }
        }
    }

    /** Long-press on a pad key: toggles the note regardless of Notes mode. */
    fun longPressDigit(digit: Int): DigitOutcome {
        val st = _state.value ?: return DigitOutcome.NONE
        if (st.solved) return DigitOutcome.NONE
        if (st.selected < 0 || st.isGiven(st.selected)) return DigitOutcome.REJECTED
        return toggleNoteOutcome(st, digit)
    }

    /** Rule-out mark toggle from the TalkBack pad-key action, regardless of the entry mode. */
    fun ruleOutDigit(digit: Int): DigitOutcome {
        val st = _state.value ?: return DigitOutcome.NONE
        if (st.solved) return DigitOutcome.NONE
        if (st.selected < 0 || st.isGiven(st.selected)) return DigitOutcome.REJECTED
        return toggleRuleOutOutcome(st, digit)
    }

    private fun toggleRuleOutOutcome(st: GameState, digit: Int): DigitOutcome {
        dispatch { it.toggleRuleOut(digit) }
        val changed = _state.value?.ruledOut?.get(st.selected) != st.ruledOut[st.selected]
        return if (changed) DigitOutcome.RULE_OUT else DigitOutcome.NONE
    }

    /** NOTE only if the note really changed; on a provisional value the toggle merely commits it. */
    private fun toggleNoteOutcome(st: GameState, digit: Int): DigitOutcome {
        dispatch { it.toggleNote(digit) }
        val changed = _state.value?.notes?.get(st.selected) != st.notes[st.selected]
        return if (changed) DigitOutcome.NOTE else DigitOutcome.NONE
    }

    fun erase(): Boolean = dispatch { it.erase() }

    fun undo(): Boolean = dispatch { it.undo() }

    fun hint(): Boolean = dispatch { it.hint() }

    fun toggleNotesMode() {
        val allowRuleOut = services.settings.settings.value.ruleOutMarks
        dispatch { it.toggleNotesMode(allowRuleOut) }
    }

    /** Applies a reducer; returns true if the state changed. */
    private fun dispatch(reducer: (GameState) -> GameState): Boolean {
        val old = _state.value ?: return false
        val new = reducer(old)
        if (new == old) return false
        _state.value = new
        if (new.solved && !old.solved) {
            onSolved(new)
        } else {
            scheduleSave()
        }
        return true
    }

    // ---- timer ----

    private fun updateTimer() {
        val st = _state.value
        val shouldRun = resumed && st != null && !st.solved
        if (!shouldRun) {
            stopTimer()
        } else if (timerJob?.isActive != true) {
            timerJob = viewModelScope.launch {
                var last = SystemClock.elapsedRealtime()
                while (isActive) {
                    delay(1000L)
                    val now = SystemClock.elapsedRealtime()
                    _elapsedMs.value = _elapsedMs.value + (now - last)
                    last = now
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ---- saving ----

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500L)
            val st = _state.value ?: return@launch
            // A pending placement is saved as committed, without committing it in memory, so the
            // second tap of a tap-twice gesture still works after the debounced save.
            if (!st.solved) {
                services.progress.saveGame(st.puzzleId, st.level, SavedGame.of(st.commitPending(), _elapsedMs.value))
            }
        }
    }

    /** Saves immediately (state captured now; the write runs in call order with other store operations). */
    private fun saveNow() {
        saveJob?.cancel()
        saveJob = null
        val st = _state.value ?: return
        if (st.solved) return
        val saved = SavedGame.of(st.commitPending(), _elapsedMs.value)
        viewModelScope.launch { services.progress.saveGame(st.puzzleId, st.level, saved) }
    }

    // ---- win ----

    private fun onSolved(st: GameState) {
        saveJob?.cancel()
        updateTimer() // stops it
        val time = _elapsedMs.value
        val technique = puzzle?.hardestTechniqueName ?: ""
        viewModelScope.launch {
            val newBest = services.progress.markSolved(st.puzzleId, st.level, time)
            delay(400L)
            if (_state.value?.puzzleId == st.puzzleId) {
                _win.value = WinInfo(st.level, st.index, technique, time, newBest, st.hintsUsed, st.mistakes)
            }
        }
    }
}
