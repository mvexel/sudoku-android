package net.vanexel.sudoku.game

import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.engine.Hints
import net.vanexel.sudoku.engine.Rules

const val MAX_HINTS = 3
const val NO_CELL = -1

/** Board contents before a mutation; [cell] is the cell the mutation affected (restored as selection on undo). */
data class Snapshot(
    val values: List<Int>,
    val notes: List<Int>,
    val cell: Int,
    val ruledOut: List<Int> = List(Peers.CELLS) { 0 },
)

/**
 * A provisional value: [digit] was just placed in the empty [cell]. It is shown at once (with
 * conflicts and the win check), but peer-note removal and the mistake check wait for the commit
 * (see [commitPending]). A second tap of [digit] turns it into a note instead.
 */
data class PendingPlacement(val cell: Int, val digit: Int)

/**
 * Immutable game state. Lists always have 81 entries.
 *
 * - [values]: 0 = empty; givens are included.
 * - [notes]: bit (d - 1) set = note d. Notes are kept while a cell has a value, but are only shown
 *   (and only matter) while the cell is empty.
 * - [ruledOut]: same bit layout, digits the player marked as impossible in the cell. A digit is
 *   never both a note and ruled out in one cell. Kept and hidden like notes.
 * - [pendingPlacement]: provisional value awaiting commit, or null. Never persisted uncommitted.
 * - [selected]: selected cell or [NO_CELL].
 * - [padFocus]: digit focused from the pad while nothing is selected, or 0.
 * - [notesMode], [ruleOutMode]: the entry mode of the Notes button; at most one is on.
 */
data class GameState(
    val puzzleId: String,
    val level: Difficulty,
    val index: Int,
    val givens: List<Int>,
    val solution: List<Int>,
    val values: List<Int>,
    val notes: List<Int>,
    val ruledOut: List<Int> = List(Peers.CELLS) { 0 },
    val selected: Int = NO_CELL,
    val padFocus: Int = 0,
    val notesMode: Boolean = false,
    val ruleOutMode: Boolean = false,
    val hintsLeft: Int = MAX_HINTS,
    val hintsUsed: Int = 0,
    val mistakes: Int = 0,
    val history: List<Snapshot> = emptyList(),
    val pendingPlacement: PendingPlacement? = null,
    val solved: Boolean = false,
) {
    fun isGiven(cell: Int): Boolean = cell in 0 until Peers.CELLS && givens[cell] != 0

    fun isEditable(cell: Int): Boolean = cell in 0 until Peers.CELLS && givens[cell] == 0

    /** The digit whose placements drive digit focus: the selected cell's value, or the pad focus. */
    val focusedDigit: Int
        get() = if (selected != NO_CELL) values[selected] else padFocus

    /** 9 minus the number of cells holding [digit] (givens and user values, right or wrong). */
    fun remaining(digit: Int): Int = 9 - values.count { it == digit }

    /**
     * Whether pad key [digit] is enabled: not while all nine are placed, except when a pending
     * placement used the last one (so the second tap of the gesture still works).
     */
    fun isPadKeyEnabled(digit: Int): Boolean =
        !solved && (remaining(digit) > 0 || pendingPlacement?.digit == digit)

    val canUndo: Boolean get() = !solved && history.isNotEmpty()

    val canErase: Boolean
        get() = !solved && isEditable(selected) &&
            (values[selected] != 0 || notes[selected] != 0 || ruledOut[selected] != 0)

    val canHint: Boolean get() = !solved && hintsLeft > 0

    companion object {
        fun newGame(
            puzzleId: String,
            level: Difficulty,
            index: Int,
            givens: List<Int>,
            solution: List<Int>,
        ): GameState {
            require(givens.size == Peers.CELLS && solution.size == Peers.CELLS)
            return GameState(
                puzzleId = puzzleId,
                level = level,
                index = index,
                givens = givens,
                solution = solution,
                values = givens,
                notes = List(Peers.CELLS) { 0 },
            )
        }
    }
}

fun noteBit(digit: Int): Int = 1 shl (digit - 1)

fun hasNote(notes: Int, digit: Int): Boolean = notes and noteBit(digit) != 0

private fun List<Int>.replaced(index: Int, value: Int): List<Int> =
    toMutableList().also { it[index] = value }

private fun GameState.snapshot(cell: Int): Snapshot = Snapshot(values, notes, cell, ruledOut)

/** Clears [digit] in [cell]'s entry of a note or rule-out mask list. */
private fun List<Int>.withoutDigit(cell: Int, digit: Int): List<Int> =
    replaced(cell, this[cell] and noteBit(digit).inv())

