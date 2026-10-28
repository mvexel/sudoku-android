package net.vanexel.sudoku.game

import kotlin.random.Random
import net.vanexel.sudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Additional reducer behaviour checks (tap-twice model, hints, win, digit focus). */
class ReducerQaTest {
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

    private fun newGame() = GameState.newGame("qa", Difficulty.EXPERT, 4, givens, solution)

    private val empty = 2   // solution 4
    private val peer = 3    // same row, solution 6
    private val nonPeer = 72

    private fun GameState.withNotes(vararg cellsAndNotes: Pair<Int, Int>): GameState {
        val n = notes.toMutableList()
        for ((cell, mask) in cellsAndNotes) n[cell] = mask
        return copy(notes = n)
    }

    private fun bits(vararg ds: Int): Int = ds.fold(0) { acc, d -> acc or noteBit(d) }

    // ---- tap twice ----

    @Test
    fun deselectByTappingSelectedCellCommits() {
        val s = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4).select(empty)
        assertEquals(NO_CELL, s.selected)
        assertNull(s.pendingPlacement)
        assertEquals(0, s.notes[peer])
    }

    @Test
    fun conversionRestoresPeersAndOnlyAddsTheNote() {
        val start = newGame()
            .withNotes(peer to bits(4, 6), nonPeer to bits(4), empty to bits(2, 7))
            .select(empty)
        val s = start.enterDigit(4).enterDigit(4)
        // Exactly the pre-placement board plus note 4 in the cell.
        val expectedNotes = start.notes.toMutableList().also { it[empty] = bits(2, 4, 7) }
        assertEquals(start.values, s.values)
        assertEquals(expectedNotes, s.notes)
        assertEquals(start.mistakes, s.mistakes)
        assertEquals(empty, s.selected)
    }

    @Test
    fun thirdTapPlacesAgainAndFourthConvertsAgain_noteKept() {
        val s1 = newGame().select(empty).enterDigit(4).enterDigit(4) // note 4
        val s2 = s1.enterDigit(4)                                     // provisional again
        assertEquals(4, s2.values[empty])
        assertEquals(PendingPlacement(empty, 4), s2.pendingPlacement)
        val s3 = s2.enterDigit(4)
        assertEquals(0, s3.values[empty])
        assertEquals(bits(4), s3.notes[empty])
        // Undo the second pair returns to "note 4", undo again to the empty start.
        assertEquals(bits(4), s3.undo().notes[empty])
        assertEquals(0, s3.undo().undo().notes[empty])
    }

    @Test
    fun committedConversionUndoBringsValueBack() {
        val committed = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4).select(nonPeer).select(empty)
        assertEquals(4, committed.values[empty])
        assertEquals(0, committed.notes[peer])
        val converted = committed.enterDigit(4)
        assertEquals(0, converted.values[empty])
        assertTrue(hasNote(converted.notes[empty], 4))
        assertEquals(0, converted.notes[peer]) // peer removal not restored
        val undone = converted.undo()
        assertEquals(4, undone.values[empty])
        assertFalse(hasNote(undone.notes[empty], 4))
    }

    @Test
    fun committedWrongValueConversionDoesNotRefundMistake() {
        val s = newGame().select(empty).enterDigit(9).commitPending()
        assertEquals(1, s.mistakes)
        assertEquals(1, s.enterDigit(9).mistakes)
    }

    @Test
    fun pendingLastDigitCanStillBeConverted() {
        var s = newGame()
        val fives = (0 until 81).filter { solution[it] == 5 && givens[it] == 0 }
        for (c in fives.dropLast(1)) s = s.select(c).enterDigit(5, tapTwiceForNote = false)
        s = s.select(fives.last()).enterDigit(5)
        assertEquals(0, s.remaining(5))
        assertTrue(s.isPadKeyEnabled(5))
        val converted = s.enterDigit(5)
        assertEquals(0, converted.values[fives.last()])
        assertTrue(hasNote(converted.notes[fives.last()], 5))
        assertEquals(1, converted.remaining(5))
        // Other digits at zero stay disabled.
        assertFalse(s.copy(pendingPlacement = PendingPlacement(fives.last(), 3)).isPadKeyEnabled(5))
    }

    @Test
    fun replacingWithDifferentDigitThenSameDigitFollowsCommittedRule() {
        val s = newGame().withNotes(peer to bits(4)).select(empty)
            .enterDigit(7).enterDigit(4) // 7 committed, replaced by committed 4
        assertNull(s.pendingPlacement)
        assertEquals(0, s.notes[peer])
        val again = s.enterDigit(4)       // rule 4: committed value -> note, own step
        assertEquals(0, again.values[empty])
        assertTrue(hasNote(again.notes[empty], 4))
        assertEquals(s.history.size + 1, again.history.size)
    }

    // ---- long-press ----

    @Test
    fun longPressTogglesNoteRegardlessOfNotesMode() {
        val s = newGame().select(empty)
        assertFalse(s.notesMode)
        val on = s.toggleNote(3)
        assertTrue(hasNote(on.notes[empty], 3))
        assertFalse(hasNote(on.toggleNote(3).notes[empty], 3)) // the one-action removal
    }

    @Test
    fun longPressOnProvisionalValueOnlyCommits() {
        val pending = newGame().withNotes(peer to bits(4)).select(empty).enterDigit(4)
        val s = pending.toggleNote(4)
        assertNull(s.pendingPlacement)
        assertEquals(4, s.values[empty])
        assertFalse(hasNote(s.notes[empty], 4))
        assertEquals(0, s.notes[peer])
        assertEquals(pending.history, s.history)
    }

    // ---- hints ----

    @Test
    fun hintUndoRestoresBoardAndSelectionAndHintCanBeReused() {
        val start = newGame().withNotes(peer to bits(4, 6)).select(empty)
        val hinted = start.hint()
        assertEquals(4, hinted.values[empty])
        assertEquals(bits(6), hinted.notes[peer])
        val undone = hinted.select(nonPeer).undo()
        assertEquals(0, undone.values[empty])
        assertEquals(bits(4, 6), undone.notes[peer])
        assertEquals(empty, undone.selected)
        assertEquals(MAX_HINTS - 1, undone.hintsLeft)
        val again = undone.hint()
        assertEquals(4, again.values[empty])
        assertEquals(MAX_HINTS - 2, again.hintsLeft)
        assertEquals(2, again.hintsUsed)
    }

    @Test
    fun hintCommitsPendingWrongValueThenFixesIt() {
        val s = newGame().select(empty).enterDigit(9).hint()
        assertEquals(4, s.values[empty])
        assertEquals(1, s.mistakes)
        assertNull(s.pendingPlacement)
    }

    @Test
    fun hintThatCompletesBoardWins() {
        var s = newGame()
        val empties = (0 until 81).filter { givens[it] == 0 }
        for (c in empties.dropLast(1)) s = s.select(c).enterDigit(solution[c], tapTwiceForNote = false)
        s = s.select(empties.last()).hint()
        assertTrue(s.solved)
        assertFalse(s.canHint)
        assertFalse(s.canErase)
        assertFalse(s.isPadKeyEnabled(1))
    }

    // ---- win ----

    @Test
    fun provisionalLastPlacementWinsImmediately() {
        var s = newGame()
        val empties = (0 until 81).filter { givens[it] == 0 }
        for (c in empties.dropLast(1)) s = s.select(c).enterDigit(solution[c], tapTwiceForNote = false)
        val last = empties.last()
        s = s.select(last).enterDigit(solution[last])
        assertTrue(s.solved)
        assertNull(s.pendingPlacement)
        assertEquals(0, s.mistakes)
        assertSame(s, s.enterDigit(solution[last])) // no conversion after the win
        assertSame(s, s.toggleNotesMode())
        assertSame(s, s.erase())
        assertSame(s, s.hint())
    }

    // ---- digit focus ----

    @Test
    fun digitFocusMatchesBruteForceAndIgnoresNotes() {
        val rnd = Random(7)
        repeat(200) {
            val values = List(81) { if (rnd.nextInt(3) == 0) rnd.nextInt(1, 10) else 0 }
            val d = rnd.nextInt(1, 10)
            val f = digitFocus(values, d)
            for (cell in 0 until 81) {
                val expected = values[cell] != 0 ||
                    (0 until 81).any { o -> values[o] == d && (o == cell || Peers.isPeer(o, cell)) }
                assertEquals("cell $cell digit $d", expected, f.excluded[cell])
            }
            assertEquals(values.indices.filter { values[it] == d }.map { Peers.row(it) }.distinct().sorted(), f.rows)
            assertEquals(values.indices.filter { values[it] == d }.map { Peers.col(it) }.distinct().sorted(), f.cols)
        }
    }

    @Test
    fun notesNeverExcludeInBoardView() {
        val base = newGame().focusDigit(4)
        val withNotes = base.copy(notes = List(81) { if (givens[it] == 0) bits(4) else 0 })
        val a = boardView(base, false, true, true)
        val b = boardView(withNotes, false, true, true)
        assertEquals(a.focus, b.focus)
        assertEquals(a.backgrounds, b.backgrounds)
        assertEquals(4, b.highlightedNote)
    }

    @Test
    fun provisionalValueDrivesFocusButIsNotAMistakeYet() {
        val s = newGame().select(empty).enterDigit(9) // wrong, pending
        val v = boardView(s, highlightMistakes = true, highlightSameDigits = true, helperLines = true)
        assertEquals(9, v.focus?.digit)
        assertFalse(v.mistakes[empty])
        val committed = boardView(s.select(nonPeer).select(empty), true, true, true)
        assertTrue(committed.mistakes[empty])
        assertEquals(CellBackground.SELECTED, committed.backgrounds[empty])
    }

    @Test
    fun cellDescriptions() {
        val s = newGame().select(empty).enterDigit(4).select(nonPeer).toggleNote(2).toggleNote(9)
        val v = boardView(s, false, true, true)
        assertEquals("Row 1, column 1, 5, given", cellDescription(s, v, 0))
        assertEquals("Row 1, column 3, 4", cellDescription(s, v, empty))
        assertEquals("Row 9, column 1, empty, notes 2 9", cellDescription(s, v, nonPeer))
    }
}
