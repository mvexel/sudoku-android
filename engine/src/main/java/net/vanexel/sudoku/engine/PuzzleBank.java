package net.vanexel.sudoku.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only bank of pregenerated puzzles for one level, as written by {@link BankBuilder}.
 *
 * <p>File format (UTF-8): one puzzle per line, {@code <givens81> <TECHNIQUE>}, where givens are
 * digits with '0' for empty and TECHNIQUE is a {@link Technique} enum name (the hardest technique
 * needed). Blank lines and lines starting with '#' are ignored and do not count as entries.
 * Entry indices are 0-based and stable for a given builder seed; {@link #getId} is stable
 * regardless of seed, count or order.
 *
 * <p>Loading keeps only the raw lines (about 100 bytes each); {@link #get} parses and solves one
 * entry on demand (the solution is recomputed, typically around a millisecond).
 */
public final class PuzzleBank {
    private final Difficulty level;
    private final String[] lines;
    private final String[] ids;
    private Map<String, Integer> byId;      // built lazily

    private PuzzleBank(Difficulty level, List<String> lines) {
        this.level = level;
        this.lines = lines.toArray(new String[0]);
        this.ids = new String[this.lines.length];
    }

    /** Loads a level file from a UTF-8 stream. The stream is read fully but not closed. */
    public static PuzzleBank read(Difficulty level, InputStream in) throws IOException {
        return read(level, new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Loads a level file from a reader. The reader is read fully but not closed. */
    public static PuzzleBank read(Difficulty level, Reader reader) throws IOException {
        if (level == null) throw new IllegalArgumentException("level is null");
        BufferedReader br = new BufferedReader(reader);
        List<String> out = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            line = line.trim();
            if (isEntry(line)) {
                validate(line, lineNo);
                out.add(line);
            }
        }
        return new PuzzleBank(level, out);
    }

    /** Counts entries in a level file without parsing them. The stream is not closed. */
    public static int count(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        int n = 0;
        String line;
        while ((line = br.readLine()) != null) if (isEntry(line.trim())) n++;
        return n;
    }

    public Difficulty getLevel() { return level; }

    /** Number of puzzles in the bank. */
    public int size() { return lines.length; }

    /** Stable id of entry {@code index} (hash of its givens, see {@link Puzzle#idOf}). */
    public String getId(int index) {
        String id = ids[index];
        if (id == null) ids[index] = id = Puzzle.idOf(parseGivens(lines[index]));
        return id;
    }

    /** Index of the entry with this id, or -1 if it is not in the bank. */
    public synchronized int indexOf(String id) {
        if (byId == null) {
            byId = new HashMap<>(lines.length * 2);
            for (int i = 0; i < lines.length; i++) byId.put(getId(i), i);
        }
        Integer i = byId.get(id);
        return i == null ? -1 : i;
    }

    /** Hardest technique of entry {@code index}, without solving. */
    public Technique getTechnique(int index) {
        return Technique.valueOf(lines[index].substring(82));
    }

    /** Givens of entry {@code index}, without solving. */
    public int[] getGivens(int index) { return parseGivens(lines[index]); }

    /** Full puzzle for entry {@code index}; the solution is recomputed (and checked unique). */
    public Puzzle get(int index) {
        int[] givens = parseGivens(lines[index]);
        if (Solver.countSolutions(givens, 2) != 1) {
            throw new IllegalStateException("bank entry " + index + " does not have a unique solution");
        }
        return new Puzzle(givens, Solver.solve(givens), level, getTechnique(index), index);
    }

    /** Formats a puzzle as one bank line. */
    public static String format(Puzzle p) {
        return p.givensString() + " " + p.getHardestTechnique().name();
    }

    // ---- parsing ----

    private static boolean isEntry(String line) {
        return !line.isEmpty() && line.charAt(0) != '#';
    }

    private static void validate(String line, int lineNo) throws IOException {
        boolean ok = line.length() > 82 && line.charAt(81) == ' ';
        for (int i = 0; ok && i < 81; i++) ok = line.charAt(i) >= '0' && line.charAt(i) <= '9';
        if (ok) {
            try {
                Technique.valueOf(line.substring(82));
            } catch (IllegalArgumentException e) {
                ok = false;
            }
        }
        if (!ok) throw new IOException("malformed puzzle bank line " + lineNo + ": " + line);
    }

    private static int[] parseGivens(String line) {
        int[] g = new int[Board.SIZE];
        for (int i = 0; i < Board.SIZE; i++) g[i] = line.charAt(i) - '0';
        return g;
    }
}
