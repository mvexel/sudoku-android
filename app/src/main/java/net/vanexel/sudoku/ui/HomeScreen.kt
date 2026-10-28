package net.vanexel.sudoku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.vanexel.sudoku.AppViewModel
import net.vanexel.sudoku.PuzzleFilter
import net.vanexel.sudoku.continueTarget
import net.vanexel.sudoku.data.PuzzleProgress
import net.vanexel.sudoku.data.PuzzleStatus
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.game.blurb
import net.vanexel.sudoku.game.formatElapsed
import net.vanexel.sudoku.game.label
import net.vanexel.sudoku.game.spokenElapsed
import net.vanexel.sudoku.ui.theme.SudokuTheme

private data class TileItem(val index: Int, val id: String, val progress: PuzzleProgress?) {
    val status: PuzzleStatus get() = progress?.status ?: PuzzleStatus.NOT_STARTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenPuzzle: (Difficulty, Int) -> Unit,
) {
    val progress by vm.progress.collectAsState()
    val bankIds by vm.bankIds.collectAsState()
    val settings by vm.settings.collectAsState()
    val tab = vm.selectedTab
    val filter = vm.filter
    val colors = SudokuTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val levels = remember { Difficulty.values().toList() }
    val ids = bankIds[tab]
    val allTiles = remember(ids, progress) {
        ids?.mapIndexed { i, id -> TileItem(i, id, progress[id]) } ?: emptyList()
    }
    val tiles = remember(allTiles, filter) {
        when (filter) {
            PuzzleFilter.ALL -> allTiles
            PuzzleFilter.UNSOLVED -> allTiles.filter { it.status != PuzzleStatus.SOLVED }
            PuzzleFilter.IN_PROGRESS -> allTiles.filter { it.status == PuzzleStatus.IN_PROGRESS }
            PuzzleFilter.SOLVED -> allTiles.filter { it.status == PuzzleStatus.SOLVED }
        }
    }
    val solvedCounts = remember(bankIds, progress) {
        levels.associateWith { level -> bankIds[level]?.count { progress[it]?.solved == true } ?: 0 }
    }
    val inProgressCount = allTiles.count { it.status == PuzzleStatus.IN_PROGRESS }
    val continueRef = remember(progress, bankIds) { continueTarget(progress, bankIds) }

    // Scroll so the first unsolved puzzle is visible when a tab opens.
    val gridState = rememberLazyGridState()
    val loaded = ids != null
    LaunchedEffect(tab, loaded, filter) {
        if (loaded) {
            val pos = tiles.indexOfFirst { it.status != PuzzleStatus.SOLVED }
            gridState.scrollToItem(if (pos > 0) pos else 0)
        }
    }

    Scaffold(
        containerColor = colors.screenBackground,
        topBar = {
            TopAppBar(
                title = { Text("Sudoku", fontSize = 36.sp, fontWeight = FontWeight.SemiBold) },
                actions = {
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
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screenBackground),
            )
        },
        floatingActionButton = {
            val ref = continueRef
            if (ref != null) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenPuzzle(ref.level, ref.index) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Continue #${ref.index + 1}") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            PrimaryTabRow(
                selectedTabIndex = levels.indexOf(tab),
                containerColor = colors.screenBackground,
            ) {
                for (level in levels) {
                    val total = bankIds[level]?.size
                    Tab(
                        selected = level == tab,
                        onClick = { vm.selectTab(level) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(level.label())
                                Text(
                                    text = if (total != null) "${solvedCounts[level] ?: 0} / $total" else "…",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.alpha(0.7f),
                                )
                            }
                        },
                    )
                }
            }

            Text(
                text = "${tab.blurb()} · ${solvedCounts[tab] ?: 0} solved · $inProgressCount in progress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (f in PuzzleFilter.values()) {
                    FilterChip(
                        selected = f == filter,
                        onClick = { vm.selectFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }

            when {
                ids == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                tiles.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No puzzles here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tiles, key = { it.id }) { item ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PuzzleTile(
                                level = tab,
                                item = item,
                                onClick = { onOpenPuzzle(tab, item.index) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(settings = settings, onChange = { vm.updateSettings(it) }, onDismiss = { showSettings = false })
    }
}

@Composable
private fun PuzzleTile(level: Difficulty, item: TileItem, onClick: () -> Unit) {
    val colors = SudokuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val number = item.index + 1
    val p = item.progress
    val status = item.status
    val description = when (status) {
        PuzzleStatus.SOLVED -> {
            val best = p?.bestTimeMs
            if (best != null) "Puzzle $number, ${level.label()}, solved in ${spokenElapsed(best)}"
            else "Puzzle $number, ${level.label()}, solved"
        }
        PuzzleStatus.IN_PROGRESS -> "Puzzle $number, ${level.label()}, in progress"
        PuzzleStatus.NOT_STARTED -> "Puzzle $number, ${level.label()}, not started"
    }
    val background = if (status == PuzzleStatus.SOLVED) {
        colors.accent.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val subtitle = when (status) {
        PuzzleStatus.SOLVED -> p?.bestTimeMs?.let { formatElapsed(it) }
        PuzzleStatus.IN_PROGRESS -> formatElapsed(p?.elapsedMs ?: 0L)
        PuzzleStatus.NOT_STARTED -> null
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(shape)
            .background(background)
            .then(if (status == PuzzleStatus.IN_PROGRESS) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (status) {
            PuzzleStatus.IN_PROGRESS -> Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
            PuzzleStatus.SOLVED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(16.dp)
                    .clearAndSetSemantics { },
            )
            PuzzleStatus.NOT_STARTED -> Unit
        }
    }
}
