import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.runner.SearchTester;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.runner.PuzzleFeeder;
import stud.g01.solver.IdAStar;
import stud.g01.solver.ZobristKey;
import stud.queue.StackFrontier;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/** Second-stage correctness and optimality checks, independent of the long benchmark. */
public final class PuzzleStage2Test {
    private static int checks;
    private static final PuzzleFeeder FEEDER = new PuzzleFeeder();
    private static final PuzzleBoard GOAL3 = board(3, 1, 2, 3, 4, 5, 6, 7, 8, 0);
    private static final PuzzleBoard GOAL4 = board(4, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 0);

    public static void main(String[] args) throws Exception {
        specialCases();
        thresholdsAndCycles();
        transpositionsAndCollisions();
        optimality();
        zobristIncrement();
        linearConflict();
        searchTesterStep2();
        System.out.println("PASS: " + checks + " N-Puzzle stage 2 checks");
    }

    private static void specialCases() {
        IdAStar solver = ida(HeuristicType.MANHATTAN, IdAStar.Mode.ZOBRIST);
        Deque<Node> empty = solver.search(new NPuzzleProblem(GOAL4, GOAL4));
        check("goal empty path", empty != null && empty.isEmpty() && solver.iterations() == 1);
        PuzzleBoard impossible = board(4, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 14, 0);
        check("unsolvable", solver.search(new NPuzzleProblem(impossible, GOAL4)) == null
                && solver.nodesGenerated() == 0 && solver.iterations() == 0);
        PuzzleBoard one = GOAL4.next(new PuzzleMove(PuzzleMove.Direction.LEFT));
        for (HeuristicType heuristic : List.of(HeuristicType.MANHATTAN, HeuristicType.MISPLACED)) {
            for (IdAStar.Mode mode : IdAStar.Mode.values()) {
                solver = ida(heuristic, mode);
                Deque<Node> path = solver.search(new NPuzzleProblem(one, GOAL4));
                check("one move " + heuristic + mode, path != null && path.size() == 1
                        && path.getLast().getPathCost() == 1 && GOAL4.equals(path.getLast().getState()));
                check("reset " + heuristic + mode, solver.search(new NPuzzleProblem(GOAL4, GOAL4)).isEmpty()
                        && solver.nodesGenerated() == 0 && solver.nodesExpanded() == 0);
            }
        }
    }

    private static void thresholdsAndCycles() {
        // An admissible zero heuristic produces successive bounds 0,1,2,3.
        PuzzleBoard start = GOAL4.next(new PuzzleMove(PuzzleMove.Direction.LEFT))
                .next(new PuzzleMove(PuzzleMove.Direction.UP))
                .next(new PuzzleMove(PuzzleMove.Direction.LEFT));
        IdAStar zero = new IdAStar(new StackFrontier(), (state, goal) -> 0, IdAStar.Mode.BASIC);
        Deque<Node> path = zero.search(new NPuzzleProblem(start, GOAL4));
        check("minimum exceeded f", path != null && path.size() == 3
                && zero.thresholdSequence().equals(List.of(0L, 1L, 2L, 3L)));
        check("cycle prunes", zero.cyclePrunes() > 0 && zero.peakPathStates() > 1);
        IdAStar manhattan = ida(HeuristicType.MANHATTAN, IdAStar.Mode.BASIC);
        manhattan.search(new NPuzzleProblem(start, GOAL4));
        check("initial h", manhattan.initialBound() == FEEDER.getPredictor(HeuristicType.MANHATTAN)
                .heuristics(start, GOAL4));
    }

