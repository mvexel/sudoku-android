package net.vanexel.sudoku.data

import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.vanexel.sudoku.engine.Difficulty
import net.vanexel.sudoku.game.GameState
import net.vanexel.sudoku.game.NO_CELL
import net.vanexel.sudoku.game.Peers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class PuzzleStatus { NOT_STARTED, IN_PROGRESS, SOLVED }

/** Per-puzzle progress, keyed by puzzle id in [ProgressStore]. */
data class PuzzleProgress(
    val level: Difficulty,
    val solved: Boolean = false,
    val bestTimeMs: Long? = null,
    val attempts: Int = 0,
    val lastPlayed: Long = 0L,
    /** True while a saved game exists (also for a replay of a solved puzzle). */
    val hasSavedGame: Boolean = false,
    /** Elapsed time of the saved game, for the home tiles. */
    val elapsedMs: Long = 0L,
) {
    val status: PuzzleStatus
        get() = when {
            solved -> PuzzleStatus.SOLVED
            hasSavedGame -> PuzzleStatus.IN_PROGRESS
            else -> PuzzleStatus.NOT_STARTED
        }
}

/** The persisted part of an in-progress game. Givens and solution come from the bank; undo history is dropped. */
data class SavedGame(
    val values: List<Int>,
    val notes: List<Int>,
    val selected: Int,
    val notesMode: Boolean,
    val hintsLeft: Int,
    val hintsUsed: Int,
    val mistakes: Int,
    val elapsedMs: Long,
    val ruledOut: List<Int> = List(Peers.CELLS) { 0 },
    val ruleOutMode: Boolean = false,
) {
    /** Rebuilds the game, or null if the save does not fit these givens. */
    fun toState(base: GameState): GameState? {
        if (values.size != Peers.CELLS || notes.size != Peers.CELLS || ruledOut.size != Peers.CELLS) return null
        for (i in 0 until Peers.CELLS) {
            if (base.givens[i] != 0 && values[i] != base.givens[i]) return null
            if (values[i] !in 0..9) return null
        }
        return base.copy(
            values = values,
            notes = notes.map { it and 0x1FF },
            ruledOut = ruledOut.map { it and 0x1FF },
            selected = if (selected in 0 until Peers.CELLS) selected else NO_CELL,
            notesMode = notesMode && !ruleOutMode,
            ruleOutMode = ruleOutMode,
            hintsLeft = hintsLeft.coerceIn(0, base.hintsLeft),
            hintsUsed = hintsUsed.coerceAtLeast(0),
            mistakes = mistakes.coerceAtLeast(0),
        )
    }

    companion object {
        fun of(state: GameState, elapsedMs: Long) = SavedGame(
            values = state.values,
            notes = state.notes,
            selected = state.selected,
            notesMode = state.notesMode,
            hintsLeft = state.hintsLeft,
            hintsUsed = state.hintsUsed,
            mistakes = state.mistakes,
            elapsedMs = elapsedMs,
            ruledOut = state.ruledOut,
            ruleOutMode = state.ruleOutMode,
        )
    }
}

/**
 * Progress persistence: one index file (progress.json) with every puzzle's progress, plus one file
 * per in-progress game (games/<id>.json). All writes are atomic (temp file + rename) and serialized
 * by a mutex that is taken before switching to the IO dispatcher, so operations run in call order.
 */
class ProgressStore(private val dir: File) {
    private val mutex = Mutex()
    private val indexFile = File(dir, "progress.json")
    private val gamesDir = File(dir, "games")
    private var loaded = false

    private val _progress = MutableStateFlow<Map<String, PuzzleProgress>>(emptyMap())
    val progress: StateFlow<Map<String, PuzzleProgress>> = _progress.asStateFlow()

    suspend fun load(): Unit = mutex.withLock {
        if (loaded) return@withLock
        val map = withContext(Dispatchers.IO) { readIndex() }
        _progress.value = map
        loaded = true
    }

    /** A new attempt starts (first play, replay or restart): any old saved game is discarded. */
    suspend fun startNew(id: String, level: Difficulty): Unit = mutex.withLock {
        val now = System.currentTimeMillis()
        val updated = edit(id, level) {
            it.copy(attempts = it.attempts + 1, lastPlayed = now, hasSavedGame = false, elapsedMs = 0L)
        }
        withContext(Dispatchers.IO) {
            gameFile(id).delete()
            writeIndex(updated)
        }
    }

    suspend fun saveGame(id: String, level: Difficulty, game: SavedGame): Unit = mutex.withLock {
        val now = System.currentTimeMillis()
        val updated = edit(id, level) {
            it.copy(lastPlayed = now, hasSavedGame = true, elapsedMs = game.elapsedMs)
        }
        withContext(Dispatchers.IO) {
            try {
                gamesDir.mkdirs()
                writeAtomic(gameFile(id), gameToJson(game).toString())
            } catch (e: IOException) {
                Log.w(TAG, "could not save game $id", e)
            }
            writeIndex(updated)
        }
    }

