# Sudoku — MVP Design Spec

Target: Android, Kotlin + Jetpack Compose, Material 3. Portrait phones 360–430dp wide are the
primary target; landscape gets a simple fallback. All sizes in dp/sp. Colors are given as tokens
(section 6) — never hard-code hex in composables.

---

## 1. Screens & navigation

The puzzles are a fixed, numbered bank generated at build time: a few hundred per level, with
levels Hard, Expert and Extreme. Players choose a specific puzzle and can always see which ones
they have solved. There are two destinations plus dialogs, and no bottom nav or drawer.

```
Home (puzzle browser) ──(tap puzzle tile)──► Game
  ▲                                           │  back / system back → Home (progress auto-saved)
  └────────────── [Win dialog] ◄──(solved)────┘   Win: [Browse] → Home | [Next puzzle] → Game
Game ──(⋮ menu)──► [Settings dialog] / [Restart confirm]
```

**Home: puzzle browser**
- TopAppBar with the title "Sudoku" and a ⋮ menu containing Settings.
- Directly below it, a `PrimaryTabRow` with three tabs: **Hard · Expert · Extreme**. Each tab label
  shows that level's progress as a second line, e.g. "Expert" over "42 / 300" (labelSmall, 70% alpha).
  The selected tab is remembered across launches.
- Under the tabs, a one-line summary (bodySmall, 16dp side padding), e.g. "Swordfish, XY/XYZ-wings ·
  42 solved · 3 in progress". The technique blurbs are Hard: "X-wings, hidden pairs, triples",
  Expert: "Swordfish, XY/XYZ-wings, jellyfish", Extreme: "Chains, coloring, W-wings, UR".
- A filter chip row: **All · Unsolved · In progress · Solved**, default All, using FilterChip with
  8dp gaps.
- Puzzle grid: `LazyVerticalGrid(GridCells.Adaptive(64.dp))`, 8dp spacing, 16dp side padding.
  Each tile is 64×64dp with 12dp corners and shows the puzzle number (titleMedium, tabular figures).
  The tile has three states, and each state differs in more than color:
  - *Unsolved*: `surfaceContainer` background, number in onSurface.
  - *In progress*: `surfaceContainer` background, a 2dp `accent` border, and a small filled dot
    (6dp, accent) in the top-right corner. Elapsed time appears under the number (labelSmall, e.g. "04:12").
  - *Solved*: `accent` at 16% alpha as the background, a check icon (16dp, accent) in the top-right,
    and the best time under the number (labelSmall). Solved puzzles stay replayable. Replaying one
    starts from scratch, and the best time updates only if the new time is lower.
- Tile content description: "Puzzle 17, Expert, solved in 12 minutes 30 seconds", "…, in progress",
  or "…, not started".
- A "Continue" extended FAB (bottom end) appears when any puzzle is in progress. It reads
  "Continue #17" and opens the most recently played in-progress puzzle.
- When the app opens on a tab, the grid scrolls so the first unsolved puzzle is visible.

**Progress model**: each puzzle has its own saved state, so several can be in progress at once.
Progress is keyed by the puzzle's stable id (a hash of its givens), not by its list position. The
stored fields are: status, best time, attempts, the last-played timestamp, and for an in-progress
puzzle the full game state.

**Win dialog** (AlertDialog, not dismissible by an outside tap)
- Title: "Solved!" (headlineSmall). The body shows "Expert #17", the hardest technique required
  ("Needed: X-wing"), the time ("Time 07:32", plus "New best" if it improved), hints used, and the
  mistake count (only when mistake highlighting was on).
- Buttons: [Browse] (TextButton → Home on the same tab) and [Next puzzle] (FilledButton → the next
  unsolved puzzle at the same level).
- On a win, the in-progress state is deleted and the puzzle is marked solved.

