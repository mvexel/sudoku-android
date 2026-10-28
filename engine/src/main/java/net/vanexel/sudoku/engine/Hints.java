package net.vanexel.sudoku.engine;

/** Hint selection for the UI. */
public final class Hints {
    private Hints() {}

    /**
     * Picks a cell to reveal, in priority order:
     * <ol>
     *   <li>a filled cell whose value is wrong (lowest index),</li>
     *   <li>an empty cell that is a naked single, then one that is a hidden single,</li>
     *   <li>any empty cell (lowest index).</li>
     * </ol>
     * Returns -1 if the grid is already solved.
     */
    public static int nextHint(int[] current, int[] solution) {
        Board.check(current);
        Board.check(solution);
        for (int i = 0; i < Board.SIZE; i++) {
            if (current[i] != 0 && current[i] != solution[i]) return i;
        }

        // All filled values are correct now; compute candidates of empty cells.
        int[] cand = new int[Board.SIZE];
        int firstEmpty = -1;
        for (int i = 0; i < Board.SIZE; i++) {
            if (current[i] != 0) continue;
            if (firstEmpty < 0) firstEmpty = i;
            int used = 0;
            for (int p : Board.PEERS[i]) if (current[p] != 0) used |= Board.bit(current[p]);
            cand[i] = ~used & Board.ALL;
        }
        if (firstEmpty < 0) return -1;

        for (int i = 0; i < Board.SIZE; i++) {
            if (current[i] == 0 && Integer.bitCount(cand[i]) == 1) return i;
        }

        int best = -1;  // lowest-index hidden single across all units
        for (int[] unit : Board.UNITS) {
            for (int d = 1; d <= 9; d++) {
                int b = Board.bit(d), found = -1, n = 0;
                for (int c : unit) {
                    if (current[c] == 0 && (cand[c] & b) != 0) {
                        found = c;
                        n++;
                    }
                }
                if (n == 1 && (best < 0 || found < best)) best = found;
            }
        }
        return best >= 0 ? best : firstEmpty;
    }
}
