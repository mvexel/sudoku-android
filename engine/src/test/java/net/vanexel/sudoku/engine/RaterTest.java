package net.vanexel.sudoku.engine;

import static net.vanexel.sudoku.engine.TestGrids.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class RaterTest {

    @Test
    public void inkalaIsTooHardWithoutGuessing() {
        Rater.Rating r = Rater.rate(parse(INKALA));
        assertFalse(r.isSolved());
        assertNull(r.getDifficulty());
        assertTrue(consistent(r.getGrid(), parse(INKALA_SOLUTION)));
    }

    @Test
    public void basicOnlyIsTooEasy() {
        int[] g = parse(INKALA_SOLUTION);
        g[40] = 0;
        Rater.Rating r = Rater.rate(g);
        assertTrue(r.isSolved());
        assertTrue(r.isTooEasy());
        assertNull(r.getDifficulty());
        assertEquals(Technique.NAKED_SINGLE, r.getHardest());
    }

    @Test
    public void craftedJellyfish() {
        // Digit 1 pencil marks; every other digit is a candidate everywhere.
        String[] marks = {"1..11....", "1..11..1.", "11.1.111.", "1...1..1.", ".11.....1",
                "111.11.11", "...1...1.", ".1....1..", "..11.1..1"};
        int[] values = new int[81], cand = new int[81];
        for (int i = 0; i < 81; i++) cand[i] = 0x1FE | (marks[i / 9].charAt(i % 9) == '1' ? 1 : 0);
        int[] before = cand.clone();
        assertEquals(Technique.JELLYFISH, Rater.applyNextStep(values, cand));
        // Base rows 0,1,3,6 / cover columns 0,3,4,7: digit 1 leaves the cover columns elsewhere.
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < 81; i++) {
            if (cand[i] != before[i]) {
                changed.add(i);
                assertEquals(before[i] & ~1, cand[i]);
            }
        }
        assertEquals(Arrays.asList(18, 21, 25, 45, 49, 52, 75), changed);
    }

    @Test
    public void craftedHiddenTriple() {
        int[] values = new int[81], cand = new int[81];
        Arrays.fill(cand, 0x1FF);
        for (int c = 0; c < 9; c++) cand[c] = c % 4 == 0 ? 0x1FF : 0x1F8;  // 1-3 only at cells 0,4,8
        assertEquals(Technique.HIDDEN_TRIPLE, Rater.applyNextStep(values, cand));
        assertEquals(0x7, cand[0]);
        assertEquals(0x7, cand[4]);
        assertEquals(0x7, cand[8]);
        for (int i = 0; i < 81; i++) {
            if (i != 0 && i != 4 && i != 8) assertEquals(i < 9 ? 0x1F8 : 0x1FF, cand[i]);
        }
    }

    @Test
    public void neverContradictsTheSolution() {
        Random rnd = new Random(42);
        for (int n = 0; n < 150; n++) {
            int[] full = Generator.generate(Difficulty.values()[n % 3], 70_000 + n).getSolution();
            int[] puz = carveUnique(full, rnd);           // unconstrained: any rating, incl. too hard
            Rater.Rating r = Rater.rate(puz);
            if (r.isSolved()) assertArrayEquals(full, r.getGrid());
            else assertTrue(consistent(r.getGrid(), full));
        }
    }

    @Test
    public void techniqueTiers() {
        assertNull(Technique.POINTING.getDifficulty());
        assertEquals(Difficulty.HARD, Technique.X_WING.getDifficulty());
        assertEquals(Difficulty.EXPERT, Technique.JELLYFISH.getDifficulty());
        assertEquals(Difficulty.EXTREME, Technique.XY_CHAIN.getDifficulty());
        assertEquals("XY-Wing", Technique.XY_WING.getDisplayName());
    }

    private static int[] carveUnique(int[] full, Random rnd) {
        int[] g = full.clone();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 41; i++) order.add(i);
        Collections.shuffle(order, rnd);
        for (int a : order) {
            g[a] = 0;
            g[80 - a] = 0;
            if (Solver.countSolutions(g, 2) != 1) {
                g[a] = full[a];
                g[80 - a] = full[80 - a];
            }
        }
        return g;
    }
}