**Settings dialog** (from ⋮ menu): switch rows "Highlight mistakes" (default **off**),
"Highlight same digits" (default on), "Shade where a digit can't go" (default on; see §3 Digit
focus), "Tap digit twice for note" (default on; see §4) and "Rule-out marks (Notes button)"
(default on; see §4 Rule-out marks). Persisted app-wide (SharedPreferences), apply immediately.

---

## 2. Game screen layout (portrait)

Outer horizontal padding: **8dp**. Vertical stack, top to bottom:

| Region | Height | Notes |
|---|---|---|
| Top app bar | 64dp | `CenterAlignedTopAppBar` |
| gap | 8dp | |
| Board | = width − 16dp (square) | 344dp @360, 414dp @430 |
| gap | 16dp | |
| Action row | 64dp | 4 buttons, evenly spaced |
| gap | 8dp | |
| Number pad | 2 × 56dp + 8dp gap = 120dp | |
| bottom padding | 16dp + nav-bar inset | |

Small-height fallback: board side = `min(width − 16dp, availableHeight − 280dp)`, centered
horizontally. Everything else keeps its fixed height.

**Top bar**: navigation icon `ArrowBack` (→ Home); title = level and number ("Expert #17", titleMedium);
actions: timer text (`mm:ss`, or `h:mm:ss` past 1h; titleMedium, tabular figures
`fontFeatureSettings = "tnum"`), then ⋮ overflow (Settings, Restart).
- Timer runs only while the Game screen is resumed and the puzzle is unsolved; pauses on
  `ON_PAUSE`, stops on win. Tapping the timer does nothing in MVP.

**Action row** — four `IconButton`-style tiles, each 64dp wide × 64dp tall: 24dp icon over label
(labelMedium 12sp). Order: **Undo · Erase · Notes · Hint**.
- Undo (`Undo` icon): disabled when stack empty (38% alpha).
- Erase (`Backspace` outline): clears value, or notes and rule-out marks, of the selected
  non-given cell; disabled when selection is a given or empty.
- Notes (`Edit` pencil): entry-mode button. With rule-out marks on it cycles **Off → Notes → Rule
  out → Off**; with them off it toggles Off ↔ Notes. **On** (Notes or Rule out) = icon + label in
  `accent`, 40×28dp pill behind icon in accent at 16% alpha, plus small "ON" badge (10sp)
  top-right. In Rule out the label reads "Rule out" and a 2dp slash (top-left to bottom-right)
  crosses the pencil. **Off** = onSurfaceVariant, no pill.
- Hint (`Lightbulb`): Material `BadgedBox` with remaining count (3 → 0). At 0, disabled.

**Number pad — 2 rows (1–5, 6–9), chosen over 1 row of 9.** Justification: a 9-wide row at 360dp
gives ~38dp-wide keys, below the 48dp touch-target minimum; 2 rows give ~64dp × 56dp keys at every
supported width, and keep the pad below the thumb arc.
- Row 1: 5 keys; row 2: 4 keys centered (same key width as row 1). Key width =
  `(width − 16dp − 4 × 8dp) / 5`; height 56dp; 8dp gaps; shape 12dp rounded; container
  `surfaceContainerHigh`.
- Key content: digit (28sp Medium, `userDigit` color — or `noteDigit` when Notes or Rule out is
  on; in Rule out the digit also carries the slash) with remaining count below (11sp,
  onSurfaceVariant), e.g. "7" over "3".
- Remaining = 9 − count of that digit on the board (givens + user values, correct or not).
- At 0 remaining: key disabled in both modes (no point noting a completed digit), content 38%
  alpha, count hidden.

**Landscape**: Row: board on the left (side = height − top bar − 16dp), right column (max 360dp)
holding action row then number pad, vertically centered. Same components, no other changes.

---

## 3. Board visuals

- Board: square, `boardBackground`, 1.5dp outer border `gridLineThick`, corner radius 4dp (clip).
- Thin lines (between cells): **1dp** `gridLineThin`. Thick lines (box borders + outer): **2dp**
  `gridLineThick`. Draw lines in one `Canvas` on top of cell backgrounds, under digits.
