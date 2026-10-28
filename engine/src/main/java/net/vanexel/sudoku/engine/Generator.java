package net.vanexel.sudoku.engine;

import java.util.Random;

/**
 * Deterministic puzzle generator (used at build time by {@link BankBuilder}).
 *
 * <p>Each attempt builds a random full grid, then removes clues in 180°-symmetric pairs in random
 * order. A removal is kept only if the puzzle still has a unique solution and the {@link Rater}
 * can still finish it without exceeding a rating cap: HARD caps at HARD; EXPERT and EXTREME only
 * require the puzzle to stay logically solvable, so they end up as minimal as the technique set
 * allows. If the final rating doesn't match exactly, a new attempt is made with a seed derived
 * from the original one.
 */
public final class Generator {
    /** Safety cap on attempts per call. */
    static final int MAX_ATTEMPTS = 100_000;

    private Generator() {}

    /** Generates a puzzle of exactly difficulty {@code d}. Same (d, seed) always gives the same puzzle. */
    public static Puzzle generate(Difficulty d, long seed) {
        if (d == null) throw new IllegalArgumentException("difficulty is null");
        Difficulty cap = d == Difficulty.HARD ? Difficulty.HARD : Difficulty.EXTREME;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Random rnd = new Random(mix(seed, attempt));
            int[] solution = Solver.randomFullGrid(rnd);
            int[] givens = carve(solution, cap, rnd);
            Rater.Rating r = Rater.rate(givens);
            if (r.getDifficulty() == d && Solver.countSolutions(givens, 2) == 1) {
                return new Puzzle(givens, solution, d, r.getHardest(), seed);
            }
        }
        throw new IllegalStateException("no " + d + " puzzle found for seed " + seed);
    }

    /** Symmetric clue removal keeping uniqueness and logical solvability at or below {@code cap}. */
    private static int[] carve(int[] solution, Difficulty cap, Random rnd) {
        int[] grid = solution.clone();
        // Index i stands for the symmetric pair {i, 80 - i}; 40 is the center (single cell).
        int[] order = new int[41];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
        for (int a : order) {
            int b = 80 - a;
            grid[a] = 0;
            grid[b] = 0;
            if (!acceptable(grid, cap)) {
                grid[a] = solution[a];
                grid[b] = solution[b];
            }
        }
        return grid;
    }

    private static boolean acceptable(int[] grid, Difficulty cap) {
        if (Solver.countSolutions(grid, 2) != 1) return false;
        Rater.Rating r = Rater.rate(grid);
        if (!r.isSolved()) return false;
        Difficulty d = r.getDifficulty();
        return d == null || d.compareTo(cap) <= 0;      // null here means "below HARD"
    }

    /** SplitMix64 finalizer over a seed and a salt; used for all seed derivation. */
    static long mix(long seed, long salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
