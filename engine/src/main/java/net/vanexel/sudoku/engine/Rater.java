package net.vanexel.sudoku.engine;

import java.util.Arrays;

/**
 * Logical (human-style) solver used to rate puzzles. It repeatedly applies the easiest
 * {@link Technique} that makes progress and records the hardest one needed. It never guesses:
 * if the techniques run out before the grid is complete, the puzzle is "too hard".
 *
 * <p>Unique rectangles assume the puzzle has exactly one solution; only rate puzzles that do.
 */
public final class Rater {

    /** Result of rating a puzzle. */
    public static final class Rating {
        private final boolean solved;
        private final Technique hardest;
        private final int[] uses;
        private final int[] grid;

        Rating(boolean solved, Technique hardest, int[] uses, int[] grid) {
            this.solved = solved;
            this.hardest = hardest;
            this.uses = uses;
            this.grid = grid;
        }

        /** True if the logical solver completed the grid. */
        public boolean isSolved() { return solved; }

        /** Hardest technique applied (null if no step was needed). */
        public Technique getHardest() { return hardest; }

        /**
         * Player-facing difficulty, or null if the puzzle is too hard (not solved) or too easy
         * (solved with basic techniques only).
         */
        public Difficulty getDifficulty() {
            return solved && hardest != null ? hardest.getDifficulty() : null;
        }

        /** Solved, but only basic techniques were needed (below HARD). */
        public boolean isTooEasy() { return solved && getDifficulty() == null; }

        /** Number of times a technique made progress. */
        public int getUses(Technique t) { return uses[t.ordinal()]; }

        /** The grid as far as the logical solver got (complete if solved). */
        public int[] getGrid() { return grid.clone(); }

        @Override
        public String toString() {
            return solved ? "Rating{" + getDifficulty() + ", hardest=" + hardest + "}" : "Rating{TOO_HARD}";
        }
    }

    private static final Technique[] TECHNIQUES = Technique.values();

    private final int[] value = new int[Board.SIZE];
    private final int[] cand = new int[Board.SIZE];   // candidate bitmask per empty cell
    private int empty;
    private boolean broken;                           // contradiction found (invalid input)

    // scratch buffers for chain searches
    private final int[] queue = new int[Board.SIZE * 10];
    private final boolean[] seen = new boolean[Board.SIZE * 10];

    private Rater() {}

    /** Rates a puzzle (81 cells, 0 = empty). The input is not modified. */
    public static Rating rate(int[] grid) {
        Board.check(grid);
        return new Rater().run(grid);
    }

    /**
     * Applies one logical step to a pencil-mark state, in place: {@code values} (0 = empty) and
     * {@code candidates} (per-cell digit bitmask, bit d-1 = digit d; ignored for filled cells).
     * Candidates are taken as given, not recomputed from values. Returns the technique used, or
     * null if no technique applies. Useful for technique tests and explained hints.
     */
    public static Technique applyNextStep(int[] values, int[] candidates) {
        Board.check(values);
        if (candidates == null || candidates.length != Board.SIZE) {
            throw new IllegalArgumentException("candidates must have 81 cells");
        }
        Rater r = new Rater();
        for (int i = 0; i < Board.SIZE; i++) {
            r.value[i] = values[i];
            r.cand[i] = values[i] == 0 ? candidates[i] & Board.ALL : 0;
            if (values[i] == 0) r.empty++;
        }
        Technique t = r.empty == 0 ? null : r.step();
        System.arraycopy(r.value, 0, values, 0, Board.SIZE);
        System.arraycopy(r.cand, 0, candidates, 0, Board.SIZE);
        return t;
    }

    private Rating run(int[] grid) {
        int[] uses = new int[TECHNIQUES.length];
        Arrays.fill(cand, Board.ALL);
        empty = Board.SIZE;
        for (int i = 0; i < Board.SIZE; i++) {
            if (grid[i] == 0) continue;
            if ((cand[i] & Board.bit(grid[i])) == 0) return new Rating(false, null, uses, grid.clone());
            place(i, grid[i]);
        }
        Technique hardest = null;
        while (empty > 0 && !broken) {
            Technique used = step();
            if (used == null) break;
            uses[used.ordinal()]++;
            if (hardest == null || used.ordinal() > hardest.ordinal()) hardest = used;
        }
        return new Rating(empty == 0 && !broken, hardest, uses, value.clone());
    }