    suspend fun loadGame(id: String): SavedGame? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = gameFile(id)
            if (!file.exists()) return@withContext null
            try {
                gameFromJson(JSONObject(file.readText()))
            } catch (e: IOException) {
                Log.w(TAG, "could not read game $id", e)
                null
            } catch (e: JSONException) {
                Log.w(TAG, "corrupt game $id", e)
                null
            }
        }
    }

    /** Marks the puzzle solved and deletes its saved game. Returns true if [timeMs] is a new best. */
    suspend fun markSolved(id: String, level: Difficulty, timeMs: Long): Boolean = mutex.withLock {
        val previousBest = _progress.value[id]?.bestTimeMs
        val newBest = previousBest == null || timeMs < previousBest
        val now = System.currentTimeMillis()
        val updated = edit(id, level) {
            it.copy(
                solved = true,
                bestTimeMs = if (newBest) timeMs else previousBest,
                lastPlayed = now,
                hasSavedGame = false,
                elapsedMs = 0L,
            )
        }
        withContext(Dispatchers.IO) {
            gameFile(id).delete()
            writeIndex(updated)
        }
        newBest
    }

    private inline fun edit(
        id: String,
        level: Difficulty,
        transform: (PuzzleProgress) -> PuzzleProgress,
    ): Map<String, PuzzleProgress> {
        val current = _progress.value
        val entry = transform(current[id] ?: PuzzleProgress(level = level))
        val updated = current + (id to entry)
        _progress.value = updated
        return updated
    }

    private fun gameFile(id: String) = File(gamesDir, "$id.json")

    // ---- index ----

    private fun readIndex(): Map<String, PuzzleProgress> {
        if (!indexFile.exists()) return emptyMap()
        return try {
            val root = JSONObject(indexFile.readText())
            val puzzles = root.optJSONObject("puzzles") ?: return emptyMap()
            val out = HashMap<String, PuzzleProgress>()
            val keys = puzzles.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = puzzles.optJSONObject(id) ?: continue
                val level = Difficulty.values().firstOrNull { it.name == o.optString("level") } ?: continue
                out[id] = PuzzleProgress(
                    level = level,
                    solved = o.optBoolean("solved", false),
                    bestTimeMs = if (o.has("best")) o.optLong("best") else null,
                    attempts = o.optInt("attempts", 0),
                    lastPlayed = o.optLong("last", 0L),
                    hasSavedGame = o.optBoolean("saved", false),
                    elapsedMs = o.optLong("elapsed", 0L),
                )
            }
            out
        } catch (e: IOException) {
            Log.w(TAG, "could not read progress", e)
            emptyMap()
        } catch (e: JSONException) {
            Log.w(TAG, "corrupt progress file", e)
            emptyMap()
        }
    }

    private fun writeIndex(map: Map<String, PuzzleProgress>) {
        val puzzles = JSONObject()
        for ((id, p) in map) {
            val o = JSONObject()
            o.put("level", p.level.name)
            o.put("solved", p.solved)
            val best = p.bestTimeMs
            if (best != null) o.put("best", best)
            o.put("attempts", p.attempts)
            o.put("last", p.lastPlayed)
            o.put("saved", p.hasSavedGame)
            o.put("elapsed", p.elapsedMs)
            puzzles.put(id, o)
        }
        val root = JSONObject()
        root.put("version", 1)
        root.put("puzzles", puzzles)
        try {
            dir.mkdirs()
            writeAtomic(indexFile, root.toString())
        } catch (e: IOException) {
            Log.w(TAG, "could not write progress", e)
        }
    }

    // ---- game files ----

    private fun gameToJson(g: SavedGame): JSONObject {
        val o = JSONObject()
        o.put("version", 1)
        o.put("values", intArrayJson(g.values))
        o.put("notes", intArrayJson(g.notes))
        o.put("selected", g.selected)
        o.put("notesMode", g.notesMode)
        o.put("hintsLeft", g.hintsLeft)
        o.put("hintsUsed", g.hintsUsed)
        o.put("mistakes", g.mistakes)
        o.put("elapsed", g.elapsedMs)
        o.put("ruledOut", intArrayJson(g.ruledOut))
        o.put("ruleOutMode", g.ruleOutMode)
        return o
    }

    private fun gameFromJson(o: JSONObject): SavedGame = SavedGame(
        values = intList(o.getJSONArray("values")),
        notes = intList(o.getJSONArray("notes")),
        selected = o.optInt("selected", NO_CELL),
        notesMode = o.optBoolean("notesMode", false),
        hintsLeft = o.optInt("hintsLeft", 0),
        hintsUsed = o.optInt("hintsUsed", 0),
        mistakes = o.optInt("mistakes", 0),
        elapsedMs = o.optLong("elapsed", 0L),
        // Added after version 1 shipped; older saves have neither field.
        ruledOut = o.optJSONArray("ruledOut")?.let { intList(it) } ?: List(Peers.CELLS) { 0 },
        ruleOutMode = o.optBoolean("ruleOutMode", false),
    )

    private fun intArrayJson(list: List<Int>): JSONArray {
        val a = JSONArray()
        for (v in list) a.put(v)
        return a
    }

    private fun intList(a: JSONArray): List<Int> = List(a.length()) { i -> a.getInt(i) }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("rename failed for $file")
        }
    }

    private companion object {
        const val TAG = "ProgressStore"
    }
}
