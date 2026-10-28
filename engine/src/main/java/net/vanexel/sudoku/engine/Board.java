package net.vanexel.sudoku.engine;

/**
 * Static geometry of a 9x9 board. Cells are indexed 0..80 in row-major order.
 * Units are numbered 0..26: rows 0-8, columns 9-17, boxes 18-26.
 */
public final class Board {
    public static final int SIZE = 81;
    /** Bitmask with all nine digit bits set (bit d-1 represents digit d). */
    public static final int ALL = 0x1FF;

    /** The 9 cell indices of each of the 27 units. */
    static final int[][] UNITS = new int[27][9];
    /** The 20 peers (same row, column or box) of each cell. */
    static final int[][] PEERS = new int[SIZE][20];
    /** IS_PEER[a][b] is true when a != b and they share a unit. */
    static final boolean[][] IS_PEER = new boolean[SIZE][SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            int r = row(i), c = col(i), b = box(i);
            UNITS[r][c] = i;
            UNITS[9 + c][r] = i;
            UNITS[18 + b][(r % 3) * 3 + c % 3] = i;
        }
        for (int a = 0; a < SIZE; a++) {
            int n = 0;
            for (int b = 0; b < SIZE; b++) {
                if (a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))) {
                    IS_PEER[a][b] = true;
                    PEERS[a][n++] = b;
                }
            }
        }
    }

    private Board() {}

    public static int row(int cell) { return cell / 9; }

    public static int col(int cell) { return cell % 9; }

    public static int box(int cell) { return (cell / 27) * 3 + (cell % 9) / 3; }

    /** Bit for digit d (1..9). */
    static int bit(int d) { return 1 << (d - 1); }

    /** Digit for a single-bit mask. */
    static int digit(int singleBit) { return Integer.numberOfTrailingZeros(singleBit) + 1; }

    /** Throws if the grid is not 81 values in 0..9. */
    static void check(int[] grid) {
        if (grid == null || grid.length != SIZE) {
            throw new IllegalArgumentException("grid must have 81 cells");
        }
        for (int v : grid) {
            if (v < 0 || v > 9) throw new IllegalArgumentException("cell value out of range: " + v);
        }
    }
}
