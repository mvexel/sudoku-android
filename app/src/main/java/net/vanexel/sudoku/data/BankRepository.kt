package net.vanexel.sudoku.data

import android.content.res.AssetManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.engine.Puzzle
import net.vanexel.sudoku.engine.PuzzleBank

/** Loads the per-level puzzle banks from assets once, off the main thread, and keeps them in memory. */
class BankRepository(private val assets: AssetManager) {
    private val mutex = Mutex()
    private val banks = HashMap<Difficulty, PuzzleBank>()

    suspend fun bank(level: Difficulty): PuzzleBank = mutex.withLock {
        banks[level] ?: withContext(Dispatchers.IO) {
            assets.open(assetPath(level)).use { input -> PuzzleBank.read(level, input) }
        }.also { banks[level] = it }
    }

    /** The full puzzle (solution recomputed by the engine). */
    suspend fun puzzle(level: Difficulty, index: Int): Puzzle {
        val bank = bank(level)
        return withContext(Dispatchers.Default) { bank.get(index) }
    }

    /** Stable ids of every entry, in bank order. */
    suspend fun ids(level: Difficulty): List<String> {
        val bank = bank(level)
        return withContext(Dispatchers.Default) { List(bank.size()) { i -> bank.getId(i) } }
    }

    private fun assetPath(level: Difficulty): String =
        "puzzles/" + level.name.lowercase(Locale.ROOT) + ".txt"
}
