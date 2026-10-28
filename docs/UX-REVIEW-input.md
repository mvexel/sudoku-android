# UX review: "tap again for note" digit entry (NRC-style)

Scope: the proposed entry mechanic against DESIGN.md §3–§4, for Hard/Expert/Extreme players.
Labels: **[E]** = follows from the spec or from arithmetic. **[J]** = my judgment, not yet tested
with players. No user data exists yet, so every tap count below is an estimate from a model.

**Verdict:** keep it as an accelerator, but change its semantics (§4). As written it makes noting
slower than the alternatives, can silently delete candidates, and with mistake highlighting on it
leaks the solution.

## 1. Tap cost and error rate

**Model solve (Expert) [J for the inputs, E for the arithmetic]:** 55 empty cells. A full
pencil-marking pass covers ~45 cells at ~3.5 candidates each, so ~158 note adds. ~50 manual note
removals (X-wing, swordfish and chain eliminations). 55 values. ~140 cell selections in any
cell-first model.

| Model | Marking pass (taps) | Whole solve (taps) | Notes |
|---|---|---|---|
| P: proposed, 2 taps per note | 45 + 316 = **361** | 140 + 316 + 100 + 55 = **~611** | removing a note also costs 2 |
| A: Notes toggle | 45 + 158 + 1 = **204** | ~463 + 40–80 switches = **~500–540** | switches cost a reach to the action row |
| B: long-press | 45 + 158 holds = **203** | **~403** taps, 208 of them holds | holds take 400–500ms each (Android default timeout), ~+60–80 s per solve |
| C: digit-first (lock d, tap cells) | 9 + 158 = **~167** | close to A overall | best fit for a sweep "where can 4 go?" |

What follows [J]:
- **P is the most expensive way to note.** It costs ~75% more than A or B in the marking pass
  (361 vs ~204) and ~15–50% more over the whole solve. Advanced players spend most of their taps
  on notes, so P must not be the primary note path.
- P is efficient only in **mixed play**: "I'll place it... no, pencil it." That happens a few
  times per solve. Two taps beat toggle → tap → toggle there.
- **Errors:** P has low motor error (same key, same spot) but high cognitive error: one key does
  three things depending on cell state. A risks forgetting the mode (offset by the pill and "ON"
  badge). B risks a hold released too early, placing a value that is usually a valid candidate,
  so nothing flags it.
- **C fits this audience best and the spec already half-builds it.** Digit focus shades every
  excluded cell, so the unshaded cells *are* the candidates for d. Letting a tap on an unshaded
  cell toggle note d would make focus a one-tap marking tool. This is a follow-up, not MVP (§4c).

## 2. Failure modes as specified

1. **Transient note wipe (worst one) [E].** Tap d on a cell that has notes {2,5,8}. The spec
   (§4) clears that cell's notes and strips d from 20 peers. The second tap restores all of it.
   So every note entered by double-tap makes up to 21 cells' notes blink out and back. Entering 3
   notes in one cell makes the board flicker 3 times.
2. **Silent candidate deletion [E].** The proposal says that on a cell with note d, tap d places
   d and tapping again turns note d *off*. A player who mis-places d and taps again to "take it
   back" (the old spec cleared the cell, and so does every app that toggles) loses a valid
   candidate without noticing. Wrong eliminations are how expert solves fall apart [J].
3. **Mistake highlighting leaks the solution [E].** With the setting on, each note entered via
   P shows the red mistake background for one tap whenever d ≠ solution, which is true for
   ~2/3 of candidates. Tap, look, tap again works as a free oracle. It also flashes red constantly.
4. **Conflict flash [E/J].** This happens only if the player notes d in a cell where d is already
   excluded. Noting an excluded digit is itself an error, so the flash is **useful feedback; keep
   it.**
5. **Pad key disables mid-gesture [E].** Remaining = 9 − count *including* wrong values. If the
   provisional placement brings d to 0, key d disables and the second tap can't happen.
6. **Haptics [J].** One tick per tap is fine. It acknowledges input and is not semantic. Never
   fire anything state-based (reject, error) on the provisional state.
7. **Win check [E/J].** A win requires all 81 cells correct, so a provisional placement that
   completes the board really is the solution. Fire it immediately and lock input. Nobody notes
   the last empty cell.
8. **"Immediately" is undefined [E].** What counts as immediate is not specified. Neither is what
   tapping d means on a value d after the selection moved away and came back, after an Undo, or
   after app pause. The undo stack and auto-save (500ms debounce) also need a defined point at
   which the value is committed.
9. **TalkBack [E/J].** Tap 1 announces "Row 3, column 5, 7", then tap 2 announces the notes. That
   is two double-taps and a misleading first announcement. Long-press under TalkBack is
   double-tap-and-hold, which is awkward. The note path needs a real accessibility action.