- Cell digit: 60% of cell side as sp-equivalent (≈ 22–26sp). Use `Text` with `fontSize` from
  `cellSize * 0.6f` converted via `LocalDensity`.
  - **Given**: `givenDigit`, FontWeight.SemiBold (600).
  - **User**: `userDigit`, FontWeight.Normal (400).
- **Notes**: 3×3 mini-grid inside the cell with 2dp inset; digit n at position
  ((n−1)/3, (n−1)%3), i.e. 1 2 3 / 4 5 6 / 7 8 9. Size = 26% of cell (≈ 9–11sp), Medium,
  `noteDigit`. When a digit is same-digit highlighted, its note is drawn in `accent` + Bold.
- **Rule-out marks**: drawn in the note's own 3×3 position, same size, Medium, `noteDigit`, with a
  1.25dp diagonal slash (bottom-left to top-right, round caps) slightly past the glyph. Never
  drawn in `accent` Bold, even when the digit is highlighted: that style means "candidate". A cell
  shows notes and rule-out marks side by side; a digit is never both.
- **Completion pulse**: when a placement completes a row, column or box (nine filled, no
  duplicates) or places the ninth copy of a digit (no two sharing a house), those cells flash
  `accent` at up to 45% alpha, rippling out from the placed cell by Chebyshev distance (35ms
  stagger, 380ms flash, fast rise and slow fade). Never checked against the solution, so it reveals
  nothing the conflict styling doesn't. Fires for placements and hints, not undo, and not on the
  winning move (the win wave covers that). TalkBack announces "Row 3 complete" / "All 5s placed".

**Cell states and background layering** (higher wins; only one background is painted):

| Priority | State | Background | Digit color |
|---|---|---|---|
| 1 | Selected | `selectedCell` + 2dp inner border `accent` | per type (below) |
| 2 | Mistake (setting on, committed user value ≠ solution; not while provisional, §4) | `mistakeCell` | `conflictDigit` |
| 3 | Same digit as selected cell's value | `sameDigitCell` | per type |
| 4 | Excluded for the focused digit (shading on) | `excludedCell` | per type |
| 4b | Peer (same row/col/box as selection; only when no digit focus) | `peerCell` | per type |
| 5 | Default | `boardBackground` | per type |

Digit color priority (independent of background): **conflict/mistake → `conflictDigit`**, else
given → `givenDigit`, else user → `userDigit`.
- **Conflict** = a value duplicated in its row, column, or box. Always shown (not a setting). All
  involved *user* cells get `conflictDigit`; a conflicting *given* keeps `givenDigit` but gets a
  1.5dp `conflictDigit` underline below the digit, so the user sees which cell they clash with.
- **Mistake** (setting on) adds the `mistakeCell` background on top of conflict styling.
- Selected + mistake: selected background wins, digit stays `conflictDigit` (contrast verified).
- Same-digit highlight applies only when the selected cell has a value; empty selection highlights
  peers only. Selecting a given highlights the same way.

### Digit focus (exclusion shading), a core feature

The player taps a cell containing a digit *d* and sees every cell where *d* can no longer go, based
on all the *d*s already on the board. The empty cells left unshaded are the remaining places for *d*.

- **Trigger:** select a cell whose value is *d* (a given or a user entry, provisional included).
  Focus also activates when no cell is selected and the player taps pad key *d*. That tap enters
  nothing; it only focuses *d*. Nothing is selected at the start of a game, after tapping the
  selected cell again, or after tapping outside the board (§4). Tapping the same key again, tapping
  outside the board, or selecting any cell clears the pad focus.
- **Excluded cells** for *d* are every cell in the row, column or box of any placed *d*, every
  filled cell, and every empty cell where the player ruled *d* out. Excluded cells get the
  `excludedCell` background.
