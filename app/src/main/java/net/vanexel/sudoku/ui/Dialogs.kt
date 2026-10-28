package net.vanexel.sudoku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.vanexel.sudoku.WinInfo
import net.vanexel.sudoku.data.Settings
import net.vanexel.sudoku.game.formatElapsed
import net.vanexel.sudoku.game.puzzleTitle
import net.vanexel.sudoku.ui.theme.SudokuTheme

/** Not dismissible by an outside tap; system back acts as Browse. */
@Composable
fun WinDialog(
    info: WinInfo,
    showMistakes: Boolean,
    onBrowse: () -> Unit,
    onNext: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBrowse,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Solved!", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = puzzleTitle(info.level, info.index),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (info.technique.isNotEmpty()) Text("Needed: ${info.technique}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Time ${formatElapsed(info.timeMs)}")
                    if (info.newBest) {
                        Text(
                            text = "  New best",
                            color = SudokuTheme.colors.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text("Hints used: ${info.hintsUsed}")
                if (showMistakes) Text("Mistakes: ${info.mistakes}")
            }
        },
        dismissButton = {
            TextButton(onClick = onBrowse) { Text("Browse") }
        },
        confirmButton = {
            Button(onClick = onNext) { Text("Next puzzle") }
        },
    )
}

@Composable
fun SettingsDialog(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                SwitchRow("Highlight mistakes", settings.highlightMistakes) {
                    onChange(settings.copy(highlightMistakes = it))
                }
                SwitchRow("Highlight same digits", settings.highlightSameDigits) {
                    onChange(settings.copy(highlightSameDigits = it))
                }
                SwitchRow("Shade where a digit can't go", settings.helperLines) {
                    onChange(settings.copy(helperLines = it))
                }
                SwitchRow("Tap digit twice for note", settings.tapTwiceForNote) {
                    onChange(settings.copy(tapTwiceForNote = it))
                }
                SwitchRow("Rule-out marks (Notes button)", settings.ruleOutMarks) {
                    onChange(settings.copy(ruleOutMarks = it))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun RestartDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restart puzzle?") },
        text = { Text("Your progress on this puzzle will be cleared. The timer restarts from zero.") },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Restart") }
        },
    )
}
