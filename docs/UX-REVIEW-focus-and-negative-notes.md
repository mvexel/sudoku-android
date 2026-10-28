# UX review: digit-focus shading contrast, and negative notes

Scope: two requests from the owner, checked against DESIGN.md §3–§6 for Hard/Expert/Extreme
players. The helper lines are gone, so only the `excludedCell` shading remains (§3 still describes
the lines). Labels: **[E]** = follows from the spec or from arithmetic (ratios were computed with the
WCAG 2.x formula, not estimated). **[J]** = my judgment, not yet tested with players.

## 1. "The shading is too subtle"

**Current ratios [E]:**

| Pair | Light | Dark |
|---|---|---|
| excludedCell / boardBackground (the distinction itself) | 1.24 | **1.17** |
| peerCell / boardBackground | 1.11 | 1.14 |
| excludedCell / peerCell | 1.11 | **1.03** |
| excludedCell / sameDigitCell | 1.03 | 1.18 |

At 1.17:1 the dark shading is close to invisible, and peer vs excluded (1.03) is effectively the
same color, which matches what the owner saw on the Pixel. Light mode is weak but workable.

**Target [J]:** ≥ 1.5:1 in dark and ≥ 1.4:1 in light. Here the shading marks large blocks (whole
rows, columns and boxes), not thin strokes, so a ratio well below WCAG's 3:1 for UI components still
reads at a glance. Dark gets the higher target because OLED panels and a dark-adapted eye squash
differences near black. The ceiling is set by the digits, which must stay ≥ 4.5:1 on the shade:
- **Light [E]:** `userDigit`/`accent` (#2457C5) hits 4.5:1 at about 1.44:1 shade contrast.
  `accent` is shared with chrome, so it stays as it is, and light tops out around 1.4.
- **Dark [E]:** `noteDigit` and `conflictDigit` hit 4.5:1 at about 1.65:1.

**Proposed tokens:**

| Token | Light | Dark |
|---|---|---|
| excludedCell | `#E4E7EC` → **`#D5DAE1`** | `#262A32` → **`#363C47`** |
| noteDigit | `#5B6472` → **`#525B69`** | unchanged `#A3ACBA` |
| peerCell, sameDigitCell, selectedCell | unchanged | unchanged |

**New ratios [E]:**

| Pair | Light | Dark |
|---|---|---|
| excludedCell / boardBackground | **1.41** | **1.52** |
| excludedCell / peerCell | 1.26 | 1.33 |
| excludedCell / sameDigitCell | 1.17 | 1.10 |
| excludedCell / selectedCell | 1.05 | 1.01 |
| givenDigit / excludedCell | 10.50 | 9.35 |
| userDigit / excludedCell | 4.61 | 5.31 |
| noteDigit / excludedCell | 4.89 | 4.84 |
| conflictDigit / excludedCell | 4.65 | 4.86 |
| accent (highlighted note) / excludedCell | 4.61 | 5.31 |

- **Light `noteDigit` must darken [E].** The current #5B6472 gives only 4.26 on the new shade. It
  also gives **4.49 on `selectedCell` today**, a hair under the 4.5 that §6 claims. #525B69 fixes
  both: 5.15 on selected, 6.86 on the board, 5.67 on mistake. It reads the same at note size.
- **sameDigitCell and selectedCell [E/J].** Their luminance is close to the new shade (1.01–1.17),
  but neither relies on color. A selected cell has the 2dp `accent` border. A same-digit cell holds
  the digit *d* itself, and it is also more saturated blue than the neutral slate shade. Dark
  sameDigit now sits slightly *darker* than excluded, so focused *d*s stand out less than before. I
  accept that: the digits are what the player is looking at [J]. Don't retune sameDigit for this.
- **peerCell vs excludedCell** never appear together (focus replaces peers), but switching between
  them is now visible (1.26 / 1.33), so the player notices that focus has kicked in.
- **Fallback if dark is still too faint on device:** `#3A404B` gives 1.62:1, with note 4.55 and
  conflict 4.57. That is the last step before digits fail. Don't go further.

**Shading only, or a pattern too? Recommendation: no hatch [J, firm].**
- §5's "never rely on color alone" protects against **hue-only** encodings, the ones
  color-vision-deficient players can't see. Here the difference is luminance, which every CVD type
  perceives [E]. The real problem was too little luminance difference, and the new values fix that.
- A hatch would cross 9–11sp note digits in exactly the cells being studied. It also clashes with
  the slashed glyph proposed for negative notes (§2.3), where a diagonal stroke means "ruled out by
  the player" [E]. Two diagonal-stroke meanings on one board is worse than none.
- The candidate cells (the information that matters) are already marked by something other than
  shade: they are the only empty, unshaded cells, and their note *d* is bold in `accent`.
- Clean-up the owner should know about [E]: the `helperLine` token is now dead. The setting is
  still called "Helper lines", which no longer describes anything. Rename it **"Digit focus
  shading"** and update §3/§6.

## 2. Negative notes ("a 4 can never go here")

### 2.1 Is it distinct from removing a note? Yes [E/J]
- Removing a note needs a note to exist. A player who **doesn't full-mark** (Snyder-style pairs
  only, or no marks at all) has no list to remove from. To record "not 4" today they would have to
  list every other candidate, which is exactly the work they skip [E].
