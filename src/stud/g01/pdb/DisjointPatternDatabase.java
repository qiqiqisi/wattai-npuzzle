package stud.g01.pdb;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Additive disjoint PDB with a cost partition over moved numbered tiles. */
public final class DisjointPatternDatabase implements Predictor {
    public static final int[][] FOUR_FOUR_FOUR_THREE = {
            {1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}, {13, 14, 15}};
    public static final int[][] FIVE_FIVE_FIVE = {
            {1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, 13, 14, 15}};
    /** Column-oriented alternative; a complete additive partition, not added to overlapping 555. */
    public static final int[][] FIVE_FIVE_FIVE_TRANSPOSED = {
            {1,5,9,13,2}, {6,10,14,3,7}, {11,15,4,8,12}};
    public static final int[][] EIGHT_PUZZLE = {{1, 2, 3, 4}, {5, 6, 7, 8}};
    private final PuzzleBoard goal;
    private final List<PatternTable> tables;
    private long lookups;

    public DisjointPatternDatabase(PuzzleBoard goal, int[][] patterns, Path cacheDirectory) throws IOException {
        this.goal = goal;
        int cells = goal.getSize() * goal.getSize();
        boolean[] seen = new boolean[cells];
        List<PatternTable> prepared = new ArrayList<>();
        String goalKey = goalKey(goal);
        for (int[] pattern : patterns) {
            StringBuilder name = new StringBuilder("v1-").append(goal.getSize()).append('-').append(goalKey);
            for (int tile : pattern) {
                if (tile <= 0 || tile >= cells || seen[tile]) throw new IllegalArgumentException("Patterns must be disjoint");
                seen[tile] = true;
                name.append('-').append(tile);
            }
            prepared.add(new PatternTable(goal, pattern, cacheDirectory.resolve(name + ".pdb")));
        }
        for (int tile = 1; tile < cells; tile++) if (!seen[tile])
            throw new IllegalArgumentException("Patterns must cover every numbered tile");
        tables = List.copyOf(prepared);
    }

    public static int[][] defaultPatterns(int size) {
        return switch (size) {
            case 3 -> EIGHT_PUZZLE;
            case 4 -> FOUR_FOUR_FOUR_THREE;
            default -> throw new IllegalArgumentException("PDB supports 3x3 or 4x4 boards");
        };
    }

    public List<PatternTable> tables() { return tables; }
    public long lookups() { return lookups; }

    @Override
    public int heuristics(State state, State target) {
        if (!(state instanceof PuzzleBoard board) || !goal.equals(target))
            throw new IllegalArgumentException("PDB goal mismatch");
        int value = 0;
        for (PatternTable table : tables) value += table.value(board);
        lookups++;
        return value;
    }

    private static String goalKey(PuzzleBoard goal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int row = 0; row < goal.getSize(); row++)
                for (int col = 0; col < goal.getSize(); col++) digest.update((byte) goal.tileAt(row, col));
            return HexFormat.of().formatHex(Arrays.copyOf(digest.digest(), 8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** The framework predictor is goal aware and builds/loads a table only on first use. */
    public static final class LazyPredictor implements Predictor {
        private PuzzleBoard activeGoal;
        private DisjointPatternDatabase active;
        @Override
        public synchronized int heuristics(State state, State goal) {
            if (!(goal instanceof PuzzleBoard target)) throw new IllegalArgumentException("Expected PuzzleBoard goal");
            if (!target.equals(activeGoal)) {
                try {
                    active = new DisjointPatternDatabase(target, defaultPatterns(target.getSize()),
                            Path.of("generated", "pdb"));
                } catch (IOException error) {
                    throw new IllegalStateException("Cannot build or load PDB", error);
                }
                activeGoal = target;
            }
            return active.heuristics(state, goal);
        }
    }
}
