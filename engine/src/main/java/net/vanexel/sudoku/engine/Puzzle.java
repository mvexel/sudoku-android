package net.vanexel.sudoku.engine;

import java.util.Arrays;

/** Immutable puzzle. Arrays are defensively copied in and out. */
public final class Puzzle {
    private final int[] givens;
    private final int[] solution;
    private final Difficulty difficulty;
    private final Technique hardest;
    private final long seed;
    private final String id;

    /**
     * @param givens     81 cells, 0 = empty
     * @param solution   81 cells, the unique solution
     * @param difficulty the rated difficulty
     * @param hardest    the hardest technique the rater needed
     * @param seed       generator seed (for bank puzzles: the index in the bank file)
     */
    public Puzzle(int[] givens, int[] solution, Difficulty difficulty, Technique hardest, long seed) {
        Board.check(givens);
        Board.check(solution);
        this.givens = givens.clone();
        this.solution = solution.clone();
        this.difficulty = difficulty;
        this.hardest = hardest;
        this.seed = seed;
        this.id = idOf(givens);
    }

    public int[] getGivens() { return givens.clone(); }

    public int[] getSolution() { return solution.clone(); }

    public Difficulty getDifficulty() { return difficulty; }

    public Technique getHardestTechnique() { return hardest; }

    /** Display name of the hardest technique, e.g. "XY-Wing", for the post-win screen. */
    public String getHardestTechniqueName() { return hardest.getDisplayName(); }

    public long getSeed() { return seed; }

    /** Stable 12-hex-digit id derived from the givens only (see {@link #idOf}). */
    public String getId() { return id; }

    /** Number of non-empty cells in the givens. */
    public int getGivenCount() {
        int n = 0;
        for (int v : givens) if (v != 0) n++;
        return n;
    }

    /** The givens as an 81-character string of digits, '0' for empty. */
    public String givensString() { return toDigits(givens); }

    /**
     * Stable id for a set of givens: the low 48 bits of a 64-bit FNV-1a hash of the 81-digit
     * string, as 12 lowercase hex digits. Independent of bank seed, count and order.
     */
    public static String idOf(int[] givens) {
        Board.check(givens);
        long h = 0xcbf29ce484222325L;
        for (int v : givens) {
            h ^= '0' + v;
            h *= 0x100000001b3L;
        }
        return String.format("%012x", h & 0xFFFFFFFFFFFFL);
    }

    static String toDigits(int[] grid) {
        char[] cs = new char[Board.SIZE];
        for (int i = 0; i < Board.SIZE; i++) cs[i] = (char) ('0' + grid[i]);
        return new String(cs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Puzzle)) return false;
        Puzzle p = (Puzzle) o;
        return seed == p.seed && difficulty == p.difficulty && hardest == p.hardest
                && Arrays.equals(givens, p.givens) && Arrays.equals(solution, p.solution);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(givens) + Long.hashCode(seed);
    }

    @Override
    public String toString() {
        return "Puzzle{" + difficulty + "/" + hardest + ", id=" + id + ", " + givensString() + "}";
    }
}
