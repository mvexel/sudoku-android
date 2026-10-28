package net.vanexel.sudoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionTest {

    // A valid solved grid: row r, col c holds ((r * 3 + r / 3 + c) % 9) + 1.
    private val solved = List(81) { ((Peers.row(it) * 3 + Peers.row(it) / 3 + Peers.col(it)) % 9) + 1 }

    private fun without(vararg cells: Int): List<Int> =
        solved.mapIndexed { i, v -> if (i in cells) 0 else v }

    private fun kinds(found: List<Completion>) = found.map { it.kind to it.index }.toSet()

    @Test
    fun lastCellOfARowCompletesOnlyTheRow() {
        // Clear cell 4 (row 0), cells keeping its column and box open, and another of its digit.
        val otherSame = (27 until 81).first { solved[it] == solved[4] }
        val before = without(4, 13, 22, otherSame)
        val after = before.toMutableList().also { it[4] = solved[4] }
        val found = completions(before, after)
        assertEquals(setOf(Completion.Kind.ROW to 0), kinds(found))
        assertEquals(4, found.single().origin)
        assertEquals((0..8).toList(), found.single().cells)
    }

    @Test
    fun oneCellCanCompleteRowColumnBoxAndDigit() {
        val before = without(40)
        val after = solved
        assertEquals(
            setOf(
                Completion.Kind.ROW to 4,
                Completion.Kind.COLUMN to 4,
                Completion.Kind.BOX to 4,
                Completion.Kind.DIGIT to solved[40],
            ),
            kinds(completions(before, after)),
        )
    }

    @Test
    fun duplicatesDoNotComplete() {
        val before = without(4, 13, 22)
        val after = before.toMutableList().also { it[4] = solved[0] } // duplicates cell 0
        assertTrue(completions(before, after).isEmpty())
    }

    @Test
    fun alreadyCompleteHousesAreNotRepeated() {
        // Row 0 is already full; filling cell 9 (row 1) must not report row 0.
        val before = without(9, 10, 18)
        val after = before.toMutableList().also { it[9] = solved[9] }
        assertTrue(Completion.Kind.ROW to 0 !in kinds(completions(before, after)))
    }

    @Test
    fun erasingCompletesNothing() {
        assertTrue(completions(solved, without(40)).isEmpty())
    }
}
