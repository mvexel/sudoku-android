package net.vanexel.sudoku.game

import net.vanexel.sudoku.engine.Rules

/** Cell background, in layering priority order (highest last). Only one is painted. */
enum class CellBackground { DEFAULT, PEER, EXCLUDED, SAME_DIGIT, MISTAKE, SELECTED }

/** Everything the board needs to paint, derived purely from the state and settings. */
data class BoardView(
    val backgrounds: List<CellBackground>,
    /** Filled cells whose value is duplicated in a row, column or box (always shown). */
    val conflicts: List<Boolean>,
    /**
     * User cells whose value differs from the solution; only when mistake highlighting is on, and
     * not for a pending (uncommitted) placement.
     */
    val mistakes: List<Boolean>,
    /** Notes of this digit are drawn in the accent color, bold; 0 for none. */
    val highlightedNote: Int,
    /** Active digit focus (shading on and a focused digit), or null. */
    val focus: DigitFocus?,
)

fun boardView(
    state: GameState,
    highlightMistakes: Boolean,
    highlightSameDigits: Boolean,
    helperLines: Boolean,
): BoardView {
    val values = state.values
    val sel = state.selected
    val conflicts = Rules.conflicts(values.toIntArray()).toList()
    val pendingCell = state.pendingPlacement?.cell ?: NO_CELL
    val mistakes = List(Peers.CELLS) { i ->
        highlightMistakes && i != pendingCell && state.givens[i] == 0 &&
            values[i] != 0 && values[i] != state.solution[i]
    }

    val focusedDigit = state.focusedDigit
    val focus = if (helperLines && focusedDigit != 0) digitFocus(values, focusedDigit, state.ruledOut) else null
    val sameDigit = when {
        focus != null -> focus.digit
        highlightSameDigits -> focusedDigit
        else -> 0
    }

    val backgrounds = List(Peers.CELLS) { i ->
        when {
            i == sel -> CellBackground.SELECTED
            mistakes[i] -> CellBackground.MISTAKE
            sameDigit != 0 && values[i] == sameDigit -> CellBackground.SAME_DIGIT
            focus != null && focus.excluded[i] -> CellBackground.EXCLUDED
            focus == null && sel != NO_CELL && Peers.isPeer(sel, i) -> CellBackground.PEER
            else -> CellBackground.DEFAULT
        }
    }
    return BoardView(backgrounds, conflicts, mistakes, sameDigit, focus)
}

/** TalkBack description of a cell, with 1-based row and column. */
fun cellDescription(state: GameState, view: BoardView, cell: Int): String {
    val sb = StringBuilder("Row ${Peers.row(cell) + 1}, column ${Peers.col(cell) + 1}, ")
    val value = state.values[cell]
    when {
        value != 0 && state.givens[cell] != 0 -> {
            sb.append(value).append(", given")
            if (view.conflicts[cell]) sb.append(", conflict")
        }
        value != 0 -> {
            sb.append(value)
            if (view.conflicts[cell]) sb.append(", conflict")
            if (view.mistakes[cell]) sb.append(", incorrect")
        }
        else -> {
            sb.append("empty")
            val notes = state.notes[cell]
            if (notes != 0) {
                sb.append(", notes")
                for (d in 1..9) if (hasNote(notes, d)) sb.append(' ').append(d)
            }
            val ruledOut = state.ruledOut[cell]
            if (ruledOut != 0) {
                sb.append(", ruled out")
                for (d in 1..9) if (hasNote(ruledOut, d)) sb.append(' ').append(d)
            }
            val focus = view.focus
            if (focus != null && focus.excluded[cell] && !hasNote(ruledOut, focus.digit)) {
                sb.append(", ").append(focus.digit).append(" excluded")
            }
        }
    }
    return sb.toString()
}

/** TalkBack announcement after the focus changes, e.g. "Focused on 1: 14 cells possible". */
fun focusAnnouncement(focus: DigitFocus): String {
    val n = focus.candidateCount
    return "Focused on ${focus.digit}: $n ${if (n == 1) "cell" else "cells"} possible"
}