    /** Applies the first technique that makes progress; returns it, or null if stuck. */
    private Technique step() {
        if (nakedSingle()) return Technique.NAKED_SINGLE;
        if (hiddenSingle()) return Technique.HIDDEN_SINGLE;
        if (nakedSubset(2)) return Technique.NAKED_PAIR;
        if (pointing()) return Technique.POINTING;
        if (claiming()) return Technique.CLAIMING;
        if (hiddenSubset(2)) return Technique.HIDDEN_PAIR;
        if (nakedSubset(3)) return Technique.NAKED_TRIPLE;
        if (hiddenSubset(3)) return Technique.HIDDEN_TRIPLE;
        if (fish(2)) return Technique.X_WING;
        if (xyWing()) return Technique.XY_WING;
        if (xyzWing()) return Technique.XYZ_WING;
        if (fish(3)) return Technique.SWORDFISH;
        if (fish(4)) return Technique.JELLYFISH;
        if (uniqueRectangle()) return Technique.UNIQUE_RECTANGLE;
        if (wWing()) return Technique.W_WING;
        if (simpleColoring()) return Technique.SIMPLE_COLORING;
        if (xChain()) return Technique.X_CHAIN;
        if (xyChain()) return Technique.XY_CHAIN;
        return null;
    }

    // ---- state helpers ----

    private void place(int cell, int d) {
        value[cell] = d;
        cand[cell] = 0;
        empty--;
        int b = Board.bit(d);
        for (int p : Board.PEERS[cell]) eliminate(p, b);
    }

    /** Removes {@code bits} from an empty cell's candidates; true if anything changed. */
    private boolean eliminate(int cell, int bits) {
        if (value[cell] != 0 || (cand[cell] & bits) == 0) return false;
        cand[cell] &= ~bits;
        if (cand[cell] == 0) broken = true;
        return true;
    }

    /** Removes digit bit {@code b} from every cell that sees both {@code a} and {@code c}. */
    private boolean eliminateCommonPeers(int a, int c, int b) {
        boolean changed = false;
        for (int t : Board.PEERS[a]) {
            if (t != c && Board.IS_PEER[c][t]) changed |= eliminate(t, b);
        }
        return changed;
    }

    /** 9-bit mask of positions within unit {@code u} where digit bit {@code b} is a candidate. */
    private int positions(int u, int b) {
        int[] cells = Board.UNITS[u];
        int m = 0;
        for (int k = 0; k < 9; k++) if ((cand[cells[k]] & b) != 0) m |= 1 << k;
        return m;
    }

    private boolean bivalue(int cell) {
        return value[cell] == 0 && Integer.bitCount(cand[cell]) == 2;
    }

    // ---- singles ----

    private boolean nakedSingle() {
        for (int i = 0; i < Board.SIZE; i++) {
            if (value[i] == 0 && Integer.bitCount(cand[i]) == 1) {
                place(i, Board.digit(cand[i]));
                return true;
            }
        }
        return false;
    }

    private boolean hiddenSingle() {
        for (int u = 0; u < 27; u++) {
            for (int d = 1; d <= 9; d++) {
                int pm = positions(u, Board.bit(d));
                if (Integer.bitCount(pm) == 1) {
                    place(Board.UNITS[u][Integer.numberOfTrailingZeros(pm)], d);
                    return true;
                }
            }
        }
        return false;
    }

    // ---- subsets ----