- **Who and when [J]:** Hard/Expert players doing pointing pairs or box/line reductions before
  marking a box. Extreme players recording the results of chains, coloring or W-wings in cells they
  haven't fully marked. Expect bursts of 2–5 eliminations, 10–40 per solve.
- Full-markers get little from it: for them a negative note is a stronger kind of removal. The
  feature has to cost them nothing.

### 2.2 Data model
- Add a second 9-bit mask per cell, `excluded: List<Int>` (bit d−1 = "player ruled out d"), next to
  `notes`. Same shape, same helpers.
- **Note and negative note for the same digit are mutually exclusive [E].** "d is a candidate" and
  "d is not" contradict each other. Setting either one clears the other bit. Different digits mix
  freely: notes {2,5} with ruled-out {7} is valid.
- Like notes, the mask is kept but hidden while the cell has a value.

### 2.3 Rendering
- Draw it in the note's own 3×3 position at the same size, Medium weight, in `noteDigit`, with a
  **diagonal slash** (bottom-left to top-right, 1.25dp, `noteDigit`, round caps) running about 10%
  past the glyph box. The slash is the cue that works without color [E]. Don't use red: §6 reserves
  red for errors.
- A ruled-out *d* is **never** drawn in `accent` Bold, even when *d* is highlighted, because that
  style means "candidate". It stays plain and slashed.
- Contrast is the same as `noteDigit` on every background: ≥ 4.84 with the new tokens.
- Hallway check [J]: a fully slashed cell (8 marks) is noisy. That is rare and still legible.

### 2.4 Input
Tap-count model [J for inputs]: one burst rules out *d* in 4 cells while Notes is off.

| Option | Taps per burst | Problems |
|---|---|---|
| **R: separate "Rule out" toggle** (action row) | 1 + 4×2 + 1 = **10** | needs a 5th tile |
| T: Notes toggle cycles off → notes → rule-out | 2 + 8 + 1 = **11** | Notes users need 2 taps to turn Notes off (+1 every time); one badge for two states |
| L: long-press pad key = rule out | 4×2 = **8**, 4 of them holds (~+2 s) | takes away the required one-action note removal (§4) |
| S: swipe on pad key | 8 | can't be discovered; misfires on neighbouring keys |
| C: Notes-mode tap cycles none → note → ruled out | 1 + 8 + 1 = 10 | removing a note takes 2 taps: a cost for full-markers |

**Recommendation: R [J].** A fifth tile still fits: (360 − 16) / 5 = 68.8dp ≥ 64dp [E]. Order:
**Undo · Erase · Notes · Rule out · Hint**. Icon: the pencil with a slash. It gets the same
pill + "ON" badge styling as Notes.
- Notes and Rule out act as **radio buttons**: turning one on turns the other off. Both commit a
  pending placement.
- In Rule-out mode, tap *d* on an empty editable cell toggles ruled-out *d* (clearing note *d*). On
  a cell with a value it does nothing, as in Notes mode.
- Long-press *d* keeps meaning "toggle note *d*" in every mode, so one gesture means one thing.
- Pad keys in Rule-out mode show the digit in `noteDigit` with the same slash. That way the mode is
  visible where the input happens.
