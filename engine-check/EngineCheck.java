import net.vanexel.sudoku.engine.BankBuilder;
import net.vanexel.sudoku.engine.Difficulty;
import net.vanexel.sudoku.engine.Generator;
import net.vanexel.sudoku.engine.Hints;
import net.vanexel.sudoku.engine.Puzzle;
import net.vanexel.sudoku.engine.PuzzleBank;
import net.vanexel.sudoku.engine.Rater;
import net.vanexel.sudoku.engine.Rules;
import net.vanexel.sudoku.engine.Solver;
import net.vanexel.sudoku.engine.Technique;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Standalone check harness for the puzzle engine (no JUnit needed). Mirrors the JUnit tests in
 * engine/src/test and adds timing. From the project root:
 *
 * <pre>
 *   javac --release 17 -d /tmp/engine-check engine/src/main/java/net/vanexel/sudoku/engine/*.java engine-check/EngineCheck.java
 *   java -cp /tmp/engine-check EngineCheck [seedsPerLevel]
 * </pre>
 */
public class EngineCheck {
    static final String INKALA =
            "8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..";
    static final String INKALA_SOLUTION =
            "812753649943682175675491283154237896369845721287169534521974368438526917796318452";

    /** Digit-1 pencil marks (rows of 9) where the first applicable technique is a row jellyfish. */
    static final String[] JELLYFISH_MARKS = {
            "1..11....", "1..11..1.", "11.1.111.", "1...1..1.", ".11.....1",
            "111.11.11", "...1...1.", ".1....1..", "..11.1..1"};
    /** Cells the jellyfish (base rows 0,1,3,6; cover columns 0,3,4,7) must clear digit 1 from. */
    static final int[] JELLYFISH_ELIMS = {18, 21, 25, 45, 49, 52, 75};

    static int passed, failed;

