package net.vanexel.sudoku.game

/**
 * A row, column, box or digit completed by a placement in [origin]: all nine cells filled without
 * duplicates (for a digit: nine placed with no two sharing a house). Never checked against the
 * solution, so it reveals nothing the conflict styling doesn't. [cells] flash outward from [origin].
 */
data class Completion(val kind: Kind, val index: Int, val origin: Int, val cells: List<Int>) {
    enum class Kind { ROW, COLUMN, BOX, DIGIT }

    /** TalkBack text, e.g. "Row 3 complete" or "All 9s placed". */
    val announcement: String
        get() = when (kind) {
            Kind.ROW -> "Row ${index + 1} complete"
            Kind.COLUMN -> "Column ${index + 1} complete"
            Kind.BOX -> "Box ${index + 1} complete"
            Kind.DIGIT -> "All ${index}s placed"
        }
}

private val ROWS = List(9) { r -> List(9) { c -> r * 9 + c } }
private val COLUMNS = List(9) { c -> List(9) { r -> r * 9 + c } }
private val BOXES = List(9) { b -> (0 until Peers.CELLS).filter { Peers.box(it) == b } }

private fun houseComplete(values: List<Int>, cells: List<Int>): Boolean =
    cells.all { values[it] != 0 } && cells.map { values[it] }.toSet().size == 9

private fun digitComplete(values: List<Int>, digit: Int): Boolean {
    val cells = values.indices.filter { values[it] == digit }
    return cells.size == 9 &&
        cells.map { Peers.row(it) }.toSet().size == 9 &&
        cells.map { Peers.col(it) }.toSet().size == 9 &&
        cells.map { Peers.box(it) }.toSet().size == 9
}

/** Houses and digits that are complete in [after] but were not in [before], for each newly set cell. */
fun completions(before: List<Int>, after: List<Int>): List<Completion> {
    require(before.size == Peers.CELLS && after.size == Peers.CELLS) { "need 81 values" }
    val found = LinkedHashMap<Pair<Completion.Kind, Int>, Completion>()
    for (cell in 0 until Peers.CELLS) {
        if (after[cell] == 0 || after[cell] == before[cell]) continue
        val houses = listOf(
            Completion.Kind.ROW to Peers.row(cell),
            Completion.Kind.COLUMN to Peers.col(cell),
            Completion.Kind.BOX to Peers.box(cell),
        )
        for ((kind, i) in houses) {
            val cells = when (kind) {
                Completion.Kind.ROW -> ROWS[i]
                Completion.Kind.COLUMN -> COLUMNS[i]
                else -> BOXES[i]
            }
            if (houseComplete(after, cells) && !houseComplete(before, cells)) {
                found.getOrPut(kind to i) { Completion(kind, i, cell, cells) }
            }
        }
        val d = after[cell]
        if (digitComplete(after, d) && !digitComplete(before, d)) {
            found.getOrPut(Completion.Kind.DIGIT to d) {
                Completion(Completion.Kind.DIGIT, d, cell, after.indices.filter { after[it] == d })
            }
        }
    }
    return found.values.toList()
}
