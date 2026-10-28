package net.vanexel.sudoku.data

import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.game.GameState
import net.vanexel.sudoku.game.MAX_HINTS
import net.vanexel.sudoku.game.NO_CELL
import net.vanexel.sudoku.game.commitPending
import net.vanexel.sudoku.game.enterDigit
import net.vanexel.sudoku.game.hint
import net.vanexel.sudoku.game.noteBit
import net.vanexel.sudoku.game.select
import net.vanexel.sudoku.game.toggleNote
import net.vanexel.sudoku.game.toggleNotesMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round trip of the pure save model (SavedGame.of / toState); no Android needed. */
class SavedGameTest {
    private fun digits(s: String): List<Int> = s.map { it - '0' }

    private val givens = digits(
        "530070000" + "600195000" + "098000060" +
            "800060003" + "400803001" + "700020006" +
            "060000280" + "000419005" + "000080079"
    )
    private val solution = digits(
        "534678912" + "672195348" + "198342567" +
            "859761423" + "426853791" + "713924856" +
            "961537284" + "287419635" + "345286179"
    )

    private fun base() = GameState.newGame("id", Difficulty.HARD, 0, givens, solution)

    @Test
    fun roundTripKeepsEverythingButHistory() {
        val st = base().select(3).toggleNote(1).toggleNote(8)
            .select(2).enterDigit(9).commitPending() // a mistake
            .select(4).hint()
            .toggleNotesMode()
        val saved = SavedGame.of(st, 123_456L)
        val restored = saved.toState(base())
        assertNotNull(restored)
        restored!!
        assertEquals(st.values, restored.values)
        assertEquals(st.notes, restored.notes)
        assertEquals(st.selected, restored.selected)
        assertEquals(st.notesMode, restored.notesMode)
        assertEquals(MAX_HINTS - 1, restored.hintsLeft)
        assertEquals(1, restored.hintsUsed)
        assertEquals(1, restored.mistakes)
        assertTrue(restored.history.isEmpty())
        assertNull(restored.pendingPlacement)
        assertEquals(123_456L, saved.elapsedMs)
    }

    @Test
    fun pendingPlacementIsSavedCommitted() {
        val notes = base().notes.toMutableList().also { it[3] = noteBit(4) }
        val pending = base().copy(notes = notes).select(2).enterDigit(4)
        // What the view model writes: the committed form.
        val restored = SavedGame.of(pending.commitPending(), 0L).toState(base())!!
        assertEquals(4, restored.values[2])
        assertEquals(0, restored.notes[3])
        assertNull(restored.pendingPlacement)
    }

    @Test
    fun saveForOtherGivensIsRejected() {
        val other = base().values.toMutableList().also { it[0] = 1 }
        val saved = SavedGame(other, List(81) { 0 }, NO_CELL, false, 3, 0, 0, 0L)
        assertNull(saved.toState(base()))
        assertNull(SavedGame(List(80) { 0 }, List(81) { 0 }, NO_CELL, false, 3, 0, 0, 0L).toState(base()))
    }

    @Test
    fun outOfRangeFieldsAreClamped() {
        val saved = SavedGame(base().values, List(81) { -1 }, 200, true, 99, -3, -1, 0L)
        val r = saved.toState(base())!!
        assertEquals(NO_CELL, r.selected)
        assertEquals(MAX_HINTS, r.hintsLeft)
        assertEquals(0, r.hintsUsed)
        assertEquals(0, r.mistakes)
        assertEquals(0x1FF, r.notes[0])
    }
}
