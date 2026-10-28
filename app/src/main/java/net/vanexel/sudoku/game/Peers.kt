package net.vanexel.sudoku.game

/** Cell geometry helpers. Cells are indexed 0..80, row-major. */
object Peers {
    const val CELLS = 81

    fun row(cell: Int): Int = cell / 9

    fun col(cell: Int): Int = cell % 9

    fun box(cell: Int): Int = (cell / 27) * 3 + (cell % 9) / 3

    /** True when [a] and [b] are different cells sharing a row, column or box. */
    fun isPeer(a: Int, b: Int): Boolean =
        a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))

    /** The 20 peers of each cell. */
    val PEERS: List<IntArray> = List(CELLS) { cell ->
        (0 until CELLS).filter { other -> isPeer(cell, other) }.toIntArray()
    }
}