    private static void transpositionsAndCollisions() {
        GraphProblem graph = new GraphProblem();
        IdAStar tt = new IdAStar(new StackFrontier(), (state, goal) -> 0, IdAStar.Mode.TT);
        Deque<Node> ttPath = tt.search(graph);
        check("TT optimal graph", ttPath != null && ttPath.size() == 4);
        check("TT duplicate pruned", tt.transpositionPrunes() > 0 && tt.peakTranspositionEntries() > 0);
        check("TT resets each iteration", tt.thresholdSequence().equals(List.of(0L, 1L, 2L, 3L, 4L)));
        IdAStar zobrist = new IdAStar(new StackFrontier(), (state, goal) -> 0, IdAStar.Mode.ZOBRIST);
        Deque<Node> collisionPath = zobrist.search(graph);
        check("forced hash collision", collisionPath != null && collisionPath.size() == 4
                && zobrist.peakTranspositionEntries() > 1);
        check("better g still allowed", ttPath.getLast().getPathCost() == collisionPath.getLast().getPathCost());
        IdAStar limited = new IdAStar(new StackFrontier(), (state, goal) -> 0,
                IdAStar.Mode.ZOBRIST, 1);
        check("capped TT remains optimal", limited.search(graph).size() == 4
                && limited.peakTranspositionEntries() == 1);
        IdAStar jump = new IdAStar(new StackFrontier(), (state, goal) ->
                switch (((GraphState) state).name) {
                    case "A" -> 4;
                    case "D", "B", "E" -> 3;
                    case "C" -> 2;
                    case "X" -> 1;
                    default -> 0;
                }, IdAStar.Mode.BASIC);
        check("next bound jumps to minimum f", jump.search(graph).size() == 4
                && jump.thresholdSequence().equals(List.of(0L, 4L)));
    }

    private static void optimality() {
        PuzzleBoard start8 = board(3, 5, 0, 8, 4, 2, 1, 7, 3, 6);
        compareWithAStar(start8, GOAL3);
        PuzzleBoard state = GOAL4;
        Random random = new Random(20260930);
        for (int i = 0; i < 8; i++) {
            List<PuzzleMove> moves = new ArrayList<>();
            state.actions().forEach(moves::add);
            state = state.next(moves.get(random.nextInt(moves.size())));
            compareWithAStar(state, GOAL4);
        }
    }

    private static void compareWithAStar(PuzzleBoard start, PuzzleBoard goal) {
        NPuzzleProblem problem = new NPuzzleProblem(start, goal);
        Deque<Node> oracle = FEEDER.getAStar(HeuristicType.MANHATTAN).search(problem);
        for (IdAStar.Mode mode : IdAStar.Mode.values()) {
            Deque<Node> path = ida(HeuristicType.MANHATTAN, mode).search(problem);
            check("A* optimal agreement " + mode, oracle != null && path != null
                    && path.size() == oracle.size() && validPath(start, goal, path));
        }
    }

