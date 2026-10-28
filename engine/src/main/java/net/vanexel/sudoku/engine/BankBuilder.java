package net.vanexel.sudoku.engine;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Build-time CLI that writes the puzzle bank:
 * {@code <out>/puzzles/hard.txt}, {@code expert.txt} and {@code extreme.txt}.
 *
 * <pre>
 * java net.vanexel.sudoku.engine.BankBuilder --out DIR [--count N] [--seed S] [--threads T]
 * </pre>
 *
 * <p>Deterministic for the same seed regardless of thread count: entry i of a level comes from
 * seed {@code mix(mix(seed, level), i)} and output is written in index order. Entry i depends
 * only on (seed, level, i) and on earlier entries (for deduplication), so increasing --count
 * appends without changing existing indices. Duplicates (same givens id, across all levels)
 * are replaced deterministically by retrying with derived seeds.
 */
public final class BankBuilder {
    public static final int DEFAULT_COUNT = 500;
    public static final long DEFAULT_SEED = 1L;

    private BankBuilder() {}

    public static void main(String[] args) throws Exception {
        Path out = null;
        int count = DEFAULT_COUNT;
        long seed = DEFAULT_SEED;
        int threads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (i + 1 >= args.length) usage("missing value for " + a);
            String v = args[++i];
            switch (a) {
                case "--out": out = Paths.get(v); break;
                case "--count": count = Integer.parseInt(v); break;
                case "--seed": seed = Long.parseLong(v); break;
                case "--threads": threads = Math.max(1, Integer.parseInt(v)); break;
                default: usage("unknown option " + a);
            }
        }
        if (out == null) usage("--out is required");
        if (count < 0) usage("--count must be >= 0");
        build(out, count, seed, threads);
    }

    private static void usage(String msg) {
        System.err.println("BankBuilder: " + msg);
        System.err.println("usage: BankBuilder --out DIR [--count N] [--seed S] [--threads T]");
        System.exit(2);
    }

    /** Generates all levels and writes them under {@code out/puzzles/}. */
    public static void build(Path out, int count, long seed, int threads) throws Exception {
        Path dir = out.resolve("puzzles");
        Files.createDirectories(dir);
        Set<String> seenIds = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long totalStart = System.nanoTime();
        try {
            for (Difficulty level : Difficulty.values()) {
                long t0 = System.nanoTime();
                List<Puzzle> puzzles = generateLevel(pool, level, count, seed, seenIds);
                double secs = (System.nanoTime() - t0) / 1e9;
                Path file = dir.resolve(level.name().toLowerCase(Locale.ROOT) + ".txt");
                write(file, level, seed, puzzles);
                System.out.printf(Locale.ROOT, "%-7s %6d puzzles in %7.2f s  (%.1f/s, %.1f/s per thread, %d threads) -> %s (%d bytes)%n",
                        level, count, secs, count / secs, count / secs / threads, threads, file, Files.size(file));
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.printf(Locale.ROOT, "total %.2f s%n", (System.nanoTime() - totalStart) / 1e9);
    }

    /** Seed for entry {@code index} of {@code level}; retry > 0 is used only to replace duplicates. */
    static long entrySeed(long baseSeed, Difficulty level, int index, int retry) {
        long s = Generator.mix(Generator.mix(baseSeed, level.ordinal()), index);
        return retry == 0 ? s : Generator.mix(s, 1_000_000L + retry);
    }

    private static List<Puzzle> generateLevel(ExecutorService pool, Difficulty level, int count,
                                              long seed, Set<String> seenIds) throws Exception {
        List<Future<Puzzle>> futures = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long s = entrySeed(seed, level, i, 0);
            futures.add(pool.submit(() -> Generator.generate(level, s)));
        }
        List<Puzzle> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Puzzle p = futures.get(i).get();
            // Deduplicate in index order so the outcome never depends on scheduling.
            for (int retry = 1; !seenIds.add(p.getId()); retry++) {
                p = Generator.generate(level, entrySeed(seed, level, i, retry));
            }
            result.add(p);
        }
        return result;
    }

    private static void write(Path file, Difficulty level, long seed, List<Puzzle> puzzles) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write("# sudoku puzzle bank: level=" + level + " seed=" + seed + " count=" + puzzles.size()
                    + " format=<givens81> <hardestTechnique>\n");
            for (Puzzle p : puzzles) {
                w.write(PuzzleBank.format(p));
                w.write('\n');
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
