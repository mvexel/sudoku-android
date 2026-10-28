package net.vanexel.sudoku.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import net.vanexel.sudoku.DigitOutcome
import net.vanexel.sudoku.GameViewModel
import net.vanexel.sudoku.data.Settings
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.game.GameState
import net.vanexel.sudoku.game.boardView
import net.vanexel.sudoku.game.cellDescription
import net.vanexel.sudoku.game.focusAnnouncement
import net.vanexel.sudoku.game.formatElapsed
import net.vanexel.sudoku.game.hasNote
import net.vanexel.sudoku.game.puzzleTitle
import net.vanexel.sudoku.game.spokenElapsed
import net.vanexel.sudoku.ui.theme.SudokuTheme

private const val ENTRY_TIP = "Tip: tap a digit twice to pencil it in. Long-press to toggle a pencil mark."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    vm: GameViewModel,
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onBack: () -> Unit,
    onBrowse: (Difficulty) -> Unit,
    onNext: (Difficulty, Int) -> Unit,
) {
    val state by vm.state.collectAsState()
    val win by vm.win.collectAsState()
    val colors = SudokuTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Timer runs only while resumed; save on stop; commit a pending placement on pause.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                Lifecycle.Event.ON_STOP -> vm.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onPause()
        }
    }

    LaunchedEffect(Unit) {
        if (vm.consumeEntryTip()) {
            snackbar.showSnackbar(
                message = ENTRY_TIP,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        }
    }

    Scaffold(
        containerColor = colors.screenBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val st = state
                    Text(
                        text = if (st != null) puzzleTitle(st.level, st.index) else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TimerText(vm.elapsedMs)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuOpen = false
                                    showSettings = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Restart") },
                                enabled = state?.solved == false,
                                onClick = {
                                    menuOpen = false
                                    confirmRestart = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.screenBackground,
                ),
            )
        },
    ) { padding ->
        val st = state
        if (st == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            GameContent(
                st = st,
                settings = settings,
                vm = vm,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }

    val w = win
    if (w != null) {
        WinDialog(
            info = w,
            showMistakes = settings.highlightMistakes,
            onBrowse = { onBrowse(w.level) },
            onNext = { onNext(w.level, w.index) },
        )
    }
    if (showSettings) {
        SettingsDialog(settings = settings, onChange = onSettingsChange, onDismiss = { showSettings = false })
    }
    if (confirmRestart) {
        RestartDialog(
            onConfirm = {
                confirmRestart = false
                vm.restart()
            },
            onDismiss = { confirmRestart = false },
        )
    }
}

@Composable
private fun TimerText(elapsed: StateFlow<Long>) {
    val ms by elapsed.collectAsState()
    val spoken = "Elapsed time " + spokenElapsed(ms)
    Text(
        text = formatElapsed(ms),
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    )
}

@Composable
private fun GameContent(st: GameState, settings: Settings, vm: GameViewModel, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val bv = remember(st, settings) {
        boardView(st, settings.highlightMistakes, settings.highlightSameDigits, settings.helperLines)
    }

    // TalkBack: announce focus changes.
    val focusDigit = bv.focus?.digit ?: 0
    LaunchedEffect(focusDigit) {
        val f = bv.focus
        if (f != null) view.announceForAccessibility(focusAnnouncement(f))
    }

    val onCellTap: (Int) -> Unit = remember(vm) { { cell: Int -> vm.selectCell(cell) } }
    val onDigit: (Int) -> Unit = remember(vm, settings) {
        { d: Int ->
            when (vm.pressDigit(d)) {
                DigitOutcome.VALUE -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val now = vm.state.value
                    if (now != null && now.selected >= 0) {
                        val nv = boardView(now, settings.highlightMistakes, settings.highlightSameDigits, settings.helperLines)
                        view.announceForAccessibility(cellDescription(now, nv, now.selected))
                    }
                }
                DigitOutcome.NOTE -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                DigitOutcome.CONVERTED_TO_NOTE -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.announceForAccessibility("Note $d added")
                }
                DigitOutcome.RULE_OUT -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    view.announceForAccessibility(ruleOutAnnouncement(vm, d))
                }
                DigitOutcome.REJECTED -> view.rejectHaptic()
                DigitOutcome.FOCUS, DigitOutcome.NONE -> Unit
            }
        }
    }
    val onRuleOutDigit: (Int) -> Unit = remember(vm) {
        { d: Int ->
            if (vm.ruleOutDigit(d) == DigitOutcome.RULE_OUT) {
                view.announceForAccessibility(ruleOutAnnouncement(vm, d))
            }
        }
    }
    val onLongDigit: (Int) -> Unit = remember(vm) {
        { d: Int ->
            when (vm.longPressDigit(d)) {
                DigitOutcome.NOTE -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                DigitOutcome.REJECTED -> view.rejectHaptic()
                else -> Unit
            }
        }
    }

    // A win plays the long-press haptic once.
    LaunchedEffect(st.solved) {
        if (st.solved) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BoxWithConstraints(
        modifier.pointerInput(vm) {
            // Taps not consumed by the board, keys or buttons: deselect.
            detectTapGestures(onTap = { vm.deselect() })
        }
    ) {
        val landscape = maxWidth > maxHeight
        if (!landscape) {
            // Below the board: 16 + 64 action row + 8 + 120 pad + 16, plus the 8dp gap above it.
            val boardSide = minOf(maxWidth - 16.dp, maxHeight - 232.dp).coerceAtLeast(120.dp)
            val padWidth = maxWidth - 16.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                SudokuBoard(st, bv, onCellTap, Modifier.size(boardSide))
                Spacer(Modifier.height(16.dp))
                ActionRow(st, settings.ruleOutMarks, vm, haptics)
                Spacer(Modifier.height(8.dp))
                NumberPad(st, settings.ruleOutMarks, padWidth, onDigit, onLongDigit, onRuleOutDigit)
                Spacer(Modifier.height(16.dp))
            }
        } else {
            val boardSide = (maxHeight - 16.dp).coerceAtLeast(120.dp)
            val columnWidth = minOf(360.dp, maxWidth - boardSide - 40.dp).coerceAtLeast(200.dp)
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                SudokuBoard(st, bv, onCellTap, Modifier.size(boardSide))
                Column(Modifier.width(columnWidth)) {
                    ActionRow(st, settings.ruleOutMarks, vm, haptics)
                    Spacer(Modifier.height(8.dp))
                    NumberPad(st, settings.ruleOutMarks, columnWidth, onDigit, onLongDigit, onRuleOutDigit)
                }
            }
        }
    }
}