    public static void main(String[] args) throws Exception {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        section("Solver");
        solverChecks();
        section("Rater");
        raterChecks();
        section("Generator (" + seeds + " seeds per level)");
        generatorChecks(seeds);
        section("Puzzle bank");
        bankChecks();
        section("Hints");
        hintChecks();
        section("Rules");
        rulesChecks();
        section("Timing (single thread)");
        timing(seeds);

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- solver

    static void solverChecks() {
        int[] p = parse(INKALA);
        int[] before = p.clone();
        int[] s = Solver.solve(p);
        check("solve Inkala returns the known solution", Arrays.equals(s, parse(INKALA_SOLUTION)));
        check("Inkala solution is a valid grid", isValidSolution(s));
        check("solve does not modify input", Arrays.equals(p, before));
        check("Inkala has exactly one solution", Solver.countSolutions(p, 2) == 1);

        int[] dup = new int[81];
        dup[0] = 5;
        dup[1] = 5;
        check("conflicting givens -> 0 solutions, solve null",
                Solver.countSolutions(dup, 2) == 0 && Solver.solve(dup) == null);
        int[] blocked = new int[81];
        for (int c = 1; c <= 8; c++) blocked[c] = c;      // row 0 holds 1..8, col 0 holds a 9
        blocked[36] = 9;
        check("cell with no candidates -> 0 solutions", Solver.countSolutions(blocked, 2) == 0);

        check("empty grid -> stops at limit (2)", Solver.countSolutions(new int[81], 2) == 2);
        check("empty grid with limit 5 -> 5", Solver.countSolutions(new int[81], 5) == 5);
        int[] sparse = parse(INKALA);
        sparse[0] = 0;
        check("Inkala minus a clue -> >1 solutions", Solver.countSolutions(sparse, 2) == 2);
        int[] ur = deadlyRectangle(parse(INKALA_SOLUTION));
        check("solution minus a deadly rectangle -> exactly 2 (limit 10)",
                ur != null && Solver.countSolutions(ur, 10) == 2);
        check("full valid grid -> 1 solution", Solver.countSolutions(s, 2) == 1);
    }

    /** Removes 4 cells forming a deadly pattern (two digits swappable) -> exactly 2 solutions. */
    static int[] deadlyRectangle(int[] sol) {
        for (int r1 = 0; r1 < 9; r1++)
            for (int r2 = r1 + 1; r2 < 9; r2++) {
                if (r1 / 3 != r2 / 3) continue;
                for (int c1 = 0; c1 < 9; c1++)
                    for (int c2 = c1 + 1; c2 < 9; c2++) {
                        if (c1 / 3 == c2 / 3) continue;
                        int a = sol[r1 * 9 + c1], b = sol[r1 * 9 + c2];
                        if (sol[r2 * 9 + c1] == b && sol[r2 * 9 + c2] == a) {
                            int[] g = sol.clone();
                            g[r1 * 9 + c1] = g[r1 * 9 + c2] = g[r2 * 9 + c1] = g[r2 * 9 + c2] = 0;
                            return g;
                        }
                    }
            }
        return null;
    }

    // ---------------------------------------------------------------- rater

    static void raterChecks() {
        Rater.Rating inkala = Rater.rate(parse(INKALA));
        check("Inkala: too hard for the technique set (no guessing)",
                !inkala.isSolved() && inkala.getDifficulty() == null);
        check("Inkala: partial logical progress consistent with solution",
                consistent(inkala.getGrid(), parse(INKALA_SOLUTION)));

        int[] oneHole = parse(INKALA_SOLUTION);
        oneHole[40] = 0;
        Rater.Rating easy = Rater.rate(oneHole);
        check("single hole: solved, too easy (basic only)",
                easy.isSolved() && easy.isTooEasy() && easy.getHardest() == Technique.NAKED_SINGLE);

        // Crafted jellyfish state: digit 1 per JELLYFISH_MARKS, all other digits everywhere.
        int[] values = new int[81], cand = new int[81];
        for (int i = 0; i < 81; i++) cand[i] = 0x1FE | (JELLYFISH_MARKS[i / 9].charAt(i % 9) == '1' ? 1 : 0);
        int[] before = cand.clone();
        Technique t = Rater.applyNextStep(values, cand);
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < 81; i++) if (cand[i] != before[i]) changed.add(i);
        boolean onlyDigit1 = true;
        for (int i : changed) onlyDigit1 &= cand[i] == (before[i] & ~1);
        check("crafted state -> JELLYFISH with exactly the expected eliminations", t == Technique.JELLYFISH
                && changed.equals(toList(JELLYFISH_ELIMS)) && onlyDigit1);

        // Crafted hidden triple: in row 0, digits 1-3 only fit cells 0, 4 and 8 (different boxes).
        values = new int[81];
        cand = new int[81];
        Arrays.fill(cand, 0x1FF);
        for (int c = 0; c < 9; c++) cand[c] = (c % 4 == 0) ? 0x1FF : 0x1F8;
        t = Rater.applyNextStep(values, cand);
        boolean tripleOk = t == Technique.HIDDEN_TRIPLE && cand[0] == 0x7 && cand[4] == 0x7 && cand[8] == 0x7;
        for (int i = 0; i < 81; i++) if (i != 0 && i != 4 && i != 8) tripleOk &= cand[i] == (i < 9 ? 0x1F8 : 0x1FF);
        check("crafted state -> HIDDEN_TRIPLE restricting cells 0,4,8 to {1,2,3}", tripleOk);

        // Soundness + coverage over random minimal puzzles plus generated ones.
        Map<Technique, Integer> seen = new EnumMap<>(Technique.class);
        Map<String, Integer> outcome = new TreeMap<>();
        boolean sound = true;
        Random rnd = new Random(42);
        int n = 400;
        for (int k = 0; k < n; k++) {
            int[] full = Solver.solve(randomSeedGrid(rnd));
            int[] puz = carveUnique(full, rnd);
            Rater.Rating r = Rater.rate(puz);
            sound &= r.isSolved() ? Arrays.equals(r.getGrid(), full) : consistent(r.getGrid(), full);
            outcome.merge(!r.isSolved() ? "TOO_HARD" : r.isTooEasy() ? "BASIC" : r.getDifficulty().name(),
                    1, Integer::sum);
            tally(seen, r);
        }
        for (int k = 0; k < 60; k++) tally(seen, Rater.rate(Generator.generate(Difficulty.HARD, 5000 + k).getGivens()));
        seen.merge(Technique.JELLYFISH, 1, Integer::sum);       // covered by the crafted states above
        seen.merge(Technique.HIDDEN_TRIPLE, 1, Integer::sum);
        check("rater never contradicts the true solution (" + n + " random minimal puzzles)", sound);
        StringBuilder cov = new StringBuilder();
        boolean all = true;
        for (Technique tq : Technique.values()) {
            int c = seen.getOrDefault(tq, 0);
            all &= c > 0;
            cov.append(' ').append(tq).append('=').append(c);
        }
        check("every technique exercised at least once", all);
        System.out.println("    technique coverage (puzzles using it):" + cov);
        System.out.println("    ratings of " + n + " random symmetric minimal puzzles: " + outcome);
    }

