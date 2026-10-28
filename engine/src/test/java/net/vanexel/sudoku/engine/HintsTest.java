package net.vanexel.sudoku.engine;

import static net.vanexel.sudoku.engine.TestGrids.*;
import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

public class HintsTest {
    private final int[] sol = parse(INKALA_SOLUTION);

    @Test
    public void solvedReturnsMinusOne() {
        assertEquals(-1, Hints.nextHint(sol, sol));
    }

    @Test
    public void wrongValueFirst() {
        int[] g = sol.clone();
        g[30] = sol[30] % 9 + 1;
        g[60] = sol[60] % 9 + 1;
        g[5] = 0;
        assertEquals(30, Hints.nextHint(g, sol));
    }

    @Test
    public void nakedSinglePreferred() {
        int[] g = new int[81];
        for (int c = 0; c < 8; c++) g[36 + c] = sol[36 + c];   // row 4 missing only its last cell
        assertEquals(44, Hints.nextHint(g, sol));
    }

    @Test
    public void hiddenSingleWhenNoNakedSingle() {
        int[] g = new int[81];
        for (int i = 0; i < 80; i++) if (sol[i] == sol[80]) g[i] = sol[80];
        assertEquals(80, Hints.nextHint(g, sol));
    }

    @Test
    public void anyEmptyCellOtherwise() {
        assertEquals(0, Hints.nextHint(new int[81], sol));
    }

    @Test
    public void priorityHoldsOnRandomStates() {
        Random rnd = new Random(7);
        for (int n = 0; n < 60; n++) {
            Puzzle p = Generator.generate(Difficulty.values()[n % 3], 1000 + n);
            int[] s = p.getSolution(), cur = p.getGivens();
            int fill = n % 4;
            if (fill > 0) for (int i = 0; i < 81; i++) if (cur[i] == 0 && rnd.nextInt(fill + 1) == 0) cur[i] = s[i];
            if (n % 5 == 0) {
                int i = rnd.nextInt(81);
                cur[i] = s[i] % 9 + 1;
            }
            int h = Hints.nextHint(cur, s);
            int firstWrong = -1;
            for (int i = 0; i < 81 && firstWrong < 0; i++) if (cur[i] != 0 && cur[i] != s[i]) firstWrong = i;
            boolean anyNaked = false, anyHidden = false;
            for (int i = 0; i < 81; i++) {
                if (cur[i] != 0) continue;
                anyNaked |= isNakedSingle(cur, i);
                anyHidden |= isHiddenSingle(cur, i);
            }
            if (firstWrong >= 0) assertEquals(firstWrong, h);
            else if (anyNaked) assertTrue(cur[h] == 0 && isNakedSingle(cur, h));
            else if (anyHidden) assertTrue(cur[h] == 0 && isHiddenSingle(cur, h));
            else assertEquals(0, cur[h]);
        }
    }
}
