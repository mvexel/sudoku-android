package net.vanexel.sudoku.engine;

import java.util.Random;

/**
 * Backtracking solver using per-row/column/box digit bitmasks and MRV
 * (fewest-candidates-first) cell selection.
 */
public final class Solver {
    private final int[] cells;
    private final int[] rowUsed = new int[9];
    private final int[] colUsed = new int[9];
    private final int[] boxUsed = new int[9];
    private final Random rnd;   // non-null: try candidates in random order
    private int limit;
    private int count;
    private int[] firstSolution;

    private Solver(int[] grid, Random rnd) {
        this.cells = grid.clone();
        this.rnd = rnd;
    }

    /**
     * Counts solutions of {@code grid}, stopping as soon as {@code limit} is reached.
     * Use limit 2 to test uniqueness (result 0, 1 or 2). The input is not modified.
     */
    public static int countSolutions(int[] grid, int limit) {
        Board.check(grid);
        if (limit <= 0) return 0;
        Solver s = new Solver(grid, null);
        if (!s.initMasks()) return 0;
        s.limit = limit;
        s.search();
        return s.count;
    }

    /**
     * Returns a solution of {@code grid} (the first one found), or null if it has none.
     * Does not check uniqueness; the input is not modified.
     */
    public static int[] solve(int[] grid) {
        Board.check(grid);
        Solver s = new Solver(grid, null);
        if (!s.initMasks()) return null;
        s.limit = 1;
        s.search();
        return s.firstSolution;
    }

    /** Produces a random complete valid grid using randomized backtracking. */
    static int[] randomFullGrid(Random rnd) {
        Solver s = new Solver(new int[Board.SIZE], rnd);
        s.initMasks();
        s.limit = 1;
        s.search();
        return s.firstSolution;
    }

    /** Builds the used-digit masks; returns false if the givens already conflict. */
    private boolean initMasks() {
        for (int i = 0; i < Board.SIZE; i++) {
            int v = cells[i];
            if (v == 0) continue;
            int b = Board.bit(v), r = Board.row(i), c = Board.col(i), x = Board.box(i);
            if (((rowUsed[r] | colUsed[c] | boxUsed[x]) & b) != 0) return false;
            rowUsed[r] |= b;
            colUsed[c] |= b;
            boxUsed[x] |= b;
        }
        return true;
    }

    private void search() {
        // MRV: pick the empty cell with the fewest candidates.
        int best = -1, bestMask = 0, bestCount = 10;
        for (int i = 0; i < Board.SIZE; i++) {
            if (cells[i] != 0) continue;
            int m = ~(rowUsed[Board.row(i)] | colUsed[Board.col(i)] | boxUsed[Board.box(i)]) & Board.ALL;
            int n = Integer.bitCount(m);
            if (n < bestCount) {
                best = i;
                bestMask = m;
                bestCount = n;
                if (n <= 1) break;
            }
        }
        if (best < 0) {                        // no empty cells: solved
            if (count++ == 0) firstSolution = cells.clone();
            return;
        }
        if (bestCount == 0) return;            // dead end

        int r = Board.row(best), c = Board.col(best), x = Board.box(best);
        int[] order = candidateOrder(bestMask, bestCount);
        for (int k = 0; k < bestCount; k++) {
            int b = order == null ? Integer.lowestOneBit(bestMask) : order[k];
            if (order == null) bestMask &= bestMask - 1;
            cells[best] = Board.digit(b);
            rowUsed[r] |= b;
            colUsed[c] |= b;
            boxUsed[x] |= b;
            search();
            rowUsed[r] &= ~b;
            colUsed[c] &= ~b;
            boxUsed[x] &= ~b;
            cells[best] = 0;
            if (count >= limit) return;
        }
    }

    /** Shuffled candidate bits when randomizing; null means "lowest bit first". */
    private int[] candidateOrder(int mask, int n) {
        if (rnd == null) return null;
        int[] bits = new int[n];
        for (int k = 0; k < n; k++) {
            bits[k] = Integer.lowestOneBit(mask);
            mask &= mask - 1;
        }
        for (int k = n - 1; k > 0; k--) {      // Fisher-Yates
            int j = rnd.nextInt(k + 1);
            int t = bits[k]; bits[k] = bits[j]; bits[j] = t;
        }
        return bits;
    }
}