- **Candidate cells** (empty and not excluded) keep `boardBackground`. When notes are present, a
  candidate cell's note *d* is drawn in `accent` Bold, as with same-digit highlighting.
- Placed digits and rule-out marks count; notes never exclude anything. A wrong user digit or a
  wrong rule-out mark still excludes cells, because the board shows what the player has put down.
  A ruled-out cell keeps showing its slashed *d* under the shading, so the player can tell their
  own elimination from one that follows from placed digits.
- The view does not point out singles: nothing marks a row or box that has only one candidate left.
  Spotting it stays the player's job.
- **While focus is active, the peer highlight is replaced by the exclusion shading.** Cells
  containing *d* keep `sameDigitCell`.
- Changes animate with `animateColorAsState` at 120ms. (Row/column helper lines were tried and
  removed: the shading alone reads better.)
- **Setting:** "Shade where a digit can't go", on by default. When it's off, selecting a digit falls back to plain
  same-digit and peer highlighting.
- **TalkBack:** after focus changes, announce "Focused on 1: 14 cells possible". An excluded empty
  cell's description gets the suffix ", 1 excluded" (unless the player ruled 1 out there; the
  "ruled out" list already says so).

Animation: background color changes `animateColorAsState` 120ms; no other motion on the board.
On win: wave fill — each cell flashes `sameDigitCell` with 20ms stagger by (row+col), 300ms total.

---

## 4. Interaction rules

Digit entry follows the NRC app's "tap twice for note" model, refined by the UX review
(docs/UX-REVIEW-input.md §4). It is controlled by the setting **"Tap digit twice for note"**
(default **on**).

- **Tap cell** → selects it (givens included; givens are read-only). **Tapping the selected cell
  again deselects it**, and so does tapping anywhere outside the board, pad and buttons. Initial
  state on new game: no selection; on resume: last selection restored.
- **No selection + tap pad key d** → digit focus on d (§3); tapping d again clears it. Nothing is
  entered.
- **Given selected + tap digit**: no-op + light "reject" haptic (`HapticFeedbackConstants.REJECT`
  on API 30+, otherwise nothing).

**Tap digit d, Notes off, editable selected cell** (setting on):
1. **Empty cell (with or without notes) → provisional value d.** It renders as a normal user digit
   and the cell's own notes are hidden but kept. *Immediate:* haptic tick, remaining counts,
   conflict styling, win check. *Deferred until commit:* removing d from the peers' notes, the
   mistake count, mistake highlighting and the ", incorrect" description.
2. **Commit** happens on: selection change (including deselect), a different digit, Erase, Hint,
   the Notes toggle, a long-press, and `ON_PAUSE`. There is no timeout. Committing applies the peer
   note removal and the mistake check without adding an undo step: the placement's undo entry
   covers both. Undo does not commit; it simply reverts the provisional value. Auto-save writes
   the provisional value as committed but leaves it provisional in memory, so the second tap
   still works after a debounced save. If the process dies, the value comes back committed.
3. **Tap d again while it is provisional → note.** The exact pre-placement state is restored
   (peers were never touched and no mistake was counted), then note d is **added** (never toggled
   off; a note that was already on stays on). Placement and conversion together are **one undo
   step**, which reverts to the pre-placement state.
4. **Tap d on a committed value d** (e.g. after selecting another cell and coming back): the value
   is cleared and note d is added, as its own undo step. Peer notes removed at commit are *not*
   restored.
5. **Tap d on a cell holding a different value e:** e is replaced by d, committed at once (no
   provisional state; peers lose d, mistake checked). A second tap of d then follows rule 4.
6. While a provisional d is pending, pad key d stays **enabled** even if its remaining count is 0.

With the setting **off**, every placement is committed at once (peer removal and mistake check
happen immediately), and tapping d on a cell whose value is d does nothing. Notes then go through
Notes mode or long-press.