    static void tally(Map<Technique, Integer> seen, Rater.Rating r) {
        for (Technique t : Technique.values()) if (r.getUses(t) > 0) seen.merge(t, 1, Integer::sum);
    }

    /** Random partial grid used to seed a full-grid solve (independent of Generator). */
    static int[] randomSeedGrid(Random rnd) {
        int[] g = new int[81];
        List<Integer> perm = new ArrayList<>();
        for (int d = 1; d <= 9; d++) perm.add(d);
        Collections.shuffle(perm, rnd);
        for (int c = 0; c < 9; c++) g[c] = perm.get(c);
        for (int k = 0; k < 6; k++) {
            int cell = 9 + rnd.nextInt(72);
            g[cell] = 1 + rnd.nextInt(9);
            if (Solver.countSolutions(g, 1) == 0) g[cell] = 0;
        }
        return g;
    }

    /** Symmetric carving keeping uniqueness only (no rating constraint). */
    static int[] carveUnique(int[] full, Random rnd) {
        int[] g = full.clone();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 41; i++) order.add(i);
        Collections.shuffle(order, rnd);
        for (int a : order) {
            int b = 80 - a;
            g[a] = 0;
            g[b] = 0;
            if (Solver.countSolutions(g, 2) != 1) {
                g[a] = full[a];
                g[b] = full[b];
            }
        }
        return g;
    }

    // ---------------------------------------------------------------- generator

    static void generatorChecks(int seeds) {
        for (Difficulty d : Difficulty.values()) {
            boolean unique = true, consistent = true, validSol = true, symmetric = true, rated = true,
                    deterministic = true, metaOk = true, logicMatches = true;
            int minG = 99, maxG = 0;
            Map<Technique, Integer> hardest = new TreeMap<>();
            for (long seed = 0; seed < seeds; seed++) {
                Puzzle p = Generator.generate(d, seed);
                int[] g = p.getGivens(), s = p.getSolution();
                unique &= Solver.countSolutions(g, 2) == 1;
                consistent &= consistent(g, s);
                validSol &= isValidSolution(s);
                symmetric &= isSymmetric(g);
                Rater.Rating r = Rater.rate(g);
                rated &= r.getDifficulty() == d && r.getHardest() == p.getHardestTechnique();
                logicMatches &= Arrays.equals(r.getGrid(), s);
                metaOk &= p.getDifficulty() == d && p.getSeed() == seed
                        && p.getHardestTechnique().getDifficulty() == d
                        && p.getHardestTechniqueName().equals(p.getHardestTechnique().getDisplayName())
                        && p.getId().equals(Puzzle.idOf(g)) && p.getId().length() == 12;
                deterministic &= p.equals(Generator.generate(d, seed));
                minG = Math.min(minG, p.getGivenCount());
                maxG = Math.max(maxG, p.getGivenCount());
                hardest.merge(p.getHardestTechnique(), 1, Integer::sum);
            }
            String tag = d + ": ";
            check(tag + "unique solution", unique);
            check(tag + "givens consistent with solution, solution valid", consistent && validSol);
            check(tag + "180-degree symmetry", symmetric);
            check(tag + "rater agrees on difficulty and hardest technique", rated);
            check(tag + "logical solve reaches the stored solution", logicMatches);
            check(tag + "metadata (difficulty, seed, technique name, id)", metaOk);
            check(tag + "deterministic for a seed", deterministic);
            System.out.println("    " + d + " givens " + minG + ".." + maxG + ", hardest: " + hardest);
        }
        check("different seeds give different puzzles",
                !Arrays.equals(Generator.generate(Difficulty.HARD, 1).getGivens(),
                        Generator.generate(Difficulty.HARD, 2).getGivens()));
        Puzzle p = Generator.generate(Difficulty.EXPERT, 7);
        int[] leak = p.getGivens();
        leak[0] = leak[0] == 0 ? 1 : 0;
        check("Puzzle is immutable (getters return copies)", !Arrays.equals(leak, p.getGivens()));
    }

    // ---------------------------------------------------------------- bank

    static void bankChecks() throws Exception {
        Path a = Files.createTempDirectory("bankA"), b = Files.createTempDirectory("bankB"),
                c = Files.createTempDirectory("bankC");
        System.out.println("    -- BankBuilder --count 30 --seed 7 --threads 2");
        BankBuilder.build(a, 30, 7, 2);
        System.out.println("    -- BankBuilder --count 30 --seed 7 --threads 1");
        BankBuilder.build(b, 30, 7, 1);
        System.out.println("    -- BankBuilder --count 20 --seed 7 --threads 2");
        BankBuilder.build(c, 20, 7, 2);
        Set<String> allIds = new HashSet<>();
        boolean sameAcrossThreads = true, prefixStable = true, rated = true, idsOk = true, lookups = true;
        for (Difficulty d : Difficulty.values()) {
            String name = "puzzles/" + d.name().toLowerCase() + ".txt";
            byte[] fa = Files.readAllBytes(a.resolve(name));
            sameAcrossThreads &= Arrays.equals(fa, Files.readAllBytes(b.resolve(name)));
            List<String> la = entries(fa), lc = entries(Files.readAllBytes(c.resolve(name)));
            prefixStable &= la.subList(0, 20).equals(lc);

            PuzzleBank bank;
            try (InputStream in = Files.newInputStream(a.resolve(name))) {
                bank = PuzzleBank.read(d, in);
            }
            check(d + ": count() == size() == 30", bank.size() == 30
                    && PuzzleBank.count(new ByteArrayInputStream(fa)) == 30);
            for (int i = 0; i < bank.size(); i++) {
                Puzzle p = bank.get(i);
                Rater.Rating r = Rater.rate(p.getGivens());
                rated &= r.getDifficulty() == d && r.getHardest() == bank.getTechnique(i)
                        && p.getHardestTechnique() == bank.getTechnique(i)
                        && Arrays.equals(r.getGrid(), p.getSolution()) && p.getSeed() == i;
                idsOk &= allIds.add(bank.getId(i)) && bank.getId(i).equals(p.getId());
                lookups &= bank.indexOf(bank.getId(i)) == i;
            }
            lookups &= bank.indexOf("000000000000") == -1;
        }
        check("bank files identical for 1 vs 2 threads", sameAcrossThreads);
        check("count 20 is an exact prefix of count 30 (stable indices)", prefixStable);
        check("bank entries re-rate to their level and technique; solution recomputed", rated);
        check("ids unique across all levels and equal to Puzzle.getId()", idsOk);
        check("indexOf(id) round-trips; unknown id -> -1", lookups);

        String text = "# comment\n\n" + "0".repeat(81) + " NOT_A_TECHNIQUE\n";
        boolean rejected = false;
        try {
            PuzzleBank.read(Difficulty.HARD, new StringReader(text));
        } catch (java.io.IOException e) {
            rejected = true;
        }
        check("malformed line rejected", rejected);
        check("comments and blank lines are not entries",
                PuzzleBank.count(new ByteArrayInputStream("# x\n\n".getBytes(StandardCharsets.UTF_8))) == 0);

        // Cost of PuzzleBank.get (parse + uniqueness check + solve) on the hardest level.
        PuzzleBank ex;
        try (InputStream in = Files.newInputStream(a.resolve("puzzles/extreme.txt"))) {
            ex = PuzzleBank.read(Difficulty.EXTREME, in);
        }
        for (int i = 0; i < 30; i++) ex.get(i);  // warm-up
        long t0 = System.nanoTime(), max = 0;
        for (int rep = 0; rep < 10; rep++)
            for (int i = 0; i < ex.size(); i++) {
                long s = System.nanoTime();
                ex.get(i);
                max = Math.max(max, System.nanoTime() - s);
            }
        System.out.printf("    PuzzleBank.get on EXTREME: avg %.3f ms, max %.3f ms (desktop JVM)%n",
                (System.nanoTime() - t0) / 1e6 / (10 * ex.size()), max / 1e6);
    }

    static List<String> entries(byte[] file) {
        List<String> out = new ArrayList<>();
        for (String l : new String(file, StandardCharsets.UTF_8).split("\n")) {
            if (!l.isEmpty() && !l.startsWith("#")) out.add(l);
        }
        return out;
    }

    // ---------------------------------------------------------------- hints

    static void hintChecks() {
        int[] sol = parse(INKALA_SOLUTION);
        check("solved grid -> -1", Hints.nextHint(sol, sol) == -1);

        int[] wrong = sol.clone();
        wrong[30] = sol[30] % 9 + 1;
        wrong[60] = sol[60] % 9 + 1;
        wrong[5] = 0;
        check("wrong value returned first (lowest wrong index)", Hints.nextHint(wrong, sol) == 30);

        int[] naked = new int[81];                       // row 4 filled except its last cell
        for (int c = 0; c < 8; c++) naked[36 + c] = sol[36 + c];
        check("naked single preferred over first empty", Hints.nextHint(naked, sol) == 44);

        int[] hidden = new int[81];                      // only digit sol[80] placed, except at 80
        for (int i = 0; i < 80; i++) if (sol[i] == sol[80]) hidden[i] = sol[80];
        check("hidden single used when there is no naked single", Hints.nextHint(hidden, sol) == 80);

        check("no singles -> first empty cell", Hints.nextHint(new int[81], sol) == 0);

        Random rnd = new Random(7);
        boolean ok = true;
        int wrongCases = 0, nakedCases = 0, hiddenCases = 0, otherCases = 0;
        for (int n = 0; n < 240; n++) {
            Puzzle p = Generator.generate(Difficulty.values()[n % 3], 1000 + n);
            int[] s = p.getSolution(), cur = p.getGivens();
            int fill = n % 4;                            // 0 = untouched givens, else fill ~1/(fill+1)
            if (fill > 0) for (int i = 0; i < 81; i++) if (cur[i] == 0 && rnd.nextInt(fill + 1) == 0) cur[i] = s[i];
            if (n % 5 == 0) {
                int i = rnd.nextInt(81);
                cur[i] = s[i] % 9 + 1;
            }
            int h = Hints.nextHint(cur, s);
            int firstWrong = -1;
            for (int i = 0; i < 81 && firstWrong < 0; i++) if (cur[i] != 0 && cur[i] != s[i]) firstWrong = i;
            boolean anyNaked = false, anyHidden = false, anyEmpty = false;
            for (int i = 0; i < 81; i++) {
                if (cur[i] != 0) continue;
                anyEmpty = true;
                anyNaked |= isNakedSingle(cur, i);
                anyHidden |= isHiddenSingle(cur, i);
            }
            if (firstWrong >= 0) { ok &= h == firstWrong; wrongCases++; }
            else if (!anyEmpty) ok &= h == -1;
            else if (anyNaked) { ok &= cur[h] == 0 && isNakedSingle(cur, h); nakedCases++; }
            else if (anyHidden) { ok &= cur[h] == 0 && isHiddenSingle(cur, h); hiddenCases++; }
            else { ok &= h >= 0 && cur[h] == 0; otherCases++; }
        }
        check("hint priority holds on 240 random states (wrong=" + wrongCases + ", naked=" + nakedCases
                + ", hidden=" + hiddenCases + ", other=" + otherCases + ")", ok);
    }

    static int candidates(int[] g, int cell) {
        int used = 0;
        for (int j = 0; j < 81; j++) if (g[j] != 0 && peers(cell, j)) used |= 1 << g[j];
        return ~used & 0x3FE;
    }

    static boolean isNakedSingle(int[] g, int cell) {
        return Integer.bitCount(candidates(g, cell)) == 1;
    }

    static boolean isHiddenSingle(int[] g, int cell) {
        int cand = candidates(g, cell);
        for (int d = 1; d <= 9; d++) {
            if ((cand & (1 << d)) == 0) continue;
            for (int kind = 0; kind < 3; kind++) {
                boolean alone = true;
                for (int j = 0; j < 81 && alone; j++) {
                    if (j == cell || g[j] != 0 || !sameUnit(kind, cell, j)) continue;
                    if ((candidates(g, j) & (1 << d)) != 0) alone = false;
                }
                if (alone) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- rules

    static void rulesChecks() {
        int[] sol = parse(INKALA_SOLUTION);
        boolean clean = true;
        for (boolean b : Rules.conflicts(sol)) clean &= !b;
        check("valid solution has no conflicts", clean);

        int[] g = new int[81];
        g[0] = 4; g[8] = 4;      // row 0
        g[20] = 7; g[74] = 7;    // column 2
        g[30] = 3; g[50] = 3;    // box 4
        g[60] = 9;               // lone value
        boolean[] cf = Rules.conflicts(g);
        check("row / column / box duplicates marked", cf[0] && cf[8] && cf[20] && cf[74] && cf[30] && cf[50]);
        int marked = 0;
        for (boolean b : cf) if (b) marked++;
        check("nothing else marked", marked == 6 && !cf[60]);

        check("isComplete(solution, solution)", Rules.isComplete(sol, sol));
        int[] hole = sol.clone();
        hole[10] = 0;
        int[] bad = sol.clone();
        bad[10] = sol[10] % 9 + 1;
        check("isComplete false with an empty cell or a wrong value",
                !Rules.isComplete(hole, sol) && !Rules.isComplete(bad, sol));
    }

    // ---------------------------------------------------------------- timing

    static void timing(int seeds) {
        for (long w = 0; w < 30; w++) Generator.generate(Difficulty.values()[(int) (w % 3)], 900_000 + w); // JIT warm-up
        System.out.printf("    %-8s %6s %9s %9s %8s %12s%n", "", "seeds", "avg ms", "max ms", "givens", "puzzles/s");
        for (int n : new int[]{seeds, 200}) {
            for (Difficulty d : Difficulty.values()) {
                long total = 0, max = 0, clues = 0;
                for (long s = 0; s < n; s++) {
                    long t0 = System.nanoTime();
                    Puzzle p = Generator.generate(d, 20_000 + s);
                    long dt = System.nanoTime() - t0;
                    total += dt;
                    max = Math.max(max, dt);
                    clues += p.getGivenCount();
                }
                System.out.printf("    %-8s %6d %9.2f %9.2f %8.1f %12.1f%n", d, n, total / 1e6 / n, max / 1e6,
                        (double) clues / n, n / (total / 1e9));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else failed++;
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
    }

    static void section(String name) {
        System.out.println("\n== " + name + " ==");
    }

    static int[] parse(String s) {
        int[] g = new int[81];
        for (int i = 0; i < 81; i++) {
            char ch = s.charAt(i);
            g[i] = ch >= '1' && ch <= '9' ? ch - '0' : 0;
        }
        return g;
    }

    static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int x : a) l.add(x);
        return l;
    }

    static boolean sameUnit(int kind, int a, int b) {
        switch (kind) {
            case 0: return a / 9 == b / 9;
            case 1: return a % 9 == b % 9;
            default: return a / 27 == b / 27 && (a % 9) / 3 == (b % 9) / 3;
        }
    }

    static boolean peers(int a, int b) {
        return a != b && (sameUnit(0, a, b) || sameUnit(1, a, b) || sameUnit(2, a, b));
    }

    static boolean isValidSolution(int[] s) {
        if (s == null || s.length != 81) return false;
        for (int i = 0; i < 81; i++) {
            if (s[i] < 1 || s[i] > 9) return false;
            for (int j = i + 1; j < 81; j++) if (peers(i, j) && s[i] == s[j]) return false;
        }
        return true;
    }

    static boolean consistent(int[] g, int[] s) {
        for (int i = 0; i < 81; i++) if (g[i] != 0 && g[i] != s[i]) return false;
        return true;
    }

    static boolean isSymmetric(int[] g) {
        for (int i = 0; i < 81; i++) if ((g[i] == 0) != (g[80 - i] == 0)) return false;
        return true;
    }
}
