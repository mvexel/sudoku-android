package net.vanexel.sudoku.engine;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

public class PuzzleBankTest {

    private static Path file(Path root, Difficulty d) {
        return root.resolve("puzzles").resolve(d.name().toLowerCase(Locale.ROOT) + ".txt");
    }

    private static List<String> entries(Path f) throws IOException {
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (!l.isEmpty() && !l.startsWith("#")) out.add(l);
        }
        return out;
    }

    @Test
    public void builderIsDeterministicStableAndReadable() throws Exception {
        Path a = Files.createTempDirectory("bankA"), b = Files.createTempDirectory("bankB"),
                c = Files.createTempDirectory("bankC");
        BankBuilder.build(a, 12, 7, 2);
        BankBuilder.build(b, 12, 7, 1);
        BankBuilder.build(c, 8, 7, 3);
        Set<String> ids = new HashSet<>();
        for (Difficulty d : Difficulty.values()) {
            assertArrayEquals("thread count must not matter", Files.readAllBytes(file(a, d)),
                    Files.readAllBytes(file(b, d)));
            assertEquals("smaller count is a prefix", entries(file(a, d)).subList(0, 8), entries(file(c, d)));

            PuzzleBank bank;
            try (InputStream in = Files.newInputStream(file(a, d))) {
                bank = PuzzleBank.read(d, in);
            }
            try (InputStream in = Files.newInputStream(file(a, d))) {
                assertEquals(12, PuzzleBank.count(in));
            }
            assertEquals(12, bank.size());
            assertEquals(d, bank.getLevel());
            for (int i = 0; i < bank.size(); i++) {
                Puzzle p = bank.get(i);
                Rater.Rating r = Rater.rate(p.getGivens());
                assertEquals(d, r.getDifficulty());
                assertEquals(bank.getTechnique(i), r.getHardest());
                assertEquals(bank.getTechnique(i), p.getHardestTechnique());
                assertArrayEquals(r.getGrid(), p.getSolution());
                assertArrayEquals(bank.getGivens(i), p.getGivens());
                assertEquals(i, p.getSeed());
                assertEquals(p.getId(), bank.getId(i));
                assertTrue("ids unique across levels", ids.add(bank.getId(i)));
                assertEquals(i, bank.indexOf(bank.getId(i)));
            }
            assertEquals(-1, bank.indexOf("000000000000"));
        }
    }

    @Test
    public void formatRoundTrips() throws IOException {
        Puzzle p = Generator.generate(Difficulty.EXTREME, 3);
        String text = "# header\n\n" + PuzzleBank.format(p) + "\n";
        PuzzleBank bank = PuzzleBank.read(Difficulty.EXTREME, new StringReader(text));
        assertEquals(1, bank.size());
        assertEquals(1, PuzzleBank.count(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        Puzzle q = bank.get(0);
        assertArrayEquals(p.getGivens(), q.getGivens());
        assertArrayEquals(p.getSolution(), q.getSolution());
        assertEquals(p.getHardestTechnique(), q.getHardestTechnique());
        assertEquals(p.getId(), q.getId());
    }

    @Test(expected = IOException.class)
    public void rejectsMalformedLines() throws IOException {
        char[] zeros = new char[81];
        Arrays.fill(zeros, '0');
        PuzzleBank.read(Difficulty.HARD, new StringReader(new String(zeros) + " NOT_A_TECHNIQUE\n"));
    }

    @Test
    public void idIsStableAndShort() {
        int[] g = TestGrids.parse(TestGrids.INKALA);
        assertEquals(Puzzle.idOf(g), Puzzle.idOf(g.clone()));
        assertEquals(12, Puzzle.idOf(g).length());
        g[0] = 0;
        assertNotEquals(Puzzle.idOf(TestGrids.parse(TestGrids.INKALA)), Puzzle.idOf(g));
    }
}