- **Notes mode on** (action-row toggle with "ON" badge, default off): a tap toggles note d on an
  empty cell; on a cell with a value it does nothing (keys show normally; no error).
- **Rule-out marks** ("a 4 can never go here"; setting on by default): in Rule out mode a tap
  toggles a rule-out mark for d on an empty editable cell; on a cell with a value it does nothing.
  Adding a mark clears note d in that cell, and adding note d (any route, including tap-twice
  conversion) clears the mark. Committing a placement of d clears mark d in that cell and in its
  peers, in the same undo step. Mistake highlighting never checks marks (nor notes). With the
  setting off the Notes button skips Rule out and existing marks stay visible. Tap-twice only runs
  with both modes off.
- **Long-press digit d** (required): toggles note d on the selected empty cell regardless of Notes
  or Rule out mode. This is the one-action way to **remove** a note; Erase and Notes mode also work. It
  commits a pending placement first, so on a cell holding a provisional value it does nothing
  else.
- **Erase**: commits, then clears the value (hidden notes and marks reappear), or all notes and
  rule-out marks if there is no value. One undo step.
- **Undo**: reverts the last board mutation (placement with its peer-note removal, value-to-note
  conversion, note or rule-out toggle, erase, hint). Also restores selection to the affected cell. Unlimited
  depth within a game; the stack is **not** persisted (dropped on save). Counted mistakes are not
  refunded.
- **Hint**: commits, then fills the selected cell with its correct value if it is editable and
  empty or wrong; otherwise the engine's `Hints.nextHint` picks a cell (a wrong cell, then a
  naked single, then a hidden single, then the first empty cell), which gets selected. Rendered as
  a normal user digit. Decrements the count; undo reverts the cell but does *not* refund the hint.
- **Haptics**: `HapticFeedbackType.TextHandleMove` (light tick) on value entry, note or rule-out
  toggle, conversion, erase, hint. `LongPress` on win.
- **One-time tip**: the first time a game opens with the setting on, a snackbar shows "Tip: tap a
  digit twice to pencil it in. Long-press to toggle a pencil mark." A seen flag is kept in
  SharedPreferences.
- **Completion**: when all 81 cells equal the solution (a provisional value counts) → stop timer,
  lock input (pad and actions disabled, board taps ignored), play wave + haptic, then show the Win
  dialog after 400ms. A full board that is not correct is *not* a win: no dialog; conflicts are
  already visible.
- **Auto-save**: write state (values, notes, rule-out marks, elapsed time, hints left/used,
  mistakes, selection, Notes/Rule out mode; givens and solution come from the bank by puzzle id) after every change (debounced
  500ms), on `ON_STOP` and when leaving the game.
- Post-MVP (not implemented): while digit focus is on, tapping an unshaded empty cell toggles
  note d without moving the selection.

---

## 5. Accessibility

- All digit/background pairs in section 6 meet **≥ 4.5:1** (verified for every background a digit
  can appear on, including selected and mistake).
- Touch targets ≥ 48dp: cells are ≥ 38dp at 360dp, so the board as a whole is one touch surface
  and cells are exposed to TalkBack as individual semantics nodes (`Modifier.semantics` per cell,
  `Role.Button`, `selected = isSelected`). Keys/actions are ≥ 56dp.
- Cell content descriptions (1-based rows/cols):
  - "Row 3, column 5, 7, given"
  - "Row 3, column 5, 7" (user value); append ", conflict" / ", incorrect" when applicable
  - "Row 3, column 5, empty, notes 2 4 9, ruled out 1 7" / "Row 3, column 5, empty"
- Number key: "7, 3 remaining" / "7, all placed" (disabled). When an empty editable cell is
  selected, each key has a custom action "Add note 7" / "Remove note 7" (same as long-press), plus
  "Rule out 7" / "Allow 7" with rule-out marks on. A rule-out toggle announces "7 ruled out" /
  "7 allowed". Converting a provisional value to a note announces "Note 7 added". The Notes button
  uses `toggleableState` with its current label: "Notes, on" / "Rule out, on". Hint: "Hint, 2 remaining". Timer: `liveRegion` **off** (too
  chatty); description "Elapsed time 4 minutes 12 seconds".
