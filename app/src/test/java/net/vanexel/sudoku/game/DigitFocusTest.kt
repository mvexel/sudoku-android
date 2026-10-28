package net.vanexel.sudoku.game

import net.vanexel.sudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitFocusTest {

    private fun board(vararg placed: Pair<Int, Int>): List<Int> {
        val v = MutableList(81) { 0 }
        for ((cell, d) in placed) v[cell] = d
        return v
    }

    @Test
    fun singleDigitExcludesRowColumnAndBox() {
        val f = digitFocus(board(0 to 5), 5)
        assertEquals(listOf(0), f.rows)
        assertEquals(listOf(0), f.cols)
        assertEquals(81 - 21, f.candidateCount) // the cell itself + 20 peers
        assertTrue(f.excluded[8])  // row 0
        assertTrue(f.excluded[72]) // col 0
        assertTrue(f.excluded[20]) // box 0
        assertFalse(f.excluded[30])
    }

    @Test
    fun filledCellsAreExcludedButOtherDigitsDrawNoLines() {
        val f = digitFocus(board(0 to 5, 40 to 3), 5)
        assertTrue(f.excluded[40])
        assertEquals(listOf(0), f.rows)
        assertEquals(81 - 21 - 1, f.candidateCount)
    }

    @Test
    fun linesAreSortedAndDistinct() {
        val f = digitFocus(board(80 to 2, 0 to 2, 40 to 2), 2)
        assertEquals(listOf(0, 4, 8), f.rows)
        assertEquals(listOf(0, 4, 8), f.cols)
    }

    @Test
    fun noPlacedDigitExcludesOnlyFilledCells() {
        val f = digitFocus(board(10 to 1), 9)
        assertTrue(f.rows.isEmpty())
        assertTrue(f.cols.isEmpty())
        assertEquals(80, f.candidateCount)
    }

    @Test
    fun boardViewUsesFocusInsteadOfPeers() {
        val values = board(0 to 5)
        val sol = List(81) { 1 }
        val state = GameState.newGame("x", Difficulty.HARD, 0, values, sol).select(0)
        val on = boardView(state, highlightMistakes = false, highlightSameDigits = true, helperLines = true)
        assertEquals(CellBackground.SELECTED, on.backgrounds[0])
        assertEquals(CellBackground.EXCLUDED, on.backgrounds[1])
        assertEquals(CellBackground.DEFAULT, on.backgrounds[40])
        assertEquals(5, on.focus?.digit)

        val off = boardView(state, highlightMistakes = false, highlightSameDigits = true, helperLines = false)
        assertNull(off.focus)
        assertEquals(CellBackground.PEER, off.backgrounds[1])
        assertEquals(CellBackground.DEFAULT, off.backgrounds[40])
    }

    @Test
    fun focusAnnouncementText() {
        val f = digitFocus(board(0 to 1), 1)
        assertEquals("Focused on 1: 60 cells possible", focusAnnouncement(f))
    }
}