/** Removes [digit] from the notes of every peer of [cell]. The cell's own notes are kept (hidden). */
private fun peerNotesRemoved(notes: List<Int>, cell: Int, digit: Int): List<Int> {
    val mask = noteBit(digit).inv()
    val out = notes.toMutableList()
    for (p in Peers.PEERS[cell]) out[p] = out[p] and mask
    return out
}

/**
 * Rule-out marks after placing [digit] in [cell]: the cell's own mark for [digit] is overruled and
 * the peers' marks are now implied by the placement, so both are cleared.
 */
private fun ruledOutAfterPlacing(ruledOut: List<Int>, cell: Int, digit: Int): List<Int> =
    peerNotesRemoved(ruledOut, cell, digit).withoutDigit(cell, digit)

private fun GameState.withSolvedCheck(): GameState =
    if (Rules.isComplete(values.toIntArray(), solution.toIntArray())) {
        copy(solved = true, pendingPlacement = null)
    } else {
        this
    }

// ---- Reducers. Each returns a new state, or `this` when the action does not apply. ----

/**
 * Commits a pending placement: removes its digit from the peers' notes and counts a mistake if it
 * is wrong. Adds no undo step: the placement's snapshot already covers it, so one undo reverts both.
 */
fun GameState.commitPending(): GameState {
    val p = pendingPlacement ?: return this
    if (values[p.cell] != p.digit) return copy(pendingPlacement = null)
    return copy(
        notes = peerNotesRemoved(notes, p.cell, p.digit),
        ruledOut = ruledOutAfterPlacing(ruledOut, p.cell, p.digit),
        mistakes = if (p.digit != solution[p.cell]) mistakes + 1 else mistakes,
        pendingPlacement = null,
    )
}

/** Tapping a cell selects it (givens included); tapping the selected cell again deselects it. Commits. */
fun GameState.select(cell: Int): GameState {
    if (solved || cell !in 0 until Peers.CELLS) return this
    val base = commitPending()
    return base.copy(selected = if (cell == selected) NO_CELL else cell, padFocus = 0)
}

/** Tap outside the board: clears the selection and any pad focus. Commits. */
fun GameState.deselect(): GameState {
    if (solved) return this
    return commitPending().copy(selected = NO_CELL, padFocus = 0)
}

/** Pad tap with nothing selected: focuses [digit], or clears the focus if it is already focused. */
fun GameState.focusDigit(digit: Int): GameState {
    if (solved || digit !in 1..9) return this
    return commitPending().copy(selected = NO_CELL, padFocus = if (padFocus == digit) 0 else digit)
}

/**
 * The Notes button. Cycles off -> notes -> rule out -> off, or off <-> notes when [allowRuleOut]
 * is false (rule-out mode, if on, then goes back to off). Commits.
 */
fun GameState.toggleNotesMode(allowRuleOut: Boolean = false): GameState {
    if (solved) return this
    val base = commitPending()
    return when {
        ruleOutMode -> base.copy(notesMode = false, ruleOutMode = false)
        notesMode && allowRuleOut -> base.copy(notesMode = false, ruleOutMode = true)
        else -> base.copy(notesMode = !notesMode, ruleOutMode = false)
    }
}

/** A committed placement: peers lose [digit] from their notes and a wrong value counts a mistake. */
private fun GameState.placeCommitted(cell: Int, digit: Int): GameState = copy(
    values = values.replaced(cell, digit),
    notes = peerNotesRemoved(notes, cell, digit),
    ruledOut = ruledOutAfterPlacing(ruledOut, cell, digit),
    mistakes = if (digit != solution[cell]) mistakes + 1 else mistakes,
    history = history + snapshot(cell),
).withSolvedCheck()

/**
 * Pad tap of [digit] on the selected cell with Notes mode off.
 *
 * With [tapTwiceForNote] on:
 * - Empty cell (notes or not): provisional placement ([PendingPlacement]), one undo step.
 * - Pending placement of [digit] here: restore the exact pre-placement state and make sure note
 *   [digit] is on (never toggled off). Still the same single undo step.
 * - A different pending digit is committed first, then the tap is handled as below.
 * - Committed value [digit]: clear the value and add note [digit], as its own undo step. Peer notes
 *   removed at commit are not restored.
 * - A different value: replaced at once, committed (no provisional state).
 *
 * With [tapTwiceForNote] off, every placement is committed at once and tapping the cell's own
 * value does nothing. Givens and no selection: no change.
 */
