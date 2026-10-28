package net.vanexel.sudoku.game

/**
 * Digit focus for [digit]: [excluded] marks every cell where the digit can no longer go (every
 * filled cell, every peer of a placed [digit], and every empty cell where the player ruled it out); [rows] and [cols] are the rows and columns
 * holding a placed [digit]. Sorted, without duplicates.
 */
data class DigitFocus(
    val digit: Int,
    val excluded: List<Boolean>,
    val rows: List<Int>,
    val cols: List<Int>,
) {
    /** Empty cells not excluded: the remaining places for [digit]. */
    val candidateCount: Int get() = excluded.count { !it }
}

/**
 * Computes the digit focus from placed values and the player's rule-out marks ([ruledOut], same
 * bit layout as notes). Notes never exclude.
 */
fun digitFocus(values: List<Int>, digit: Int, ruledOut: List<Int>? = null): DigitFocus {
    require(values.size == Peers.CELLS) { "need 81 values" }
    require(digit in 1..9) { "digit must be 1..9" }
    val excluded = BooleanArray(Peers.CELLS)
    val rows = sortedSetOf<Int>()
    val cols = sortedSetOf<Int>()
    for (cell in 0 until Peers.CELLS) {
        if (values[cell] == 0) {
            if (ruledOut != null && hasNote(ruledOut[cell], digit)) excluded[cell] = true
            continue
        }
        excluded[cell] = true
        if (values[cell] == digit) {
            rows.add(Peers.row(cell))
            cols.add(Peers.col(cell))
            for (p in Peers.PEERS[cell]) excluded[p] = true
        }
    }
    return DigitFocus(digit, excluded.toList(), rows.toList(), cols.toList())
}
