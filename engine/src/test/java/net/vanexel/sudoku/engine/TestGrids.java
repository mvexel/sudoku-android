package net.vanexel.sudoku.engine;

/** Shared fixtures and independent (slow, obviously correct) grid helpers for tests. */
final class TestGrids {
    static final String INKALA =
            "8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..";
    static final String INKALA_SOLUTION =
            "812753649943682175675491283154237896369845721287169534521974368438526917796318452";

    private TestGrids() {}

    static int[] parse(String s) {
        int[] g = new int[81];
        for (int i = 0; i < 81; i++) {
            char c = s.charAt(i);
            g[i] = c >= '1' && c <= '9' ? c - '0' : 0;
        }
        return g;
    }

    static boolean sameUnit(int kind, int a, int b) {
        switch (kind) {
            case 0: return a / 9 == b / 9;
            case 1: return a % 9 == b % 9;
            default: return a / 27 == b / 27 && (a % 9) / 3 == (b % 9) / 3;
        }
    }

    static boolean peers(int a, int b) {
        return a != b && (sameUnit(0, a, b) || sameUnit(1, a, b) || sameUnit(2, a, b));
    }

    static boolean isValidSolution(int[] s) {
        if (s == null || s.length != 81) return false;
        for (int i = 0; i < 81; i++) {
            if (s[i] < 1 || s[i] > 9) return false;
            for (int j = i + 1; j < 81; j++) if (peers(i, j) && s[i] == s[j]) return false;
        }
        return true;
    }

    /** Every filled cell of g equals the solution. */
    static boolean consistent(int[] g, int[] s) {
        for (int i = 0; i < 81; i++) if (g[i] != 0 && g[i] != s[i]) return false;
        return true;
    }

    static boolean isSymmetric(int[] g) {
        for (int i = 0; i < 81; i++) if ((g[i] == 0) != (g[80 - i] == 0)) return false;
        return true;
    }

    /** Candidate mask (bit d = digit d) of a cell, from placed values only. */
    static int candidates(int[] g, int cell) {
        int used = 0;
        for (int j = 0; j < 81; j++) if (g[j] != 0 && peers(cell, j)) used |= 1 << g[j];
        return ~used & 0x3FE;
    }

    static boolean isNakedSingle(int[] g, int cell) {
        return Integer.bitCount(candidates(g, cell)) == 1;
    }

    static boolean isHiddenSingle(int[] g, int cell) {
        int cand = candidates(g, cell);
        for (int d = 1; d <= 9; d++) {
            if ((cand & (1 << d)) == 0) continue;
            for (int kind = 0; kind < 3; kind++) {
                boolean alone = true;
                for (int j = 0; j < 81 && alone; j++) {
                    if (j == cell || g[j] != 0 || !sameUnit(kind, cell, j)) continue;
                    if ((candidates(g, j) & (1 << d)) != 0) alone = false;
                }
                if (alone) return true;
            }
        }
        return false;
    }
}
