package net.vanexel.sudoku.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.vanexel.sudoku.engine.Difficulty

data class Settings(
    val highlightMistakes: Boolean = false,
    val highlightSameDigits: Boolean = true,
    val helperLines: Boolean = true,
    /** Tapping the digit a cell already shows turns it into a note. */
    val tapTwiceForNote: Boolean = true,
    /** The Notes button gets a third state, Rule out, for marking digits a cell can't hold. */
    val ruleOutMarks: Boolean = true,
)

/** App-wide settings in SharedPreferences, exposed as a StateFlow so changes apply immediately. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            highlightMistakes = prefs.getBoolean(KEY_MISTAKES, false),
            highlightSameDigits = prefs.getBoolean(KEY_SAME_DIGITS, true),
            helperLines = prefs.getBoolean(KEY_HELPER_LINES, true),
            tapTwiceForNote = prefs.getBoolean(KEY_TAP_TWICE, true),
            ruleOutMarks = prefs.getBoolean(KEY_RULE_OUT, true),
        )
    )
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(settings: Settings) {
        _settings.value = settings
        prefs.edit()
            .putBoolean(KEY_MISTAKES, settings.highlightMistakes)
            .putBoolean(KEY_SAME_DIGITS, settings.highlightSameDigits)
            .putBoolean(KEY_HELPER_LINES, settings.helperLines)
            .putBoolean(KEY_TAP_TWICE, settings.tapTwiceForNote)
            .putBoolean(KEY_RULE_OUT, settings.ruleOutMarks)
            .apply()
    }

    var lastTab: Difficulty
        get() {
            val name = prefs.getString(KEY_TAB, null) ?: return Difficulty.HARD
            return Difficulty.values().firstOrNull { it.name == name } ?: Difficulty.HARD
        }
        set(value) {
            prefs.edit().putString(KEY_TAB, value.name).apply()
        }

    /** Whether the one-time entry tip has been shown. */
    var entryTipSeen: Boolean
        get() = prefs.getBoolean(KEY_TIP_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TIP_SEEN, value).apply()
        }

    private companion object {
        const val KEY_MISTAKES = "highlight_mistakes"
        const val KEY_SAME_DIGITS = "highlight_same_digits"
        const val KEY_HELPER_LINES = "helper_lines"
        const val KEY_TAB = "last_tab"
        const val KEY_TAP_TWICE = "tap_twice_for_note"
        const val KEY_TIP_SEEN = "entry_tip_seen"
        const val KEY_RULE_OUT = "rule_out_marks"
    }
}
