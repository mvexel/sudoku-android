package net.vanexel.sudoku

import android.app.Application
import android.content.Context
import java.io.File
import net.vanexel.sudoku.data.BankRepository
import net.vanexel.sudoku.data.ProgressStore
import net.vanexel.sudoku.data.SettingsStore

class SudokuApplication : Application() {
    val services: Services by lazy { Services(this) }
}

/** App-wide singletons, shared by the view models. */
class Services(context: Context) {
    val banks = BankRepository(context.assets)
    val progress = ProgressStore(File(context.filesDir, "progress"))
    val settings = SettingsStore(context)
}
