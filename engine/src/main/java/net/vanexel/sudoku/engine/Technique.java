package net.vanexel.sudoku.engine;

/**
 * Human solving techniques used by {@link Rater}, in the order they are tried (easiest first).
 * Basic techniques have no player-facing {@link Difficulty} (they are below HARD).
 */
public enum Technique {
    // Basic (below HARD)
    NAKED_SINGLE(null, "Naked Single"),
    HIDDEN_SINGLE(null, "Hidden Single"),
    NAKED_PAIR(null, "Naked Pair"),
    POINTING(null, "Pointing Pair/Triple"),          // box candidates confined to one line
    CLAIMING(null, "Claiming (Box/Line)"),           // line candidates confined to one box
    // HARD
    HIDDEN_PAIR(Difficulty.HARD, "Hidden Pair"),
    NAKED_TRIPLE(Difficulty.HARD, "Naked Triple"),
    HIDDEN_TRIPLE(Difficulty.HARD, "Hidden Triple"),
    X_WING(Difficulty.HARD, "X-Wing"),
    // EXPERT
    XY_WING(Difficulty.EXPERT, "XY-Wing"),
    XYZ_WING(Difficulty.EXPERT, "XYZ-Wing"),
    SWORDFISH(Difficulty.EXPERT, "Swordfish"),
    JELLYFISH(Difficulty.EXPERT, "Jellyfish"),
    // EXTREME
    UNIQUE_RECTANGLE(Difficulty.EXTREME, "Unique Rectangle"),   // type 1; relies on a unique solution
    W_WING(Difficulty.EXTREME, "W-Wing"),
    SIMPLE_COLORING(Difficulty.EXTREME, "Simple Coloring"),
    X_CHAIN(Difficulty.EXTREME, "X-Chain"),
    XY_CHAIN(Difficulty.EXTREME, "XY-Chain");

    private final Difficulty difficulty;
    private final String displayName;

    Technique(Difficulty difficulty, String displayName) {
        this.difficulty = difficulty;
        this.displayName = displayName;
    }

    /** The difficulty this technique implies, or null for basic techniques (below HARD). */
    public Difficulty getDifficulty() { return difficulty; }

    /** Human-readable name for the UI, e.g. "XY-Wing". */
    public String getDisplayName() { return displayName; }
}