/** "7 ruled out" or "7 allowed", from the selected cell after a rule-out toggle. */
private fun ruleOutAnnouncement(vm: GameViewModel, digit: Int): String {
    val now = vm.state.value
    val on = now != null && now.selected >= 0 && hasNote(now.ruledOut[now.selected], digit)
    return if (on) "$digit ruled out" else "$digit allowed"
}

private fun View.rejectHaptic() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

// ---- action row ----

@Composable
private fun ActionRow(st: GameState, ruleOutMarks: Boolean, vm: GameViewModel, haptics: HapticFeedback) {
    val colors = SudokuTheme.colors
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionTile(
            label = "Undo",
            description = "Undo",
            enabled = st.canUndo,
            color = idle,
            onClick = { vm.undo() },
        ) {
            Icon(SudokuIcons.Undo, contentDescription = null, tint = idle)
        }
        ActionTile(
            label = "Erase",
            description = "Erase",
            enabled = st.canErase,
            color = idle,
            onClick = {
                if (vm.erase()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
        ) {
            Icon(SudokuIcons.Backspace, contentDescription = null, tint = idle)
        }
        NotesTile(
            notes = st.notesMode,
            ruleOut = st.ruleOutMode && ruleOutMarks,
            enabled = !st.solved,
            onToggle = { vm.toggleNotesMode() },
        )
        val hintDescription = "Hint, ${st.hintsLeft} remaining"
        ActionTile(
            label = "Hint",
            description = hintDescription,
            enabled = st.canHint,
            color = idle,
            onClick = {
                if (vm.hint()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
        ) {
            BadgedBox(
                badge = {
                    Badge(containerColor = colors.accent, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text("${st.hintsLeft}")
                    }
                },
            ) {
                Icon(SudokuIcons.Lightbulb, contentDescription = null, tint = idle)
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    description: String,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The Notes button; with rule-out marks on it has a third state, Rule out (slashed pencil). */
@Composable
private fun NotesTile(notes: Boolean, ruleOut: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = SudokuTheme.colors
    val on = notes || ruleOut
    val label = if (ruleOut) "Rule out" else "Notes"
    val tint = if (on) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.38f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) colors.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = tint,
                    modifier = if (ruleOut) Modifier.slashed(tint, 2.dp, extent = 1.1f, falling = true) else Modifier,
                )
            }
            if (on) {
                Text(
                    text = "ON",
                    color = colors.accent,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

// ---- number pad ----

/** Two rows (1-5, 6-9); key width = (width - 4 * 8dp) / 5, height 56dp. */
@Composable
private fun NumberPad(
    st: GameState,
    ruleOutMarks: Boolean,
    width: Dp,
    onDigit: (Int) -> Unit,
    onLongDigit: (Int) -> Unit,
    onRuleOutDigit: (Int) -> Unit,
) {
    val keyWidth = (width - 32.dp) / 5
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in 1..5) PadKey(st, ruleOutMarks, d, keyWidth, onDigit, onLongDigit, onRuleOutDigit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (d in 6..9) PadKey(st, ruleOutMarks, d, keyWidth, onDigit, onLongDigit, onRuleOutDigit)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadKey(
    st: GameState,
    ruleOutMarks: Boolean,
    digit: Int,
    keyWidth: Dp,
    onDigit: (Int) -> Unit,
    onLongDigit: (Int) -> Unit,
    onRuleOutDigit: (Int) -> Unit,
) {
    val colors = SudokuTheme.colors
    val remaining = st.remaining(digit)
    val enabled = st.isPadKeyEnabled(digit)
    val sel = st.selected
    val canNote = enabled && st.isEditable(sel) && st.values[sel] == 0
    val noteOn = canNote && hasNote(st.notes[sel], digit)
    val description = if (remaining <= 0 && !enabled) "$digit, all placed" else "$digit, $remaining remaining"
    val noteActionLabel = if (noteOn) "Remove note $digit" else "Add note $digit"
    val ruledOut = canNote && hasNote(st.ruledOut[sel], digit)
    val ruleOutActionLabel = if (ruledOut) "Allow $digit" else "Rule out $digit"
    val ruleOutMode = st.ruleOutMode && ruleOutMarks

    Box(
        modifier = Modifier
            .size(width = keyWidth, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClick = { onLongDigit(digit) },
                onClick = { onDigit(digit) },
            )
            .semantics {
                contentDescription = description
                if (canNote) {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(noteActionLabel) {
                            onLongDigit(digit)
                            true
                        },
                        if (ruleOutMarks) {
                            CustomAccessibilityAction(ruleOutActionLabel) {
                                onRuleOutDigit(digit)
                                true
                            }
                        } else {
                            null
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.38f)
                .clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val keyColor = if (st.notesMode || ruleOutMode) colors.noteDigit else colors.userDigit
            Text(
                text = digit.toString(),
                color = keyColor,
                fontSize = 28.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium,
                modifier = if (ruleOutMode) Modifier.slashed(keyColor, 2.dp, extent = 0.85f) else Modifier,
            )
            if (enabled) {
                Text(
                    text = remaining.coerceAtLeast(0).toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}
