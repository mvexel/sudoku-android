package net.vanexel.sudoku.engine;

import static net.vanexel.sudoku.engine.TestGrids.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class RulesTest {

    @Test
    public void validSolutionHasNoConflicts() {
        for (boolean b : Rules.conflicts(parse(INKALA_SOLUTION))) assertFalse(b);
    }

    @Test
    public void marksRowColumnAndBoxDuplicatesOnly() {
        int[] g = new int[81];
        g[0] = 4; g[8] = 4;      // row 0
        g[20] = 7; g[74] = 7;    // column 2
        g[30] = 3; g[50] = 3;    // box 4
        g[60] = 9;               // lone value
        boolean[] cf = Rules.conflicts(g);
        int marked = 0;
        for (int i = 0; i < 81; i++) {
            boolean expected = i == 0 || i == 8 || i == 20 || i == 74 || i == 30 || i == 50;
            assertEquals("cell " + i, expected, cf[i]);
            if (cf[i]) marked++;
        }
        assertEquals(6, marked);
    }

    @Test
    public void isComplete() {
        int[] sol = parse(INKALA_SOLUTION);
        assertTrue(Rules.isComplete(sol, sol));
        int[] hole = sol.clone();
        hole[10] = 0;
        assertFalse(Rules.isComplete(hole, sol));
        int[] bad = sol.clone();
        bad[10] = sol[10] % 9 + 1;
        assertFalse(Rules.isComplete(bad, sol));
    }
}