- After entering a value via TalkBack, announce the new cell description.
- Respect font scale for chrome (top bar, buttons) up to 1.3×; board digits scale with cell size
  only (fixed to geometry) so the grid never breaks.
- Never rely on color alone: conflicts on givens get the underline; Notes mode has the "ON" badge;
  rule-out marks and the Rule out mode carry a slash.

---

## 6. Color palette

Calm, cool-neutral slate with a single blue accent; red is reserved for errors. Map these into a
`SudokuColors` data class provided via `CompositionLocal`; pick light/dark from
`isSystemInDarkTheme()`. Chrome (bars, buttons) uses the M3 `ColorScheme` seeded from `accent`.
Dynamic color: **off** for the board (consistency of highlights), allowed for chrome.

| Token | Light | Dark | Use |
|---|---|---|---|
| boardBackground | `#FFFFFF` | `#1A1D23` | default cell |
| gridLineThin | `#A0A8B4` | `#5A6270` | 1dp cell lines (≥ 1.7:1 on `excludedCell`) |
| gridLineThick | `#3C4452` | `#9AA3B2` | 2dp box lines + border |
| givenDigit | `#1F2933` | `#E8ECF2` | givens, weight 600 |
| userDigit | `#2457C5` | `#8AB4FF` | user entries, weight 400 |
| noteDigit | `#525B69` | `#A3ACBA` | pencil marks and rule-out marks |
| selectedCell | `#CFE0FF` | `#263B61` | selected cell bg |
| peerCell | `#F0F3F8` | `#232831` | row/col/box peers |
| sameDigitCell | `#E3EBFA` | `#2E3644` | cells with same value |
| conflictDigit | `#B3261E` | `#FF8A80` | conflicting / wrong digits |
| mistakeCell | `#FCE4E4` | `#4A2326` | wrong-value bg (setting on) |
| accent | `#2457C5` | `#8AB4FF` | selection border, Notes on, seed |
| screenBackground | `#F6F7F9` | `#121418` | behind the board |
| excludedCell | `#D5DAE1` | `#363C47` | cells where the focused digit can't go (1.41 / 1.52:1 on `boardBackground`) |

Key contrast checks (WCAG ratio, approx.):

| Pair | Light | Dark |
|---|---|---|
| givenDigit / boardBackground | 15.4 | 14.2 |
| userDigit / boardBackground | 6.5 | 8.1 |
| userDigit / selectedCell | 4.9 | 5.3 |
| noteDigit / boardBackground | 6.9 | 7.4 |
| noteDigit / selectedCell | 5.2 | 4.9 |
| noteDigit / excludedCell | 4.9 | 4.8 |
| userDigit / excludedCell | 4.6 | 5.3 |
| conflictDigit / excludedCell | 4.7 | 4.9 |
| conflictDigit / selectedCell | 4.9 | 4.9 |
| conflictDigit / mistakeCell | 5.4 | 5.9 |

Any new background token must be checked against `userDigit`, `noteDigit` and `conflictDigit`.

---

## 7. Typography summary

| Element | Size | Weight |
|---|---|---|
| Home title | 36sp | SemiBold |
| Top-bar title / timer | 16sp (titleMedium) | Medium, timer tabular |
| Cell digit | 0.6 × cell | Given 600 / User 400 |
| Note digit | 0.26 × cell | Medium (Bold when highlighted) |
| Pad digit / count | 28sp / 11sp | Medium / Normal |
| Action label | 12sp (labelMedium) | Medium |

Font: system default (Roboto / Google Sans via `FontFamily.Default`); no custom fonts in MVP.
