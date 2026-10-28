package net.vanexel.sudoku.engine;

/**
 * Player-facing difficulty, defined by the hardest human technique needed to solve the puzzle
 * (see {@link Technique} and {@link Rater}). Puzzles solvable with basic techniques alone
 * (singles, naked pairs, pointing/claiming) are below HARD and never generated.
 */
public enum Difficulty {
    /** Needs hidden pairs, naked/hidden triples or X-wing. */
    HARD,
    /** Needs swordfish, jellyfish, XY-wing or XYZ-wing. */
    EXPERT,
    /** Needs unique rectangles, W-wing, simple coloring, X-chains or XY-chains. */
    EXTREME
}
