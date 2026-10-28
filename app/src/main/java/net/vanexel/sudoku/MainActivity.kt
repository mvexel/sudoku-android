package net.vanexel.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.ui.GameScreen
import net.vanexel.sudoku.ui.HomeScreen
import net.vanexel.sudoku.ui.theme.SudokuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SudokuTheme {
                App()
            }
        }
    }
}

/** Two destinations, hoisted in [AppViewModel]; system back on the game returns home. */
@Composable
fun App(
    appVm: AppViewModel = viewModel(),
    gameVm: GameViewModel = viewModel(),
) {
    val settings by appVm.settings.collectAsState()

    val openPuzzle: (Difficulty, Int) -> Unit = { level, index ->
        gameVm.open(level, index)
        appVm.showGame()
    }
    val goHome: (Difficulty?) -> Unit = { tab ->
        gameVm.close()
        appVm.showHome(tab)
    }

    when (appVm.screen) {
        Screen.Home -> HomeScreen(vm = appVm, onOpenPuzzle = openPuzzle)
        Screen.Game -> {
            BackHandler { goHome(null) }
            GameScreen(
                vm = gameVm,
                settings = settings,
                onSettingsChange = { appVm.updateSettings(it) },
                onBack = { goHome(null) },
                onBrowse = { level -> goHome(level) },
                onNext = { level, index ->
                    val next = appVm.nextUnsolved(level, index)
                    if (next != null) openPuzzle(next.level, next.index) else goHome(level)
                },
            )
        }
    }
}
