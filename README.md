# Sudoku

A native Android Sudoku app (Kotlin, Jetpack Compose, Material 3) for advanced players. It has three
levels — **Hard**, **Expert** and **Extreme** — rated by the hardest human technique each puzzle
needs: X-wings and hidden pairs or triples for Hard; swordfish, jellyfish and XY/XYZ-wings for
Expert; and W-wings, simple coloring, X-chains, XY-chains and unique rectangles for Extreme. Every
puzzle has a unique solution, and every one can be solved by logic alone, without guessing.

## Layout

- `engine/` — pure-Java puzzle engine: the solver, the technique-based rater, the generator, the
  puzzle bank builder, hints and rules. It has its own JUnit tests.
- `app/` — the Android app.
- `docs/DESIGN.md` — the UI spec. `docs/UX-REVIEW-input.md` — a review of the tap-twice entry model.
- `engine-check/` — a standalone harness for the engine (`javac`/`java`, no Gradle needed).

## Puzzle bank

All puzzles are generated at build time by `:app:generatePuzzleBank`. The task runs
`BankBuilder` from `:engine` and packages `puzzles/{hard,expert,extreme}.txt` as assets. The
output is deterministic and cached, so only the first clean build pays for it (about half a minute
on a laptop for the default 500 puzzles per level).

```
./gradlew installDebug -PpuzzleCount=1000 -PpuzzleSeed=20261027
```

Progress is keyed by a hash of each puzzle's givens, so changing the count or the seed doesn't mix
up which puzzles you've solved. Puzzles that are no longer in the bank just stop appearing.

## Build and install over wireless debugging

The project uses JDK 17 (pinned in `mise.toml`) and the Android SDK at `~/Android/Sdk`.

1. On the phone, open Settings → Developer options → **Wireless debugging**, turn it on, and tap
   **Pair device with pairing code**.
2. On the computer, on the same Wi-Fi network:
   ```
   adb pair <phone-ip>:<pairing-port>     # enter the 6-digit code
   adb connect <phone-ip>:<port>          # the port shown on the Wireless debugging screen (not the pairing port)
   adb devices                            # the phone should be listed
   ```
3. Build and install:
   ```
   cd ~/dev/sudoku
   ./gradlew installDebug
   ```

If Gradle can't find the SDK, create `local.properties` containing `sdk.dir=/home/mvexel/Android/Sdk`.

## Tests

```
./gradlew :engine:test :app:testDebugUnitTest
```

## Entry model (summary)

- Tap a cell, then tap a digit to place it. Tap the same digit again to turn it into a note
  instead. The first tap is provisional: the digit isn't cleared from peer notes or checked for a
  mistake until you move on.
- Long-press a digit to toggle a note. The Notes button in the action row cycles Off → Notes →
  Rule out: in Notes every tap is a note, in Rule out every tap marks a digit as impossible in the
  cell (drawn slashed).
- With no cell selected, or after tapping a cell that holds a digit, **digit focus** shades every
  cell where that digit can no longer go, including cells where you ruled it out.
- Completing a row, column, box or all nine of a digit plays a short pulse.

## License

[WTFPL](LICENSE): Do What The Fuck You Want To Public License, version 2.
