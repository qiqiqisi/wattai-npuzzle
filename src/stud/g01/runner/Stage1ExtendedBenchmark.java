package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.queue.PqFrontier;
import stud.queue.ListFrontier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Fixed-seed 8-puzzle experiments; one warm-up and three measured runs per case. */
public final class Stage1ExtendedBenchmark {
    private static final long SEED = 20261002L;
    private static final String HEADER = "run_id,problem_id,seed,algorithm,heuristic,frontier,repetition,solution_length,path_cost,elapsed_ms,nodes_generated,nodes_expanded,status";
    record Observation(int length, int cost, double ms, int generated, int expanded) {}

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0] : "random";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : mode.equals("frontier") ? 6 : 100;
        if (count < 1 || (!mode.equals("random") && !mode.equals("frontier")))
            throw new IllegalArgumentException("Usage: random|frontier [positive count]");
        Path output = Path.of(mode.equals("random") ? "results/stage1_random_benchmark.csv"
                : "results/frontier_benchmark.csv");
        Path summary = Path.of("results/stage1_random_summary.csv");
        String runId = Instant.now().toString().replace(':', '-');
        PuzzleBoard goal = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,0}, 3);
        List<PuzzleBoard> cases = generate(goal, count, mode.equals("frontier") ? 10 : 22);
        List<String> rows = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            PuzzleBoard initial = cases.get(index);
            for (HeuristicType type : List.of(HeuristicType.MISPLACED, HeuristicType.MANHATTAN)) {
                for (String frontier : mode.equals("frontier") ? List.of("ListFrontier", "PqFrontier")
                        : List.of("PqFrontier")) {
                    String problemId = mode + "_" + (index + 1);
                    measure(initial, goal, type, frontier); // warm-up
                    List<Observation> observed = new ArrayList<>();
                    for (int rep = 1; rep <= 3; rep++) {
                        Observation result = measure(initial, goal, type, frontier);
                        observed.add(result);
                        rows.add(String.format(Locale.ROOT, "%s,%s,%d,AStar,%s,%s,%d,%d,%d,%.6f,%d,%d,SOLVED",
                                runId, problemId, SEED, type, frontier, rep, result.length, result.cost,
                                result.ms, result.generated, result.expanded));
                    }
                    if (mode.equals("random")) {
                        observed.sort(java.util.Comparator.comparingDouble(Observation::ms));
                        Observation median = observed.get(1);
                        summaries.add(String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%.6f,%d,%d",
                                runId, problemId, type, median.length, median.cost, median.ms,
                                median.generated, median.expanded));
                    }
                }
            }
            if ((index + 1) % 10 == 0 || index + 1 == cases.size())
                System.out.println(mode + " completed " + (index + 1) + "/" + cases.size());
        }
        append(output, HEADER, rows);
        if (mode.equals("random")) {
            Stage1Summary.write(output, summary);
            for (HeuristicType type : List.of(HeuristicType.MISPLACED, HeuristicType.MANHATTAN)) {
                for (int metric = 5; metric <= 7; metric++) {
                    int column = metric;
                    double[] values = summaries.stream().map(s -> s.split(","))
                            .filter(s -> s[2].equals(type.toString()))
                            .mapToDouble(s -> Double.parseDouble(s[column])).sorted().toArray();
                    double mean = Arrays.stream(values).average().orElseThrow();
                    double median = values.length % 2 == 0
                            ? (values[values.length / 2 - 1] + values[values.length / 2]) / 2
                            : values[values.length / 2];
                    double p95 = values[(int) Math.ceil(values.length * 0.95) - 1];
                    System.out.printf(Locale.ROOT, "%s %s mean=%.3f median=%.3f p95=%.3f%n",
                            type, metric == 5 ? "ms" : metric == 6 ? "generated" : "expanded", mean, median, p95);
                }
            }
        }
        System.out.println("run_id=" + runId + " output=" + output);
    }

    static Observation measure(PuzzleBoard initial, PuzzleBoard goal, HeuristicType type, String frontierName) {
        Frontier frontier = frontierName.equals("ListFrontier")
                ? new ListFrontier(Node.evaluator(EvaluationType.FULL))
                : new PqFrontier(Node.evaluator(EvaluationType.FULL));
        Predictor predictor = PuzzleBoard.predictor(type);
        BestFirstSearcher searcher = new BestFirstSearcher(frontier, predictor);
        Problem problem = new NPuzzleProblem(initial, goal);
        long start = System.nanoTime();
        Deque<Node> path = searcher.search(problem);
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        if (path == null || !replay(initial, goal, path)) throw new AssertionError("Invalid benchmark path");
        return new Observation(path.size(), path.isEmpty() ? 0 : path.getLast().getPathCost(), ms,
                searcher.nodesGenerated(), searcher.nodesExpanded());
    }

    private static boolean replay(PuzzleBoard initial, PuzzleBoard goal, Deque<Node> path) {
        PuzzleBoard current = initial;
        for (Node node : path) {
            boolean legal = false;
            for (PuzzleMove move : current.actions()) if (move.equals(node.getAction())) legal = true;
            if (!legal) return false;
            current = current.next(node.getAction());
            if (!current.equals(node.getState())) return false;
        }
        return current.equals(goal);
    }

    private static List<PuzzleBoard> generate(PuzzleBoard goal, int count, int depth) {
        Random random = new Random(SEED);
        Set<PuzzleBoard> seen = new HashSet<>();
        List<PuzzleBoard> cases = new ArrayList<>();
        while (cases.size() < count) {
            PuzzleBoard board = goal;
            PuzzleBoard previous = null;
            for (int step = 0; step < depth; step++) {
                List<PuzzleBoard> next = new ArrayList<>();
                for (PuzzleMove move : board.actions()) {
                    PuzzleBoard candidate = board.next(move);
                    if (!candidate.equals(previous)) next.add(candidate);
                }
                previous = board;
                board = next.get(random.nextInt(next.size()));
            }
            if (!board.equals(goal) && seen.add(board) && new NPuzzleProblem(board, goal).solvable()) cases.add(board);
        }
        return cases;
    }

    private static void append(Path path, String header, List<String> rows) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        if (!Files.exists(path)) Files.writeString(path, header + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.write(path, rows, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }
}