    /** Naked pair (k=2) / triple (k=3): k cells in a unit whose candidates union to k digits. */
    private boolean nakedSubset(int k) {
        int[] idx = new int[9];
        for (int u = 0; u < 27; u++) {
            int[] cells = Board.UNITS[u];
            int n = 0;
            for (int j = 0; j < 9; j++) {
                int c = Integer.bitCount(cand[cells[j]]);
                if (c >= 2 && c <= k) idx[n++] = j;
            }
            for (int sub = 0; sub < (1 << n); sub++) {
                if (Integer.bitCount(sub) != k) continue;
                int union = 0, members = 0;
                for (int s = 0; s < n; s++) {
                    if ((sub & (1 << s)) != 0) {
                        union |= cand[cells[idx[s]]];
                        members |= 1 << idx[s];
                    }
                }
                if (Integer.bitCount(union) != k) continue;
                boolean changed = false;
                for (int j = 0; j < 9; j++) {
                    if ((members & (1 << j)) == 0) changed |= eliminate(cells[j], union);
                }
                if (changed) return true;
            }
        }
        return false;
    }

    /** Hidden pair (k=2) / triple (k=3): k digits confined to the same k cells of a unit. */
    private boolean hiddenSubset(int k) {
        int[] digits = new int[9];
        int[] pos = new int[10];
        for (int u = 0; u < 27; u++) {
            int[] cells = Board.UNITS[u];
            int n = 0;
            for (int d = 1; d <= 9; d++) {
                pos[d] = positions(u, Board.bit(d));
                int c = Integer.bitCount(pos[d]);
                if (c >= 2 && c <= k) digits[n++] = d;
            }
            for (int sub = 0; sub < (1 << n); sub++) {
                if (Integer.bitCount(sub) != k) continue;
                int union = 0, keep = 0;
                for (int s = 0; s < n; s++) {
                    if ((sub & (1 << s)) != 0) {
                        union |= pos[digits[s]];
                        keep |= Board.bit(digits[s]);
                    }
                }
                if (Integer.bitCount(union) != k) continue;
                boolean changed = false;
                for (int j = 0; j < 9; j++) {
                    if ((union & (1 << j)) != 0) changed |= eliminate(cells[j], ~keep & Board.ALL);
                }
                if (changed) return true;
            }
        }
        return false;
    }

    // ---- intersections ----

    /** Pointing: a digit's candidates in a box all lie on one row/column; clear it from the rest of that line. */
    private boolean pointing() {
        for (int bx = 0; bx < 9; bx++) {
            int[] cells = Board.UNITS[18 + bx];
            for (int d = 1; d <= 9; d++) {
                int b = Board.bit(d);
                int pm = positions(18 + bx, b);
                if (Integer.bitCount(pm) < 2) continue;
                for (int t = 0; t < 3; t++) {
                    boolean changed = false;
                    if ((pm & ~(0x7 << (3 * t))) == 0) {           // all in box-row t
                        for (int c : Board.UNITS[Board.row(cells[3 * t])]) {
                            if (Board.box(c) != bx) changed |= eliminate(c, b);
                        }
                    } else if ((pm & ~(0x49 << t)) == 0) {          // all in box-column t
                        for (int c : Board.UNITS[9 + Board.col(cells[t])]) {
                            if (Board.box(c) != bx) changed |= eliminate(c, b);
                        }
                    }
                    if (changed) return true;
                }
            }
        }
        return false;
    }

    /** Claiming: a digit's candidates in a row/column all lie in one box; clear it from the rest of that box. */
    private boolean claiming() {
        for (int u = 0; u < 18; u++) {
            int[] cells = Board.UNITS[u];
            for (int d = 1; d <= 9; d++) {
                int b = Board.bit(d);
                int pm = positions(u, b);
                if (Integer.bitCount(pm) < 2) continue;
                for (int g = 0; g < 3; g++) {
                    if ((pm & ~(0x7 << (3 * g))) != 0) continue;
                    boolean changed = false;
                    for (int c : Board.UNITS[18 + Board.box(cells[3 * g])]) {
                        boolean onLine = u < 9 ? Board.row(c) == u : Board.col(c) == u - 9;
                        if (!onLine) changed |= eliminate(c, b);
                    }
                    if (changed) return true;
                }
            }
        }
        return false;
    }

    // ---- fish ----

