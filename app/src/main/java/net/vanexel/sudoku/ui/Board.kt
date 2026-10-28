package net.vanexel.sudoku.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import net.vanexel.sudoku.game.BoardView
import net.vanexel.sudoku.game.CellBackground
import net.vanexel.sudoku.game.Completion
import net.vanexel.sudoku.game.GameState
import net.vanexel.sudoku.game.NO_CELL
import net.vanexel.sudoku.game.Peers
import net.vanexel.sudoku.game.cellDescription
import net.vanexel.sudoku.game.completions
import net.vanexel.sudoku.game.hasNote
import net.vanexel.sudoku.ui.theme.SudokuColors
import net.vanexel.sudoku.ui.theme.SudokuTheme
import kotlin.math.abs

private const val COLOR_MS = 120
private const val WAVE_STAGGER_MS = 20
private const val WAVE_FLASH_MS = 300
private const val WAVE_TOTAL_MS = 16 * WAVE_STAGGER_MS + WAVE_FLASH_MS
private const val PULSE_STAGGER_MS = 35
private const val PULSE_FLASH_MS = 380
private const val PULSE_TOTAL_MS = 8 * PULSE_STAGGER_MS + PULSE_FLASH_MS
private const val PULSE_ALPHA = 0.45f

/**
 * The 9x9 board, in three layers: cell backgrounds, then one Canvas with the completion pulse,
 * grid lines and the selection border, then digits and notes. The whole board is one tap surface;
 * each cell is also its own semantics node for TalkBack. Size it with a square [modifier].
 */
@Composable
fun SudokuBoard(
    state: GameState,
    view: BoardView,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SudokuTheme.colors
    val currentOnTap by rememberUpdatedState(onCellTap)

    // Win wave: each cell flashes with a stagger by (row + col).
    val wave = remember { Animatable(0f) }
    LaunchedEffect(state.solved) {
        if (state.solved) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(durationMillis = WAVE_TOTAL_MS, easing = LinearEasing))
        } else {
            wave.snapTo(0f)
        }
    }

    // Completion pulse: a newly completed row, column, box or digit flashes outward from the cell
    // just filled. Only for new placements (history grew), not undo, and not on the final move.
    val androidView = LocalView.current
    val pulse = remember { Animatable(1f) }
    var pulsing by remember { mutableStateOf(emptyList<Completion>()) }
    var lastValues by remember { mutableStateOf(state.values) }
    var lastHistory by remember { mutableIntStateOf(state.history.size) }
    LaunchedEffect(state.values) {
        val found = if (state.history.size > lastHistory && !state.solved) {
            completions(lastValues, state.values)
        } else {
            emptyList()
        }
        lastValues = state.values
        lastHistory = state.history.size
        if (found.isNotEmpty()) {
            androidView.announceForAccessibility(found.joinToString(". ") { it.announcement })
            pulsing = found
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = PULSE_TOTAL_MS, easing = LinearEasing))
            pulsing = emptyList()
        }
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.boardBackground)
    ) {
        val density = LocalDensity.current
        val cellPx = constraints.maxWidth / 9f
        val digitSize = with(density) { (cellPx * 0.6f).toSp() }
        val noteSize = with(density) { (cellPx * 0.26f).toSp() }
        val cellDp = with(density) { cellPx.toDp() }

        // Layer 1: cell backgrounds.
        CellGrid(Modifier.fillMaxSize()) { cell ->
            val waveMs = wave.value * WAVE_TOTAL_MS
            val start = (Peers.row(cell) + Peers.col(cell)) * WAVE_STAGGER_MS
            val flashing = state.solved && wave.value < 1f && waveMs >= start && waveMs < start + WAVE_FLASH_MS
            val target = if (flashing) colors.sameDigitCell else backgroundColor(view.backgrounds[cell], colors)
            val color by animateColorAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = COLOR_MS),
                label = "cellBackground",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(color)
            )
        }

        // Layer 2: completion pulse, grid lines and selection border.
        val selected = state.selected
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 9f
            val pulseMs = pulse.value * PULSE_TOTAL_MS
            for (completion in pulsing) {
                for (c in completion.cells) {
                    val distance = maxOf(
                        abs(Peers.row(c) - Peers.row(completion.origin)),
                        abs(Peers.col(c) - Peers.col(completion.origin)),
                    )
                    val t = (pulseMs - distance * PULSE_STAGGER_MS) / PULSE_FLASH_MS
                    if (t <= 0f || t >= 1f) continue
                    // Rise fast, fade slowly.
                    val a = if (t < 0.3f) t / 0.3f else (1f - t) / 0.7f
                    drawRect(
                        color = colors.accent,
                        topLeft = Offset(Peers.col(c) * cell, Peers.row(c) * cell),
                        size = Size(cell, cell),
                        alpha = PULSE_ALPHA * a,
                    )
                }
            }
            val thin = 1.dp.toPx()
            for (i in 1..8) {
                if (i % 3 == 0) continue
                val p = i * cell
                drawLine(colors.gridLineThin, Offset(p, 0f), Offset(p, size.height), strokeWidth = thin)
                drawLine(colors.gridLineThin, Offset(0f, p), Offset(size.width, p), strokeWidth = thin)
            }
            val thick = 2.dp.toPx()
            for (i in listOf(3, 6)) {
                val p = i * cell
                drawLine(colors.gridLineThick, Offset(p, 0f), Offset(p, size.height), strokeWidth = thick)
                drawLine(colors.gridLineThick, Offset(0f, p), Offset(size.width, p), strokeWidth = thick)
            }
            val border = 1.5.dp.toPx()
            drawRoundRect(
                color = colors.gridLineThick,
                topLeft = Offset(border / 2f, border / 2f),
                size = Size(size.width - border, size.height - border),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = border),
            )
            if (selected != NO_CELL) {
                val bw = 2.dp.toPx()
                drawRect(
                    color = colors.accent,
                    topLeft = Offset(Peers.col(selected) * cell + bw / 2f, Peers.row(selected) * cell + bw / 2f),
                    size = Size(cell - bw, cell - bw),
                    style = Stroke(width = bw),
                )
            }
        }

        // Layer 3: digits and notes, the tap surface, and per-cell semantics.
        CellGrid(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset ->
                        val c = (offset.x / (size.width / 9f)).toInt().coerceIn(0, 8)
                        val r = (offset.y / (size.height / 9f)).toInt().coerceIn(0, 8)
                        currentOnTap(r * 9 + c)
                    })
                }
        ) { cell ->
            CellContent(
                cell = cell,
                state = state,
                view = view,
                digitSize = digitSize,
                noteSize = noteSize,
                cellDp = cellDp,
                onTap = { currentOnTap(it) },
            )
        }
    }
}