fun GameState.enterDigit(digit: Int, tapTwiceForNote: Boolean = true): GameState {
    val cell = selected
    if (solved || digit !in 1..9 || !isEditable(cell)) return this

    val pending = pendingPlacement
    if (tapTwiceForNote && pending != null && pending.cell == cell && pending.digit == digit &&
        history.isNotEmpty()
    ) {
        val before = history.last()
        return copy(
            values = before.values.replaced(cell, 0),
            notes = before.notes.replaced(cell, before.notes[cell] or noteBit(digit)),
            ruledOut = before.ruledOut.withoutDigit(cell, digit),
            pendingPlacement = null,
        )
    }

    val base = commitPending()
    val current = base.values[cell]
    return when {
        current == digit && tapTwiceForNote -> base.copy(
            values = base.values.replaced(cell, 0),
            notes = base.notes.replaced(cell, base.notes[cell] or noteBit(digit)),
            ruledOut = base.ruledOut.withoutDigit(cell, digit),
            history = base.history + base.snapshot(cell),
        )
        current == digit -> base
        current != 0 || !tapTwiceForNote -> base.placeCommitted(cell, digit)
        else -> base.copy(
            values = base.values.replaced(cell, digit),
            history = base.history + base.snapshot(cell),
            pendingPlacement = PendingPlacement(cell, digit),
        ).withSolvedCheck()
    }
}

/**
 * Toggles note [digit] in the selected cell (Notes mode tap or long-press); adding it clears a
 * rule-out mark for [digit]. Commits first; only for empty editable cells.
 */
fun GameState.toggleNote(digit: Int): GameState {
    val cell = selected
    if (solved || digit !in 1..9 || !isEditable(cell)) return this
    val base = commitPending()
    if (base.values[cell] != 0) return base
    return base.copy(
        notes = base.notes.replaced(cell, base.notes[cell] xor noteBit(digit)),
        ruledOut = base.ruledOut.withoutDigit(cell, digit),
        history = base.history + base.snapshot(cell),
    )
}

/**
 * Toggles the rule-out mark for [digit] in the selected cell (Rule-out mode tap); adding it clears
 * note [digit]. Commits first; only for empty editable cells.
 */
fun GameState.toggleRuleOut(digit: Int): GameState {
    val cell = selected
    if (solved || digit !in 1..9 || !isEditable(cell)) return this
    val base = commitPending()
    if (base.values[cell] != 0) return base
    return base.copy(
        notes = base.notes.withoutDigit(cell, digit),
        ruledOut = base.ruledOut.replaced(cell, base.ruledOut[cell] xor noteBit(digit)),
        history = base.history + base.snapshot(cell),
    )
}

/** Clears the selected cell's value, or all its notes and rule-out marks if it has no value. Commits first. */
fun GameState.erase(): GameState {
    val cell = selected
    if (solved || !isEditable(cell)) return this
    val base = commitPending()
    return when {
        base.values[cell] != 0 -> base.copy(
            values = base.values.replaced(cell, 0),
            history = base.history + base.snapshot(cell),
        )
        base.notes[cell] != 0 || base.ruledOut[cell] != 0 -> base.copy(
            notes = base.notes.replaced(cell, 0),
            ruledOut = base.ruledOut.replaced(cell, 0),
            history = base.history + base.snapshot(cell),
        )
        else -> base
    }
}

/**
 * Reverts the last board mutation (a pending placement simply disappears) and selects the cell it
 * affected. Counted mistakes and used hints are not refunded.
 */
fun GameState.undo(): GameState {
    if (solved || history.isEmpty()) return this
    val last = history.last()
    return copy(
        values = last.values,
        notes = last.notes,
        ruledOut = last.ruledOut,
        selected = last.cell,
        padFocus = 0,
        history = history.dropLast(1),
        pendingPlacement = null,
    )
}

/**
 * Reveals one cell: the selected cell if it is editable and empty or wrong, otherwise the engine's
 * pick ([Hints.nextHint]: a wrong cell, then a single, then any empty cell). The revealed cell is
 * selected. Commits first. Costs a hint, which undo does not refund.
 */
fun GameState.hint(): GameState {
    if (solved || hintsLeft <= 0) return this
    val base = commitPending()
    val sel = base.selected
    val target = if (base.isEditable(sel) && base.values[sel] != base.solution[sel]) {
        sel
    } else {
        Hints.nextHint(base.values.toIntArray(), base.solution.toIntArray())
    }
    if (target < 0) return base
    val digit = base.solution[target]
    return base.copy(
        values = base.values.replaced(target, digit),
        notes = peerNotesRemoved(base.notes, target, digit),
        ruledOut = ruledOutAfterPlacing(base.ruledOut, target, digit),
        selected = target,
        padFocus = 0,
        hintsLeft = base.hintsLeft - 1,
        hintsUsed = base.hintsUsed + 1,
        history = base.history + base.snapshot(target),
    ).withSolvedCheck()
}