    /** Basic fish of size n (2 = X-wing, 3 = swordfish, 4 = jellyfish), row- and column-based. */
    private boolean fish(int n) {
        int[] lines = new int[9];
        int[] pm = new int[9];
        for (int d = 1; d <= 9; d++) {
            int b = Board.bit(d);
            for (int orient = 0; orient < 2; orient++) {       // 0: base rows / cover cols; 1: reverse
                int m = 0;
                for (int line = 0; line < 9; line++) {
                    pm[line] = positions(orient * 9 + line, b);
                    int c = Integer.bitCount(pm[line]);
                    if (c >= 2 && c <= n) lines[m++] = line;
                }
                if (m < n) continue;
                for (int sub = 0; sub < (1 << m); sub++) {
                    if (Integer.bitCount(sub) != n) continue;
                    int cover = 0, base = 0;
                    for (int s = 0; s < m; s++) {
                        if ((sub & (1 << s)) != 0) {
                            cover |= pm[lines[s]];
                            base |= 1 << lines[s];
                        }
                    }
                    if (Integer.bitCount(cover) != n) continue;
                    boolean changed = false;
                    for (int line = 0; line < 9; line++) {
                        if ((base & (1 << line)) != 0) continue;
                        for (int x = 0; x < 9; x++) {
                            if ((cover & (1 << x)) == 0) continue;
                            changed |= eliminate(orient == 0 ? line * 9 + x : x * 9 + line, b);
                        }
                    }
                    if (changed) return true;
                }
            }
        }
        return false;
    }

    // ---- wings ----

    /** XY-wing: pivot {a,b} sees pincers {a,c} and {b,c}; cells seeing both pincers cannot be c. */
    private boolean xyWing() {
        for (int p = 0; p < Board.SIZE; p++) {
            if (!bivalue(p)) continue;
            int pc = cand[p];
            for (int q1 : Board.PEERS[p]) {
                if (!bivalue(q1)) continue;
                int shared = cand[q1] & pc;
                if (Integer.bitCount(shared) != 1) continue;
                int z = cand[q1] & ~pc;
                int want = (pc & ~shared) | z;
                for (int q2 : Board.PEERS[p]) {
                    if (q2 == q1 || value[q2] != 0 || cand[q2] != want) continue;
                    if (eliminateCommonPeers(q1, q2, z)) return true;
                }
            }
        }
        return false;
    }

    /** XYZ-wing: pivot {x,y,z} sees pincers {x,z} and {y,z}; cells seeing all three cannot be z. */
    private boolean xyzWing() {
        for (int p = 0; p < Board.SIZE; p++) {
            int pc = cand[p];
            if (value[p] != 0 || Integer.bitCount(pc) != 3) continue;
            for (int q1 : Board.PEERS[p]) {
                if (!bivalue(q1) || (cand[q1] & ~pc) != 0) continue;
                for (int q2 : Board.PEERS[p]) {
                    if (q2 <= q1 || !bivalue(q2) || (cand[q2] & ~pc) != 0) continue;
                    if ((cand[q1] | cand[q2]) != pc) continue;
                    int z = cand[q1] & cand[q2];
                    boolean changed = false;
                    for (int t : Board.PEERS[p]) {
                        if (t != q1 && t != q2 && Board.IS_PEER[q1][t] && Board.IS_PEER[q2][t]) {
                            changed |= eliminate(t, z);
                        }
                    }
                    if (changed) return true;
                }
            }
        }
        return false;
    }

