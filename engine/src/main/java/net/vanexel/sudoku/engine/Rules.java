package net.vanexel.sudoku.engine;

/** Board validation helpers for the UI. */
public final class Rules {
    private Rules() {}

    /** Marks every filled cell whose value also appears in its row, column or box. */
    public static boolean[] conflicts(int[] grid) {
        Board.check(grid);
        boolean[] out = new boolean[Board.SIZE];
        for (int i = 0; i < Board.SIZE; i++) {
            int v = grid[i];
            if (v == 0) continue;
            for (int p : Board.PEERS[i]) {
                if (grid[p] == v) {
                    out[i] = true;
                    break;
                }
            }
        }
        return out;
    }

    /** True when every cell is filled and matches the solution. */
    public static boolean isComplete(int[] grid, int[] solution) {
        Board.check(grid);
        Board.check(solution);
        for (int i = 0; i < Board.SIZE; i++) {
            if (grid[i] == 0 || grid[i] != solution[i]) return false;
        }
        return true;
    }
}
