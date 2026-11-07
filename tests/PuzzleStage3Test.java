import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.pdb.DisjointPatternDatabase;
import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.runner.PuzzleFeeder;
import stud.g01.visual.SolutionTrace;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verifies the abstract-state distances before using them for performance claims. */
public final class PuzzleStage3Test {
    private static int checks;
    private static final Path CACHE = Path.of("generated", "pdb", "test");

    public static void main(String[] args) throws Exception {
        PuzzleBoard goal3 = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,0}, 3);
        DisjointPatternDatabase pdb3 = new DisjointPatternDatabase(goal3,
                DisjointPatternDatabase.EIGHT_PUZZLE, CACHE);
        check("goal zero", pdb3.heuristics(goal3, goal3) == 0);
        for (PatternTable table : pdb3.tables()) {
            check("state count", table.states() == 15120);
            boolean[] observed = new boolean[table.states()];
            for (int rank = 0; rank < table.states(); rank++) {
                int returned = table.rank(table.unrank(rank));
                check("rank roundtrip " + rank, returned == rank && !observed[returned]);
                observed[returned] = true;
            }
        }
        DisjointPatternDatabase reloaded = new DisjointPatternDatabase(goal3,
                DisjointPatternDatabase.EIGHT_PUZZLE, CACHE);
        for (PatternTable table : reloaded.tables()) check("cache load", table.loaded());
        Path corruptFile = CACHE.resolve("corrupt-header.pdb");
        byte[] invalidCache = Files.readAllBytes(pdb3.tables().get(0).file());
        invalidCache[0] = 0;
        Files.write(corruptFile, invalidCache);
        PatternTable rebuilt = new PatternTable(goal3, pdb3.tables().get(0).tiles(), corruptFile);
        check("cache mismatch rebuild", !rebuilt.loaded() && rebuilt.value(goal3) == 0);
        Map<PuzzleBoard, Integer> distance = bfs(goal3, 8);
        for (Map.Entry<PuzzleBoard, Integer> entry : distance.entrySet()) {
            PuzzleBoard board = entry.getKey();
            int h = pdb3.heuristics(board, goal3);
            check("admissible", h >= 0 && h <= entry.getValue()
                    && h == reloaded.heuristics(board, goal3));
            for (PuzzleMove move : board.actions())
                check("consistent", h <= 1 + pdb3.heuristics(board.next(move), goal3));
        }
        PuzzleBoard customGoal = goal3.next(goal3.actions().iterator().next());
        DisjointPatternDatabase custom = new DisjointPatternDatabase(customGoal,
                DisjointPatternDatabase.EIGHT_PUZZLE, CACHE);
        check("arbitrary goal", custom.heuristics(customGoal, customGoal) == 0
                && custom.heuristics(goal3, customGoal) <= 1);
        PuzzleBoard goal4 = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0}, 4);
        DisjointPatternDatabase pdb4 = new DisjointPatternDatabase(goal4,
                DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE, CACHE);
        check("4x4 goal", pdb4.heuristics(goal4, goal4) == 0);
        PuzzleBoard state = goal4;
        Random random = new Random(20261002);
        for (int i = 0; i < 100; i++) {
            PuzzleMove[] moves = java.util.stream.StreamSupport.stream(state.actions().spliterator(), false)
                    .toArray(PuzzleMove[]::new);
            state = state.next(moves[random.nextInt(moves.length)]);
            int h = pdb4.heuristics(state, goal4);
            check("4x4 shallow bound", h >= 0 && h <= i + 1);
            for (PuzzleMove move : state.actions())
                check("4x4 consistency", h <= 1 + pdb4.heuristics(state.next(move), goal4));
            for (PuzzleMove move : state.actions()) {
                PuzzleBoard child = state.next(move);
                int swappedTile = state.tileAt(child.positionOf(0) / 4, child.positionOf(0) % 4);
                int chargedPatterns = 0;
                for (PatternTable table : pdb4.tables()) for (int tile : table.tiles())
                    if (tile == swappedTile) chargedPatterns++;
                check("cost partition charges exactly once", chargedPatterns == 1);
            }
        }
        DisjointPatternDatabase pdb555 = new DisjointPatternDatabase(goal4,
                DisjointPatternDatabase.FIVE_FIVE_FIVE, Path.of("generated", "pdb"));
        check("5-5-5 goal zero", pdb555.heuristics(goal4, goal4) == 0);
        for (PatternTable table : pdb555.tables()) {
            check("5-5-5 state count", table.states() == 5765760);
            for (int i = 0; i < 100; i++) {
                int rank = random.nextInt(table.states());
                check("5-5-5 sampled ranking", table.rank(table.unrank(rank)) == rank);
            }
        }
        PuzzleBoard one = goal4.next(goal4.actions().iterator().next());
        Deque<Node> path = new PuzzleFeeder().getAStar(HeuristicType.DISJOINT_PATTERN)
                .search(new NPuzzleProblem(one, goal4));
        check("PDB A* replay", path != null && path.size() == 1
                && one.next(path.getFirst().getAction()).equals(goal4));
        Path traceFile = Path.of("generated", "visual-test.tsv");
        Files.createDirectories(traceFile.getParent());
        Files.write(traceFile, List.of("format\tNPUZZLE_SOLUTION_V1", "algorithm\tAStar",
                "heuristic\tDISJOINT_PATTERN", "size\t4", "elapsed_ms\t1.0", "generated\t1",
                "expanded\t1", "solution_length\t1", "goal\t1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0",
                "step\tmove\tstate", "0\tSTART\t1 2 3 4 5 6 7 8 9 10 11 12 13 14 0 15",
                "1\tRIGHT\t1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0"), StandardCharsets.UTF_8);
        SolutionTrace trace = SolutionTrace.read(traceFile);
        check("visual parser replay", trace.states().size() == 2 && trace.states().get(1).equals(goal4));
        System.out.println("PASS: " + checks + " N-Puzzle stage 3 checks");
    }

    private static Map<PuzzleBoard, Integer> bfs(PuzzleBoard goal, int maxDepth) {
        Map<PuzzleBoard, Integer> distance = new HashMap<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        distance.put(goal, 0);
        queue.add(goal);
        while (!queue.isEmpty()) {
            PuzzleBoard state = queue.remove();
            int d = distance.get(state);
            if (d == maxDepth) continue;
            for (PuzzleMove move : state.actions()) {
                PuzzleBoard next = state.next(move);
                if (distance.putIfAbsent(next, d + 1) == null) queue.add(next);
            }
        }
        return distance;
    }

    private static void check(String label, boolean valid) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
}
