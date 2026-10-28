package net.vanexel.sudoku.engine;

import static net.vanexel.sudoku.engine.TestGrids.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class GeneratorTest {
    private static final int SEEDS = 10;

    @Test
    public void generatedPuzzlesAreValidForEveryLevel() {
        for (Difficulty d : Difficulty.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                Puzzle p = Generator.generate(d, seed);
                String ctx = d + " seed " + seed;
                int[] g = p.getGivens(), s = p.getSolution();
                assertEquals(ctx, 1, Solver.countSolutions(g, 2));
                assertTrue(ctx, isValidSolution(s));
                assertTrue(ctx, consistent(g, s));
                assertTrue(ctx, isSymmetric(g));
                Rater.Rating r = Rater.rate(g);
                assertEquals(ctx, d, r.getDifficulty());
                assertEquals(ctx, r.getHardest(), p.getHardestTechnique());
                assertArrayEquals(ctx, s, r.getGrid());
                assertEquals(ctx, d, p.getDifficulty());
                assertEquals(ctx, seed, p.getSeed());
                assertEquals(ctx, Puzzle.idOf(g), p.getId());
                assertEquals(ctx, p.getHardestTechnique().getDisplayName(), p.getHardestTechniqueName());
            }
        }
    }

    @Test
    public void deterministicBySeed() {
        for (Difficulty d : Difficulty.values()) {
            assertEquals(Generator.generate(d, 123), Generator.generate(d, 123));
        }
        assertFalse(java.util.Arrays.equals(Generator.generate(Difficulty.HARD, 1).getGivens(),
                Generator.generate(Difficulty.HARD, 2).getGivens()));
    }

    @Test
    public void puzzleIsImmutable() {
        Puzzle p = Generator.generate(Difficulty.EXPERT, 7);
        int[] g = p.getGivens();
        g[0] = g[0] == 0 ? 1 : 0;
        assertNotEquals(g[0], p.getGivens()[0]);
    }
}