    /**
     * W-wing: two non-seeing bivalue cells {x,y} whose x's are connected by a strong link on x
     * (a unit where x has exactly two places, one seen by each cell): cells seeing both lose y.
     */
    private boolean wWing() {
        for (int a = 0; a < Board.SIZE; a++) {
            if (!bivalue(a)) continue;
            for (int c = a + 1; c < Board.SIZE; c++) {
                if (cand[c] != cand[a] || value[c] != 0 || Board.IS_PEER[a][c]) continue;
                for (int x = cand[a]; x != 0; x &= x - 1) {
                    int xb = Integer.lowestOneBit(x), yb = cand[a] & ~xb;
                    for (int u = 0; u < 27; u++) {
                        int pm = positions(u, xb);
                        if (Integer.bitCount(pm) != 2) continue;
                        int e = Board.UNITS[u][Integer.numberOfTrailingZeros(pm)];
                        int f = Board.UNITS[u][31 - Integer.numberOfLeadingZeros(pm)];
                        if (e == a || e == c || f == a || f == c) continue;
                        boolean linked = (Board.IS_PEER[a][e] && Board.IS_PEER[c][f])
                                || (Board.IS_PEER[a][f] && Board.IS_PEER[c][e]);
                        if (linked && eliminateCommonPeers(a, c, yb)) return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- uniqueness ----

    /**
     * Unique rectangle type 1: three corners of a 2-row/2-column/2-box rectangle hold the same
     * pair {x,y}; the fourth corner cannot be x or y (it would allow two solutions).
     */
    private boolean uniqueRectangle() {
        for (int r1 = 0; r1 < 9; r1++) {
            for (int c1 = 0; c1 < 9; c1++) {
                int a = r1 * 9 + c1;
                if (!bivalue(a)) continue;
                int m = cand[a];
                for (int c2 = c1 + 1; c2 < 9; c2++) {
                    int b = r1 * 9 + c2;
                    if (value[b] != 0 || cand[b] != m) continue;
                    for (int r2 = 0; r2 < 9; r2++) {
                        if (r2 == r1) continue;
                        int c = r2 * 9 + c1, d = r2 * 9 + c2;
                        if (value[c] != 0 || value[d] != 0) continue;
                        if (!twoBoxes(a, b, c, d)) continue;
                        if (cand[c] == m && (cand[d] & m) == m && cand[d] != m) {
                            eliminate(d, m);
                            return true;
                        }
                        if (cand[d] == m && (cand[c] & m) == m && cand[c] != m) {
                            eliminate(c, m);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean twoBoxes(int a, int b, int c, int d) {
        int mask = (1 << Board.box(a)) | (1 << Board.box(b)) | (1 << Board.box(c)) | (1 << Board.box(d));
        return Integer.bitCount(mask) == 2;
    }

    // ---- single-digit chains ----

    /** Calls {@code out.accept(partner)} for each conjugate (strong-link) partner of cell on digit b. */
    private int strongPartners(int cell, int b, int[] out) {
        int n = 0;
        int[] units = {Board.row(cell), 9 + Board.col(cell), 18 + Board.box(cell)};
        for (int u : units) {
            int pm = positions(u, b);
            if (Integer.bitCount(pm) != 2) continue;
            for (int k = pm; k != 0; k &= k - 1) {
                int other = Board.UNITS[u][Integer.numberOfTrailingZeros(k)];
                if (other != cell) out[n++] = other;
            }
        }
        return n;
    }

    /**
     * Simple coloring: two-color each cluster of strong links on one digit. If two cells of the
     * same color see each other, that color is false (wrap). A cell seeing both colors is false (trap).
     */
    private boolean simpleColoring() {
        int[] color = new int[Board.SIZE];
        int[] partners = new int[6];
        int[] comp = new int[Board.SIZE];
        for (int d = 1; d <= 9; d++) {
            int b = Board.bit(d);
            Arrays.fill(color, -1);
            for (int s = 0; s < Board.SIZE; s++) {
                if ((cand[s] & b) == 0 || color[s] >= 0) continue;
                if (strongPartners(s, b, partners) == 0) continue;
                // BFS the cluster
                int head = 0, tail = 0;
                comp[tail++] = s;
                color[s] = 0;
                while (head < tail) {
                    int c = comp[head++];
                    int n = strongPartners(c, b, partners);
                    for (int k = 0; k < n; k++) {
                        int p = partners[k];
                        if (color[p] < 0) {
                            color[p] = 1 - color[c];
                            comp[tail++] = p;
                        }
                    }
                }
                if (tail < 3) continue;   // a lone conjugate pair gives nothing
                // Color wrap
                for (int i = 0; i < tail; i++) {
                    for (int j = i + 1; j < tail; j++) {
                        if (color[comp[i]] == color[comp[j]] && Board.IS_PEER[comp[i]][comp[j]]) {
                            int bad = color[comp[i]];
                            boolean changed = false;
                            for (int k = 0; k < tail; k++) if (color[comp[k]] == bad) changed |= eliminate(comp[k], b);
                            if (changed) return true;
                        }
                    }
                }
                // Color trap
                boolean changed = false;
                for (int t = 0; t < Board.SIZE; t++) {
                    if ((cand[t] & b) == 0 || inComp(comp, tail, t)) continue;
                    boolean sees0 = false, sees1 = false;
                    for (int k = 0; k < tail; k++) {
                        if (Board.IS_PEER[t][comp[k]]) {
                            if (color[comp[k]] == 0) sees0 = true; else sees1 = true;
                        }
                    }
                    if (sees0 && sees1) changed |= eliminate(t, b);
                }
                if (changed) return true;
            }
        }
        return false;
    }

    private static boolean inComp(int[] comp, int n, int cell) {
        for (int k = 0; k < n; k++) if (comp[k] == cell) return true;
        return false;
    }

    /**
     * X-chain: from "start is not d", follow strong links (false -> true) and weak links
     * (true -> false). Any cell proven true means start or that cell is d, so their common
     * peers lose d. If start itself is proven true, it must be d.
     * State encoding: cell * 2 + (1 if the cell is d).
     */
    private boolean xChain() {
        int[] partners = new int[6];
        for (int d = 1; d <= 9; d++) {
            int b = Board.bit(d);
            for (int s = 0; s < Board.SIZE; s++) {
                if ((cand[s] & b) == 0 || strongPartners(s, b, partners) == 0) continue;
                Arrays.fill(seen, 0, Board.SIZE * 2, false);
                int head = 0, tail = 0;
                queue[tail++] = s * 2;
                seen[s * 2] = true;
                while (head < tail) {
                    int st = queue[head++], c = st >> 1;
                    if ((st & 1) == 0) {                     // c is not d -> partners are d
                        int n = strongPartners(c, b, partners);
                        for (int k = 0; k < n; k++) {
                            int ns = partners[k] * 2 + 1;
                            if (!seen[ns]) { seen[ns] = true; queue[tail++] = ns; }
                        }
                    } else {                                 // c is d -> peers are not d
                        for (int p : Board.PEERS[c]) {
                            if ((cand[p] & b) == 0) continue;
                            int ns = p * 2;
                            if (!seen[ns]) { seen[ns] = true; queue[tail++] = ns; }
                        }
                    }
                }
                if (seen[s * 2 + 1]) {                       // not-d implies d: s is d
                    cand[s] = b;
                    return true;
                }
                boolean changed = false;
                for (int e = 0; e < Board.SIZE; e++) {
                    if (e != s && seen[e * 2 + 1]) changed |= eliminateCommonPeers(s, e, b);
                }
                if (changed) return true;
            }
        }
        return false;
    }

    // ---- multi-digit chains ----

    /**
     * XY-chain: through bivalue cells only. Assume start s is not a (so it is its other digit);
     * each linked bivalue peer is then forced. Reaching a cell forced to a proves s or that cell
     * is a, so their common peers lose a. State encoding: cell * 10 + forced digit.
     */
    private boolean xyChain() {
        for (int s = 0; s < Board.SIZE; s++) {
            if (!bivalue(s)) continue;
            for (int am = cand[s]; am != 0; am &= am - 1) {
                int ab = Integer.lowestOneBit(am);
                int a = Board.digit(ab);
                Arrays.fill(seen, false);
                int head = 0, tail = 0;
                int startState = s * 10 + Board.digit(cand[s] & ~ab);
                queue[tail++] = startState;
                seen[startState] = true;
                while (head < tail) {
                    int st = queue[head++], c = st / 10, v = st % 10, vb = Board.bit(v);
                    for (int p : Board.PEERS[c]) {
                        if (!bivalue(p) || (cand[p] & vb) == 0) continue;
                        int ns = p * 10 + Board.digit(cand[p] & ~vb);
                        if (!seen[ns]) { seen[ns] = true; queue[tail++] = ns; }
                    }
                }
                if (seen[s * 10 + a]) {                      // not-a implies a: s is a
                    cand[s] = ab;
                    return true;
                }
                boolean changed = false;
                for (int e = 0; e < Board.SIZE; e++) {
                    if (e != s && seen[e * 10 + a]) changed |= eliminateCommonPeers(s, e, ab);
                }
                if (changed) return true;
            }
        }
        return false;
    }
}
