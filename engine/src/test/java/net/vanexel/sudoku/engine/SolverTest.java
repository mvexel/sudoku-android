package net.vanexel.sudoku.engine;

import static net.vanexel.sudoku.engine.TestGrids.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class SolverTest {

    @Test
    public void solvesInkalasHardestPuzzle() {
        int[] p = parse(INKALA);
        int[] before = p.clone();
        int[] s = Solver.solve(p);
        assertArrayEquals(parse(INKALA_SOLUTION), s);
        assertTrue(isValidSolution(s));
        assertArrayEquals("input must not be modified", before, p);
    }

    @Test
    public void countsZeroSolutions() {
        int[] dup = new int[81];
        dup[0] = 5;
        dup[1] = 5;
        assertEquals(0, Solver.countSolutions(dup, 2));
        assertNull(Solver.solve(dup));

        int[] blocked = new int[81];                     // cell 0 has no candidate left
        for (int c = 1; c <= 8; c++) blocked[c] = c;
        blocked[36] = 9;
        assertEquals(0, Solver.countSolutions(blocked, 2));
    }

    @Test
    public void countsOneSolution() {
        assertEquals(1, Solver.countSolutions(parse(INKALA), 2));
        assertEquals(1, Solver.countSolutions(parse(INKALA_SOLUTION), 2));
    }

    @Test
    public void countsMultipleSolutionsAndStopsAtLimit() {
        assertEquals(2, Solver.countSolutions(new int[81], 2));
        assertEquals(5, Solver.countSolutions(new int[81], 5));
        int[] sparse = parse(INKALA);
        sparse[0] = 0;
        assertEquals(2, Solver.countSolutions(sparse, 2));
    }

    @Test
    public void deadlyRectangleHasExactlyTwoSolutions() {
        int[] g = parse(INKALA_SOLUTION);
        // Find two cells pairs {a,b}/{b,a} spanning two rows, two columns and two boxes.
        int[] ur = null;
        outer:
        for (int r1 = 0; r1 < 9; r1++)
            for (int r2 = r1 + 1; r2 < 9; r2++) {
                if (r1 / 3 != r2 / 3) continue;
                for (int c1 = 0; c1 < 9; c1++)
                    for (int c2 = c1 + 1; c2 < 9; c2++) {
                        if (c1 / 3 == c2 / 3) continue;
                        if (g[r1 * 9 + c1] == g[r2 * 9 + c2] && g[r1 * 9 + c2] == g[r2 * 9 + c1]) {
                            ur = g.clone();
                            ur[r1 * 9 + c1] = ur[r1 * 9 + c2] = ur[r2 * 9 + c1] = ur[r2 * 9 + c2] = 0;
                            break outer;
                        }
                    }
            }
        assertNotNull(ur);
        assertEquals(2, Solver.countSolutions(ur, 10));
    }
}
