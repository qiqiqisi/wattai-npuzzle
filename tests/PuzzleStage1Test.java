import core.problem.Action;
import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.problem.npuzzle.PuzzleMove.Direction;
import stud.g01.queue.PqFrontier;
import stud.g01.runner.PuzzleFeeder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Standalone first-stage checks; run with Java 19. */
public final class PuzzleStage1Test {
    private static int checks;
    private static final PuzzleFeeder FEEDER = new PuzzleFeeder();
    private static final PuzzleBoard GOAL = board(3, 1, 2, 3, 4, 5, 6, 7, 8, 0);

    public static void main(String[] args) throws Exception {
        heuristics();
        frontier();
        feederAndProblem();
        search();
        System.out.println("PASS: " + checks + " N-Puzzle stage 1 checks");
    }

    private static void heuristics() {
        PuzzleBoard sample = board(3, 1, 2, 3, 4, 8, 5, 7, 6, 0);
        check("misplaced example", h(HeuristicType.MISPLACED, sample, GOAL) == 3);
        check("Manhattan example", h(HeuristicType.MANHATTAN, sample, GOAL) == 4);
        for (HeuristicType type : List.of(HeuristicType.MISPLACED, HeuristicType.MANHATTAN)) {
            check(type + " goal zero", h(type, GOAL, GOAL) == 0);
            check(type + " arbitrary goal", h(type, GOAL, sample) > 0);
        }
        PuzzleBoard goal4 = board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 0);
        PuzzleBoard one4 = goal4.next(move(Direction.LEFT));
        check("4x4 misplaced", h(HeuristicType.MISPLACED, one4, goal4) == 1);
        check("4x4 Manhattan", h(HeuristicType.MANHATTAN, one4, goal4) == 1);
        check("4x4 arbitrary goal", h(HeuristicType.MANHATTAN, goal4, one4) == 1);
        check("PDB predictor selectable", FEEDER.getPredictor(HeuristicType.DISJOINT_PATTERN) != null);
        expectIllegal("mismatched board sizes", () -> h(HeuristicType.MANHATTAN, GOAL, goal4));