- **The tap-twice mechanic is untouched [E]:** it only runs with both modes off.
- Post-MVP (matching §4's note-on-focus idea): with focus on *d* and Rule out on, tapping a cell
  toggles ruled-out *d* without moving the selection. A burst then costs 1 + 4 + 1 = 6 taps.

### 2.5 Digit focus: a ruled-out d shades the cell. Change the rule [J]
§3's "notes never exclude" exists because notes are *candidates*: shading on their absence would
fake certainty. A negative note is the reverse: it is the player's own explicit elimination, which
§3's principle already covers: "the board shows what the player has put down" (a wrong user digit
excludes too) [E]. New rule: **placed values and ruled-out marks exclude; notes never do.**
- The cell still shows the slashed *d* under the shading, so the player can tell their own
  elimination from one that follows from placed digits. That cue doesn't use color [E].
- Risk [J]: a wrong negative note hides a real candidate. Wrong placements carry the same risk
  today, and it fits the existing rule that the view never points out singles.
- `candidateCount` and the "Focused on 4: N cells possible" announcement include these cells.

### 2.6 Interactions

| Event | Behavior |
|---|---|
| Place value *d* in the cell | Mask hidden but kept. On commit, clear ruled-out *d* in that cell (the player overruled it) |
| Peer auto-removal on commit | Peers lose ruled-out *d* as well as note *d* (they are now implied). Same undo step, deferred while provisional. Losing a negative note is safe: it falls back to "unknown". Losing a note is not [E] |
| Tap-twice conversion to note *d* | Adds note *d*, clears ruled-out *d* |
| Notes-mode tap / long-press adds note *d* | Clears ruled-out *d* |
| Erase | Value present: clear value (notes and negatives reappear). Otherwise clear **both** masks. One undo step |
| Undo | `Snapshot` gains the mask. Each toggle is one step |
| Hint | Unchanged. The hinted value commits like a placement (row 1). No mistake check on negatives, same as notes |
| Completion, remaining counts | Unaffected. A key at 0 stays disabled in Rule-out mode |
| Save | Add `"excluded"` int array to the game JSON. Read it with `optJSONArray`, defaulting to zeros. It is an additive field, so no migration or version bump |
| Auto-save | Includes the mask and the Rule-out mode flag (as for Notes mode) |

**TalkBack:**
- Cell: "Row 3, column 5, empty, notes 2 4 9, ruled out 1 7". When focus is on a cell excluded
  only by the player's mark, use the suffix ", 4 ruled out" instead of ", 4 excluded".
- Tile: `toggleableState`, "Rule out, on".
- Pad key custom actions (empty editable cell selected): add "Rule out 7" / "Allow 7" next to
  "Add/Remove note 7". After a toggle, announce "7 ruled out" / "7 allowed".

### 2.7 Setting
Put it behind a setting **"Rule-out marks"**, with the tile shown only when it is on [J]. Full-markers
lose nothing (4 tiles stay at 4), and the players who want it can find it in Settings. Show a
one-time tip the first time it is enabled: "Tap Rule out, then a digit, to mark that it can't go
in a cell." The default is the owner's call (Q1).

## 3. Decisions (for the implementer)
1. Theme: light `excludedCell #D5DAE1`, `noteDigit #525B69`. Dark `excludedCell #363C47`. Update
   §6's tables, including the corrected noteDigit/selectedCell ratio.
2. No hatch. Delete the `helperLine` token. Rename the setting to "Digit focus shading".
3. Add an `excluded` 9-bit mask per cell. It is mutually exclusive per digit with `notes`, and
   hidden while the cell has a value.
4. Render a ruled-out digit as a slashed `noteDigit` digit in its 3×3 slot. Never accent.
5. Add a "Rule out" action tile, radio-exclusive with Notes. Tap toggles; long-press stays note.
   Shown only when the setting is on.
6. `digitFocus` takes the mask: ruled-out *d* excludes that cell. Amend §3's sentence.
7. On commit of *d*, clear ruled-out *d* in the cell and in its peers. Erase on an empty cell
   clears both masks.
8. Add it to Snapshot/Undo and to the save JSON (additive `"excluded"`). Add the TalkBack strings
   and actions listed in §2.6.

## 4. Open questions for the owner
1. Should "Rule-out marks" default **off** (my lean: keeps the 4-tile layout for full-markers) or
   **on** (more people find it)?
2. Tile label: "Rule out", or another word your players use ("Cross out", "Not")?
3. Should "Highlight mistakes" also flag a ruled-out mark that rules out the solution digit? I say
   no, for consistency: removed notes aren't checked either. But a wrong elimination is what kills
   Extreme solves.