@Composable
private fun CellGrid(modifier: Modifier, content: @Composable RowScope.(Int) -> Unit) {
    Column(modifier) {
        for (r in 0 until 9) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (c in 0 until 9) {
                    content(this, r * 9 + c)
                }
            }
        }
    }
}

private fun backgroundColor(bg: CellBackground, colors: SudokuColors): Color = when (bg) {
    CellBackground.DEFAULT -> colors.boardBackground
    CellBackground.PEER -> colors.peerCell
    CellBackground.EXCLUDED -> colors.excludedCell
    CellBackground.SAME_DIGIT -> colors.sameDigitCell
    CellBackground.MISTAKE -> colors.mistakeCell
    CellBackground.SELECTED -> colors.selectedCell
}

@Composable
private fun RowScope.CellContent(
    cell: Int,
    state: GameState,
    view: BoardView,
    digitSize: TextUnit,
    noteSize: TextUnit,
    cellDp: Dp,
    onTap: (Int) -> Unit,
) {
    val colors = SudokuTheme.colors
    val value = state.values[cell]
    val given = state.givens[cell] != 0
    val description = cellDescription(state, view, cell)
    val isSelected = cell == state.selected
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
                onClick(label = null) {
                    onTap(cell)
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (value != 0) {
            val conflict = view.conflicts[cell]
            val color = when {
                (!given && conflict) || view.mistakes[cell] -> colors.conflictDigit
                given -> colors.givenDigit
                else -> colors.userDigit
            }
            Text(
                text = value.toString(),
                color = color,
                fontSize = digitSize,
                lineHeight = digitSize,
                fontWeight = if (given) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (given && conflict) {
                // A conflicting given keeps its color but gets an underline (never color alone).
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = cellDp * 0.12f)
                        .width(cellDp * 0.4f)
                        .height(1.5.dp)
                        .background(colors.conflictDigit)
                )
            }
        } else if (state.notes[cell] != 0 || state.ruledOut[cell] != 0) {
            NotesGrid(state.notes[cell], state.ruledOut[cell], view.highlightedNote, noteSize, colors)
        }
    }
}

@Composable
private fun NotesGrid(notes: Int, ruledOut: Int, highlighted: Int, noteSize: TextUnit, colors: SudokuColors) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
    ) {
        for (r in 0 until 3) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (c in 0 until 3) {
                    val d = r * 3 + c + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasNote(notes, d)) {
                            val hl = d == highlighted
                            Text(
                                text = d.toString(),
                                color = if (hl) colors.accent else colors.noteDigit,
                                fontSize = noteSize,
                                lineHeight = noteSize,
                                fontWeight = if (hl) FontWeight.Bold else FontWeight.Medium,
                            )
                        } else if (hasNote(ruledOut, d)) {
                            // Ruled out: plain and slashed, never highlighted (that style means "candidate").
                            Text(
                                text = d.toString(),
                                color = colors.noteDigit,
                                fontSize = noteSize,
                                lineHeight = noteSize,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.slashed(colors.noteDigit, 1.25.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws a diagonal slash (bottom-left to top-right, or top-left to bottom-right when [falling])
 * over the content, centered and [extent] times the content's height: the "ruled out" mark for
 * note digits, pad keys and the Notes button.
 */
internal fun Modifier.slashed(
    color: Color,
    width: Dp,
    extent: Float = 1.2f,
    falling: Boolean = false,
): Modifier = drawWithContent {
    drawContent()
    val half = size.height * extent / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dy = if (falling) -half * 0.7f else half * 0.7f
    drawLine(
        color = color,
        start = Offset(cx - half * 0.7f, cy + dy),
        end = Offset(cx + half * 0.7f, cy - dy),
        strokeWidth = width.toPx(),
        cap = StrokeCap.Round,
    )
}