    private static void zobristIncrement() {
        PuzzleBoard state = GOAL4;
        Random random = new Random(1930);
        for (int i = 0; i < 200; i++) {
            List<PuzzleMove> moves = new ArrayList<>();
            state.actions().forEach(moves::add);
            state = state.next(moves.get(random.nextInt(moves.size())));
            int[] tiles = new int[16];
            for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++)
                tiles[row * 4 + col] = state.tileAt(row, col);
            PuzzleBoard rebuilt = new PuzzleBoard(tiles, 4);
            check("incremental hash " + i, state.equals(rebuilt)
                    && state.zobristHash() == rebuilt.zobristHash()
                    && state.hashCode() == rebuilt.hashCode());
            for (HeuristicType type : List.of(HeuristicType.MANHATTAN, HeuristicType.MISPLACED)) {
                NPuzzleProblem problem = new NPuzzleProblem(state, GOAL4);
                var predictor = FEEDER.getPredictor(type);
                Node parent = problem.root(predictor);
                for (Node child : problem.childNodes(parent, predictor)) {
                    check("incremental heuristic " + i + type,
                            child.getHeuristic() == predictor.heuristics(child.getState(), GOAL4));
                }
            }
        }
    }

    private static void linearConflict() {
        var manhattan = FEEDER.getPredictor(HeuristicType.MANHATTAN);
        var stronger = FEEDER.getPredictor(HeuristicType.MANHATTAN_LC);
        check("LC goal zero", stronger.heuristics(GOAL3, GOAL3) == 0);
        HashMap<PuzzleBoard, Integer> distance = new HashMap<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        distance.put(GOAL3, 0);
        queue.add(GOAL3);
        while (!queue.isEmpty()) {
            PuzzleBoard state = queue.remove();
            int exact = distance.get(state);
            int h = stronger.heuristics(state, GOAL3);
            check("LC exhaustive 8-puzzle admissibility", h >= manhattan.heuristics(state, GOAL3)
                    && h <= exact);
            for (PuzzleMove action : state.actions()) {
                PuzzleBoard next = state.next(action);
                if (!distance.containsKey(next)) {
                    distance.put(next, exact + 1);
                    queue.add(next);
                }
            }
        }
        check("complete 8-puzzle reachability class", distance.size() == 181440);
        PuzzleBoard start = board(3, 5, 0, 8, 4, 2, 1, 7, 3, 6);
        check("LC IDA optimal 8-puzzle", ida(HeuristicType.MANHATTAN_LC, IdAStar.Mode.ZOBRIST)
                .search(new NPuzzleProblem(start, GOAL3)).size() == 21);
        PuzzleBoard shallow = GOAL4.next(new PuzzleMove(PuzzleMove.Direction.LEFT))
                .next(new PuzzleMove(PuzzleMove.Direction.UP));
        check("LC arbitrary 15 goal", ida(HeuristicType.MANHATTAN_LC, IdAStar.Mode.ZOBRIST)
                .search(new NPuzzleProblem(GOAL4, shallow)).size() == 2);
    }

    private static void searchTesterStep2() throws Exception {
        Path input = Files.createTempFile("wattai-stage2-", ".txt");
        Files.writeString(input, "4 1 2 3 4 5 6 7 8 9 10 11 12 13 14 0 15 "
                + "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0\n", StandardCharsets.UTF_8);
        PrintStream previous = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            SearchTester.main(new String[]{input.toString(), "NPUZZLE", "2",
                    "stud.g01.runner.PuzzleFeeder"});
        } finally {
            System.setOut(previous);
            Files.deleteIfExists(input);
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        check("SearchTester step 2 integration", output.contains("MISPLACED")
                && output.contains("MANHATTAN") && !output.contains("No Solution"));
    }

    private static boolean validPath(PuzzleBoard start, PuzzleBoard goal, Deque<Node> path) {
        PuzzleBoard state = start;
        int cost = 0;
        for (Node node : path) {
            state = state.next(node.getAction());
            if (!state.equals(node.getState()) || node.getPathCost() != ++cost) return false;
        }
        return state.equals(goal);
    }

    private static IdAStar ida(HeuristicType heuristic, IdAStar.Mode mode) {
        return new IdAStar(new StackFrontier(), FEEDER.getPredictor(heuristic), mode);
    }

    private static PuzzleBoard board(int size, int... tiles) { return new PuzzleBoard(tiles, size); }
    private static void check(String label, boolean passed) {
        if (!passed) throw new AssertionError(label);
        checks++;
    }

    private static final class GraphMove extends Action {
        final String destination;
        GraphMove(String destination) { this.destination = destination; }
        public void draw() { System.out.println(destination); }
        public int stepCost() { return 1; }
    }

    private static final class GraphState extends State implements ZobristKey {
        private static final Map<String, List<String>> EDGES = Map.of(
                "S", List.of("A", "B", "E"), "A", List.of("D"), "D", List.of("C"),
                "B", List.of("C"), "E", List.of("C"), "C", List.of("X"),
                "X", List.of("G"), "G", List.of());
        final String name;
        GraphState(String name) { this.name = name; }
        public void draw() { System.out.println(name); }
        public State next(Action action) { return new GraphState(((GraphMove) action).destination); }
        public Iterable<GraphMove> actions() {
            List<GraphMove> moves = new ArrayList<>();
            for (String name : EDGES.get(this.name)) moves.add(new GraphMove(name));
            return moves;
        }
        public long zobristHash() { return 7L; } // Deliberate collision among all different states.
        public boolean equals(Object other) { return other instanceof GraphState state && name.equals(state.name); }
        public int hashCode() { return name.hashCode(); }
    }

    private static final class GraphProblem extends Problem {
        GraphProblem() { super(new GraphState("S"), new GraphState("G")); }
        public boolean solvable() { return true; }
        public int stepCost(State state, Action action) { return 1; }
        public boolean applicable(State state, Action action) { return true; }
        public void showSolution(Deque<Node> path) { }
    }
}
