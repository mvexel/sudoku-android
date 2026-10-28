package net.vanexel.sudoku.game

import net.vanexel.sudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

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

    private fun newGame() = GameState.newGame("test", Difficulty.HARD, 0, givens, solution)

    // Cell 2 (row 0, col 2) is empty; solution 4. Cell 3 (row 0, col 3) is empty; solution 6.
    // Cell 0 is a given (5).
    private val empty = 2
    private val peer = 3
    private val nonPeer = 72 // row 8, col 0, empty: not a peer of cell 2

    private fun GameState.withNotes(vararg cellsAndNotes: Pair<Int, Int>): GameState {
        val n = notes.toMutableList()
        for ((cell, mask) in cellsAndNotes) n[cell] = mask
        return copy(notes = n)
    }

    private fun bits(vararg digits: Int): Int = digits.fold(0) { acc, d -> acc or noteBit(d) }

    // ---- selection ----

    @Test
    fun selectAndDeselect() {
        val s = newGame().select(empty)
        assertEquals(empty, s.selected)
        assertEquals(NO_CELL, s.select(empty).selected) // tapping the selected cell deselects
        assertEquals(NO_CELL, s.deselect().selected)
        assertEquals(0, s.select(0).selected) // givens can be selected
    }

    @Test
    fun padTapWithoutSelectionFocusesDigit() {
        val s = newGame().focusDigit(5)
        assertEquals(5, s.padFocus)
        assertEquals(5, s.focusedDigit)
        assertEquals(0, s.focusDigit(5).padFocus) // same key again clears
        assertEquals(0, s.select(empty).padFocus) // selecting a cell clears
    }

    // ---- provisional placement ----

    @Test
    fun firstTapIsProvisional() {
        val start = newGame().withNotes(peer to bits(4, 6), nonPeer to bits(4)).select(empty)
        val s = start.enterDigit(4)
        assertEquals(4, s.values[empty])
        assertEquals(PendingPlacement(empty, 4), s.pendingPlacement)
        assertEquals(bits(4, 6), s.notes[peer]) // peer notes untouched while pending
        assertEquals(1, s.history.size)
    }

    @Test
    fun commitRemovesPeerNotesAndCountsMistake() {
        val start = newGame().withNotes(peer to bits(9, 6), nonPeer to bits(9)).select(empty)
        val pending = start.enterDigit(9) // wrong (solution is 4)
        assertEquals(0, pending.mistakes)

        val committed = pending.commitPending()
        assertNull(committed.pendingPlacement)
        assertEquals(bits(6), committed.notes[peer])
        assertEquals(bits(9), committed.notes[nonPeer])
        assertEquals(1, committed.mistakes)
        assertEquals(1, committed.history.size) // no extra undo step
    }

    @Test
    fun selectionChangeCommits() {
        val s = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4).select(peer)
        assertNull(s.pendingPlacement)
        assertEquals(0, s.notes[peer])
    }

    @Test
    fun otherActionsCommit() {
        val pending = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4)
        for (after in listOf(pending.toggleNotesMode(), pending.deselect(), pending.toggleNote(4), pending.hint())) {
            assertNull(after.pendingPlacement)
            assertEquals(0, after.notes[peer])
        }
        // Erase commits, then clears the value.
        val erased = pending.erase()
        assertNull(erased.pendingPlacement)
        assertEquals(0, erased.values[empty])
        assertEquals(0, erased.notes[peer])
    }

    @Test
    fun differentDigitCommitsThenReplaces() {
        val start = newGame().withNotes(peer to bits(7, 4)).select(empty)
        val s = start.enterDigit(7).enterDigit(4)
        assertEquals(4, s.values[empty])
        assertNull(s.pendingPlacement) // replacing a value is committed at once
        assertEquals(0, s.notes[peer]) // 7 removed at the first commit, 4 at the replacement
        assertEquals(1, s.mistakes) // the 7 was wrong
        assertEquals(2, s.history.size)
    }

    // ---- tap twice for note ----

    @Test
    fun tapTwiceTurnsPendingValueIntoNote_restoringPeers() {
        val start = newGame().withNotes(peer to bits(4, 6), empty to bits(1)).select(empty)
        val s = start.enterDigit(4).enterDigit(4)
        assertEquals(0, s.values[empty])
        assertEquals(bits(1, 4), s.notes[empty]) // earlier notes intact, 4 added
        assertEquals(bits(4, 6), s.notes[peer]) // peers untouched
        assertNull(s.pendingPlacement)
        assertEquals(0, s.mistakes)
        assertEquals(1, s.history.size) // one undo step for the pair

        val undone = s.undo()
        assertEquals(start.values, undone.values)
        assertEquals(start.notes, undone.notes)
        assertTrue(undone.history.isEmpty())
    }

    @Test
    fun tapTwiceOnWrongDigitCountsNoMistake() {
        val s = newGame().select(empty).enterDigit(9).enterDigit(9)
        assertEquals(0, s.mistakes)
        assertEquals(bits(9), s.notes[empty])
    }

    @Test
    fun tapTwiceOnExistingNoteKeepsIt() {
        // Only ever adds the note; removal is long-press, Notes mode or Erase.
        val s = newGame().withNotes(empty to bits(4, 5)).select(empty).enterDigit(4).enterDigit(4)
        assertEquals(bits(4, 5), s.notes[empty])
        assertEquals(0, s.values[empty])
    }

    @Test
    fun tapOnCommittedValueTurnsItIntoNote_asOwnStep() {
        val committed = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4).commitPending()
        assertEquals(1, committed.history.size)
        val s = committed.enterDigit(4)
        assertEquals(0, s.values[empty])
        assertTrue(hasNote(s.notes[empty], 4))
        assertEquals(0, s.notes[peer]) // not restored
        assertEquals(2, s.history.size)
    }

    @Test
    fun tapTwiceOffCommitsAtOnceAndOwnValueDoesNothing() {
        val start = newGame().withNotes(peer to bits(4)).select(empty)
        val s = start.enterDigit(4, tapTwiceForNote = false)
        assertNull(s.pendingPlacement)
        assertEquals(0, s.notes[peer])
        assertSame(s, s.enterDigit(4, tapTwiceForNote = false))
    }

    @Test
    fun undoRevertsPendingPlacement() {
        val start = newGame().withNotes(peer to bits(4)).select(empty)
        val s = start.enterDigit(4).undo()
        assertEquals(0, s.values[empty])
        assertNull(s.pendingPlacement)
        assertEquals(bits(4), s.notes[peer])
        assertEquals(empty, s.selected)
    }

    @Test
    fun undoAfterCommitRevertsPlacementAndPeerRemovalTogether() {
        val start = newGame().withNotes(peer to bits(4)).select(empty)
        val s = start.enterDigit(4).select(peer).undo()
        assertEquals(0, s.values[empty])
        assertEquals(bits(4), s.notes[peer])
        assertEquals(empty, s.selected) // selection restored to the affected cell
    }

    @Test
    fun padKeyStaysEnabledForPendingLastDigit() {
        // Fill every 5 except one, then place the last one provisionally.
        var s = newGame()
        val fives = (0 until 81).filter { solution[it] == 5 && givens[it] == 0 }
        for (c in fives.dropLast(1)) s = s.select(c).enterDigit(5, tapTwiceForNote = false)
        s = s.select(fives.last()).enterDigit(5)
        assertEquals(0, s.remaining(5))
        assertTrue(s.isPadKeyEnabled(5))
        assertFalse(s.commitPending().isPadKeyEnabled(5))
    }

    // ---- givens, notes, erase ----

    @Test
    fun givensAreReadOnly() {
        val s = newGame().select(0)
        assertSame(s, s.enterDigit(4))
        assertSame(s, s.toggleNote(4))
        assertSame(s, s.erase())
    }

    @Test
    fun toggleNoteAddsAndRemoves() {
        val s = newGame().select(empty)
        val on = s.toggleNote(7)
        assertTrue(hasNote(on.notes[empty], 7))
        val off = on.toggleNote(7)
        assertFalse(hasNote(off.notes[empty], 7))
        assertEquals(2, off.history.size)
    }

    @Test
    fun toggleNoteOnFilledCellDoesNothing() {
        val s = newGame().select(empty).enterDigit(4, tapTwiceForNote = false)
        assertEquals(s, s.toggleNote(7))
    }

    @Test
    fun eraseClearsValueThenNotes() {
        val s = newGame().withNotes(empty to bits(2, 3)).select(empty).enterDigit(4, tapTwiceForNote = false)
        val noValue = s.erase()
        assertEquals(0, noValue.values[empty])
        assertEquals(bits(2, 3), noValue.notes[empty]) // hidden notes come back
        val noNotes = noValue.erase()
        assertEquals(0, noNotes.notes[empty])
        assertEquals(noNotes, noNotes.erase())
    }

    // ---- hints ----

    @Test
    fun hintFillsSelectedEmptyCell() {
        val s = newGame().select(empty).hint()
        assertEquals(solution[empty], s.values[empty])
        assertEquals(MAX_HINTS - 1, s.hintsLeft)
        assertEquals(1, s.hintsUsed)
        assertEquals(empty, s.selected)
    }

    @Test
    fun hintFixesWrongSelectedCell() {
        val s = newGame().select(empty).enterDigit(9, tapTwiceForNote = false).hint()
        assertEquals(solution[empty], s.values[empty])
    }

    @Test
    fun hintWithoutUsefulSelectionPicksACellAndSelectsIt() {
        val s = newGame().select(0).hint()
        assertNotEquals(0, s.selected)
        assertEquals(0, givens[s.selected])
        assertEquals(solution[s.selected], s.values[s.selected])
    }

    @Test
    fun undoingAHintDoesNotRefundIt() {
        val s = newGame().select(empty).hint().undo()
        assertEquals(0, s.values[empty])
        assertEquals(MAX_HINTS - 1, s.hintsLeft)
    }

    @Test
    fun noHintsLeftIsNoOp() {
        var s = newGame()
        repeat(MAX_HINTS) { s = s.hint() }
        assertEquals(0, s.hintsLeft)
        assertSame(s, s.hint())
    }

    // ---- win ----

    @Test
    fun fillingTheSolutionWinsAndLocksInput() {
        var s = newGame()
        for (c in 0 until 81) if (givens[c] == 0) s = s.select(c).enterDigit(solution[c])
        assertTrue(s.solved)
        assertNull(s.pendingPlacement)
        assertSame(s, s.select(0))
        assertSame(s, s.undo())
        assertFalse(s.canUndo)
    }

    @Test
    fun fullButWrongBoardIsNotAWin() {
        var s = newGame()
        val empties = (0 until 81).filter { givens[it] == 0 }
        for (c in empties.dropLast(1)) s = s.select(c).enterDigit(solution[c], tapTwiceForNote = false)
        val last = empties.last()
        val wrong = if (solution[last] == 1) 2 else 1
        s = s.select(last).enterDigit(wrong, tapTwiceForNote = false)
        assertFalse(s.solved)
        assertEquals(1, s.mistakes)
    }
}