10. **Spec contradiction [E].** §4 says a digit tap with no selection is a no-op with a reject
    haptic. §3 says it enters digit focus. Also, tapping the selected cell never deselects it, so
    pad-only focus can be reached only before the first tap of a game.

## 3. Discoverability

- **New users lose nothing [J].** With the Notes toggle present, every player can note on day
  one. The double-tap is invisible until someone taps the same digit twice by accident. Its real
  risk is surprise (an unexpected note), not a missing capability.
- Teach it **in context**, once, as a snackbar. Show it the first time the player turns Notes on,
  or after their 3rd value placement, whichever comes first:
  > **Tip: tap a digit twice to pencil it in. Long-press to toggle a pencil mark.**
- Add it to a Help/How-to line in Settings. Don't use a tutorial overlay; this audience skips
  them [J].

## 4. Recommendation: modify it and make it a setting (default **on**)

Setting: **"Tap digit twice for note"**, default on. When it is off, the second tap clears the
cell, as in the current §4. Spec changes:

**a) Provisional placement.** Replace the "Tap digit, Notes off" bullets in §4 with:
- Tapping d on an editable cell with **no value** creates a *provisional* value d. It renders as a
  normal user digit, and the cell's own notes are hidden but kept in the model.
- **Deferred until commit:** peer-note removal, mistake highlighting and the mistake
  "incorrect" description.
- **Immediate:** the conflict styling, the haptic tick, the remaining count and the win check.
- **Commit** happens on any of: selection change, a different digit, Erase, Undo, Hint, the Notes
  toggle, a long-press, `ON_PAUSE`, or auto-save. **No timeout**, because a timeout is invisible
  state [J].
- On commit, apply the peer removal (fade 120ms, as for other changes) and evaluate mistakes.
  This is one undo step.
- **Tap d again while provisional:** discard the placement and restore the exact pre-state, then
  ensure note d is **on** (set, never toggle off). The whole sequence is one undo step that
  reverts to the pre-state. Nothing flickers, because peers were never touched.
- **Tap d on a committed value d** (for example after leaving and coming back): the cell becomes
  note d only, as its own undo step. Peer notes removed at commit are *not* restored. Document
  that.
- **Tap d on a cell with a different value e:** replace it immediately, with no provisional
  state. A second d then follows the committed rule above.
- While provisional, key d stays **enabled** even if the remaining count shows 0.
- Persist a provisional value as committed if the process dies.

**b) Note removal is never two taps.** The paths are long-press (one action), Notes mode, or
Erase. Make long-press **required**, not "optional, include if cheap". It is the one-action
toggle for both adding and removing.

**c) Fix focus reachability.** Tapping the selected cell again deselects it. Remove the
"no selection → reject haptic" rule in favour of §3's focus. Follow-up (v1.1): while digit focus
is on, tapping an unshaded empty cell toggles note d (model C) without moving the selection.

**d) TalkBack.**
- Add `customActions` "Add note 7" / "Remove note 7" to each pad key, and to the selected cell's
  node.
- When a provisional value is converted, announce "Note 7 added" and nothing else.
- When a provisional value is first placed, append ", tap 7 again for note" the first 3 times only.

**e) Undo.** The undo entry for a provisional value is rewritten in place on conversion or commit,
never pushed twice.

## 5. Hallway test (5 players, ~20 min each, two builds: setting on/off)

Recruit players who regularly solve Hard or harder (club, r/sudoku, NRC readers). Add a debug-only
input log recording timestamp, cell, key, action type and resulting state. Use a fixed Expert
puzzle and fixed mid-game save files.

| # | Task (no instructions on mechanics) | Measures |
|---|---|---|
| 1 | "Pencil in every candidate for box 5." | taps per note, time, which method they pick |
| 2 | "Remove 6 from these three cells" (an X-wing elimination). | first-attempt method, unintended placements |
| 3 | "Place 4 here... actually, make it a pencil mark." | completion, taps, verbal surprise |
| 4 | Solve from a save that needs one swordfish (~10 min). | unintended values, **corrupted candidates** (notes ≠ what they said they meant), undo count |
| 5 | Show the tip snackbar, then repeat task 1 in a fresh box. | adoption of the double-tap, change in taps per note |

**Metrics:** taps per note; unintended placements per 100 inputs; corrupted-candidate count (the
critical one); time on task; 1–5 rating "entry felt predictable"; build preference.

**Pass bar [J]:** zero corrupted candidates caused by the double-tap; taps per note ≤ 1.3 in task 1
for at least 3 of 5 players (they find toggle, long-press or focus, not P); at least 3 of 5 prefer
the "on" build or are neutral. If the bar is missed, ship with the setting defaulting to off.