        Random random = new Random(20260930);
        PuzzleBoard state = GOAL;
        for (int i = 0; i < 300; i++) {
            List<PuzzleMove> moves = new ArrayList<>();
            state.actions().forEach(moves::add);
            state = state.next(moves.get(random.nextInt(moves.size())));
            int misplaced = h(HeuristicType.MISPLACED, state, GOAL);
            int manhattan = h(HeuristicType.MANHATTAN, state, GOAL);
            check("random nonnegative and dominance " + i,
                    misplaced >= 0 && manhattan >= 0 && manhattan >= misplaced);
            for (PuzzleMove move : state.actions()) {
                PuzzleBoard next = state.next(move);
                check("random consistency " + i + " " + move,
                        misplaced <= 1 + h(HeuristicType.MISPLACED, next, GOAL)
                                && manhattan <= 1 + h(HeuristicType.MANHATTAN, next, GOAL));
            }
        }
    }

    private static void frontier() {
        PuzzleBoard a = GOAL;
        PuzzleBoard b = GOAL.next(move(Direction.LEFT));
        PuzzleBoard c = GOAL.next(move(Direction.UP));
        for (EvaluationType type : EvaluationType.values()) {
            PqFrontier frontier = new PqFrontier(Node.evaluator(type));
            Node low = node(a, 1, 1);
            Node mid = node(b, 3, 3);
            Node high = node(c, 5, 5);
            check(type + " offer", frontier.offer(high) && frontier.offer(mid) && frontier.offer(low));
            check(type + " priority order", frontier.poll() == low && frontier.poll() == mid
                    && frontier.poll() == high && frontier.isEmpty());

            Node old = node(a, 8, 8);
            Node better = node(a, 2, 2);
            Node worse = node(a, 9, 9);
            check(type + " new", frontier.offer(old) && frontier.size() == 1);
            check(type + " contains state", frontier.contains(node(a, 100, 100)));
            check(type + " replace", frontier.offer(better) && frontier.size() == 1);
            check(type + " discard", !frontier.offer(worse) && frontier.size() == 1);
            check(type + " replacement identity", frontier.poll() == better);
            check(type + " poll sync", !frontier.contains(old) && frontier.isEmpty());
            frontier.offer(old);
            frontier.clear();
            check(type + " clear sync", frontier.isEmpty() && frontier.size() == 0
                    && !frontier.contains(old) && frontier.poll() == null);
        }
        PqFrontier full = new PqFrontier(Node.evaluator(EvaluationType.FULL));
        Node shallow = node(a, 1, 3);
        Node deep = node(b, 3, 1);
        full.offer(deep);
        full.offer(shallow);
        check("FULL tie breaks on g", full.poll() == shallow);
        PqFrontier cost = new PqFrontier(Node.evaluator(EvaluationType.PATH_COST));
        cost.offer(node(a, 1, 100));
        cost.offer(node(b, 2, 0));
        check("PATH_COST ignores h", cost.poll().getState().equals(a));
        PqFrontier greedy = new PqFrontier(Node.evaluator(EvaluationType.HEURISTIC));
        greedy.offer(node(a, 1, 100));
        greedy.offer(node(b, 20, 0));
        check("HEURISTIC ignores g", greedy.poll().getState().equals(b));
    }

    private static void feederAndProblem() throws Exception {
        ArrayList<String> lines = new ArrayList<>(Files.readAllLines(Path.of("resources/problems.txt")));
        ArrayList<Problem> problems = FEEDER.getProblems(lines);
        check("parse problem count", problems.size() == 4);
        check("parse first size and state", problems.get(0).root().getState()
                .equals(board(3, 5, 0, 8, 4, 2, 1, 7, 3, 6)));
        check("parse fourth 4x4", ((PuzzleBoard) problems.get(3).root().getState()).getSize() == 4);
        check("parsed goals", h(HeuristicType.MANHATTAN, GOAL, GOAL) == 0
                && problems.get(0).goal(GOAL) && problems.get(3).solvable());
        check("problems3 ten rows", FEEDER.getProblems(new ArrayList<>(
                Files.readAllLines(Path.of("resources/problems3.txt")))).size() == 10);
        expectIllegal("bad line token count", () -> FEEDER.getProblems(new ArrayList<>(List.of("3 1 2"))));
        expectIllegal("bad line tile", () -> FEEDER.getProblems(new ArrayList<>(List.of("3 X"))));
        NPuzzleProblem problem = new NPuzzleProblem(GOAL, GOAL);
        check("legal applicable", problem.applicable(GOAL, move(Direction.LEFT)));
        check("illegal applicable", !problem.applicable(GOAL, move(Direction.RIGHT)));
        check("step cost", problem.stepCost(GOAL.next(move(Direction.LEFT)), move(Direction.LEFT)) == 1);
        check("child nodes cost", problem.childNodes(problem.root()).stream()
                .allMatch(n -> n.getPathCost() == 1));
    }

    private static void search() {
        NPuzzleProblem solved = new NPuzzleProblem(GOAL, GOAL);
        NPuzzleProblem one = new NPuzzleProblem(GOAL.next(move(Direction.LEFT)), GOAL);
        for (HeuristicType type : List.of(HeuristicType.MISPLACED, HeuristicType.MANHATTAN)) {
            AbstractSearcher searcher = FEEDER.getAStar(type);
            Deque<Node> zeroPath = searcher.search(solved);
            check(type + " already solved", zeroPath != null && zeroPath.isEmpty());
            Deque<Node> onePath = searcher.search(one);
            check(type + " one move", onePath != null && onePath.size() == 1
                    && onePath.getLast().getPathCost() == 1
                    && one.goal(onePath.getLast().getState()));
            check(type + " reuse searcher", searcher.nodesExpanded() == 1);
            PuzzleBoard customGoal = GOAL.next(move(Direction.LEFT));
            Deque<Node> customPath = searcher.search(new NPuzzleProblem(GOAL, customGoal));
            check(type + " arbitrary goal search", validPath(GOAL, customGoal, customPath, 1));
        }
        PuzzleBoard goal4 = board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 0);
        Deque<Node> path4 = FEEDER.getAStar(HeuristicType.MANHATTAN)
                .search(new NPuzzleProblem(goal4.next(move(Direction.UP)), goal4));
        check("4x4 one move A*", validPath(goal4.next(move(Direction.UP)), goal4, path4, 1));
        PuzzleBoard hard = board(3, 8, 6, 7, 2, 5, 4, 3, 0, 1); // known 31-move instance
        NPuzzleProblem difficult = new NPuzzleProblem(hard, GOAL);
        Deque<Node> misplacedPath = FEEDER.getAStar(HeuristicType.MISPLACED).search(difficult);
        Deque<Node> manhattanPath = FEEDER.getAStar(HeuristicType.MANHATTAN).search(difficult);
        check("hard 8-puzzle misplaced optimal", validPath(hard, GOAL, misplacedPath, 31));
        check("hard 8-puzzle Manhattan optimal", validPath(hard, GOAL, manhattanPath, 31));
        check("same optimal length", misplacedPath.size() == manhattanPath.size());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            one.showSolution(FEEDER.getAStar(HeuristicType.MANHATTAN).search(one));
        } finally {
            System.setOut(previous);
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        check("show solution fields", text.contains("Initial state:") && text.contains("Step 1:")
                && text.contains("Goal state:") && text.contains("Total steps: 1"));
    }

    private static boolean validPath(PuzzleBoard start, PuzzleBoard goal, Deque<Node> path, int steps) {
        if (path == null || path.size() != steps) return false;
        PuzzleBoard state = start;
        int cost = 0;
        for (Node node : path) {
            if (!new NPuzzleProblem(state, goal).applicable(state, node.getAction())) return false;
            state = state.next(node.getAction());
            if (!state.equals(node.getState()) || node.getPathCost() != ++cost) return false;
        }
        return state.equals(goal);
    }

    private static int h(HeuristicType type, PuzzleBoard state, PuzzleBoard goal) {
        Predictor predictor = FEEDER.getPredictor(type);
        return predictor.heuristics(state, goal);
    }

    private static PuzzleBoard board(int size, int... tiles) {
        return new PuzzleBoard(tiles, size);
    }

    private static PuzzleMove move(Direction direction) {
        return new PuzzleMove(direction);
    }

    private static Node node(PuzzleBoard state, int g, int h) {
        return new Node(state, null, null, g, h);
    }

    private static void check(String label, boolean result) {
        if (!result) throw new AssertionError(label);
        checks++;
    }

    private static void expectIllegal(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not reject invalid input");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
}
