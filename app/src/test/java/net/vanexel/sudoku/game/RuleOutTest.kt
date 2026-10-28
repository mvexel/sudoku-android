package net.vanexel.sudoku.game

import net.vanexel.sudoku.data.SavedGame
import net.vanexel.sudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleOutTest {

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

    // Cell 2 is empty (solution 4); cell 3 is an empty peer (solution 6); cell 72 is not a peer of 2.
    private val empty = 2
    private val peer = 3
    private val nonPeer = 72

    @Test
    fun notesButtonCyclesThroughRuleOutOnlyWhenAllowed() {
        val s = newGame()
        val notes = s.toggleNotesMode(allowRuleOut = true)
        assertTrue(notes.notesMode)
        val ruleOut = notes.toggleNotesMode(allowRuleOut = true)
        assertTrue(ruleOut.ruleOutMode)
        assertFalse(ruleOut.notesMode)
        val off = ruleOut.toggleNotesMode(allowRuleOut = true)
        assertFalse(off.notesMode || off.ruleOutMode)

        assertFalse(notes.toggleNotesMode(allowRuleOut = false).let { it.notesMode || it.ruleOutMode })
        // Setting switched off while in rule-out mode: the next tap goes back to off.
        assertFalse(ruleOut.toggleNotesMode(allowRuleOut = false).let { it.notesMode || it.ruleOutMode })
    }

    @Test
    fun ruleOutAndNoteAreMutuallyExclusive() {
        val s = newGame().select(empty).toggleNote(7)
        val ruled = s.toggleRuleOut(7)
        assertTrue(hasNote(ruled.ruledOut[empty], 7))
        assertFalse(hasNote(ruled.notes[empty], 7))
        val noted = ruled.toggleNote(7)
        assertTrue(hasNote(noted.notes[empty], 7))
        assertFalse(hasNote(noted.ruledOut[empty], 7))
        assertEquals(0, ruled.toggleRuleOut(7).ruledOut[empty]) // toggles off
    }

    @Test
    fun ruleOutOnlyOnEmptyEditableCells() {
        val s = newGame().select(0) // a given
        assertEquals(s, s.toggleRuleOut(3))
    }

    @Test
    fun placingClearsOwnAndPeerMarksForThatDigit() {
        val s = newGame()
            .select(empty).toggleRuleOut(4).toggleRuleOut(8)
            .select(peer).toggleRuleOut(4)
            .select(nonPeer).toggleRuleOut(4)
            .select(empty).enterDigit(4, tapTwiceForNote = false)
        assertEquals(noteBit(8), s.ruledOut[empty])
        assertEquals(0, s.ruledOut[peer])
        assertEquals(noteBit(4), s.ruledOut[nonPeer])
    }

    @Test
    fun provisionalPlacementClearsMarksOnlyAtCommit() {
        val s = newGame().select(peer).toggleRuleOut(4).select(empty).enterDigit(4)
        assertEquals(noteBit(4), s.ruledOut[peer])
        assertEquals(0, s.commitPending().ruledOut[peer])
        // Second tap turns it into a note; the mark for 4 in the peer survives.
        val converted = s.enterDigit(4)
        assertTrue(hasNote(converted.notes[empty], 4))
        assertEquals(noteBit(4), converted.ruledOut[peer])
    }

    @Test
    fun eraseClearsBothMasksAndUndoRestores() {
        val s = newGame().select(empty).toggleNote(1).toggleRuleOut(9)
        assertTrue(s.canErase)
        val erased = s.erase()
        assertEquals(0, erased.notes[empty])
        assertEquals(0, erased.ruledOut[empty])
        val undone = erased.undo()
        assertEquals(noteBit(9), undone.ruledOut[empty])
        assertEquals(noteBit(1), undone.notes[empty])
        assertEquals(0, undone.undo().ruledOut[empty])
    }

    @Test
    fun ruledOutDigitShadesTheCellInFocus() {
        // A cell where the placed 4s still allow a 4.
        val open = (0 until 81).first { !digitFocus(givens, 4).excluded[it] }
        val s = newGame().select(open).toggleRuleOut(4).deselect().focusDigit(4)
        val view = boardView(s, highlightMistakes = false, highlightSameDigits = true, helperLines = true)
        assertTrue(view.focus!!.excluded[open])
        assertEquals(CellBackground.EXCLUDED, view.backgrounds[open])
        // Notes never exclude.
        val noted = newGame().select(open).toggleNote(4).deselect().focusDigit(4)
        val nv = boardView(noted, highlightMistakes = false, highlightSameDigits = true, helperLines = true)
        assertFalse(nv.focus!!.excluded[open])
    }

    @Test
    fun talkBackDescribesRuledOutDigits() {
        val s = newGame().select(empty).toggleNote(2).toggleRuleOut(1).toggleRuleOut(7)
        val view = boardView(s, highlightMistakes = false, highlightSameDigits = true, helperLines = true)
        assertEquals("Row 1, column 3, empty, notes 2, ruled out 1 7", cellDescription(s, view, empty))
    }

    @Test
    fun saveRoundTripKeepsMarksAndMode() {
        val st = newGame().select(empty).toggleRuleOut(3)
            .toggleNotesMode(allowRuleOut = true).toggleNotesMode(allowRuleOut = true)
        val restored = SavedGame.of(st, 0L).toState(newGame())
        assertNotNull(restored)
        assertEquals(st.ruledOut, restored!!.ruledOut)
        assertTrue(restored.ruleOutMode)
        assertFalse(restored.notesMode)
    }
}
