package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.time.Instant;

/** Independent first-stage comparison using the teacher's BestFirstSearcher. */
public final class Stage1Benchmark {
    private static final List<String> raw = new ArrayList<>();
    private static String runId;
    private record Result(String algorithm, String heuristic, int problem, int size,
                          int length, int cost, double seconds, int generated, int expanded) {
        String csv() {
            return String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%.6f,%d,%d",
                    algorithm, heuristic, problem, size, length, cost, seconds, generated, expanded);
        }
    }

    public static void main(String[] args) throws IOException {
        raw.clear();
        runId = Instant.now().toString().replace(':','-');
        Path input = Path.of(args.length > 0 ? args[0] : "resources/problems.txt");
        int maxSize = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        Path output = Path.of(args.length > 3 ? args[3] : "results/stage1-"+runId+".csv");
        if(Files.exists(output)) throw new IOException("Use a fresh output path; refusing to replace historical data");
        if (maxSize < 1 || repetitions < 1) throw new IllegalArgumentException("Positive maxSize and repetitions required");

        PuzzleFeeder feeder = new PuzzleFeeder();
        ArrayList<Problem> problems = feeder.getProblems(new ArrayList<>(Files.readAllLines(input)));
        List<Result> results = new ArrayList<>();
        System.out.println("Wall seconds: median of " + repetitions + " runs per configuration; max board size " + maxSize);
        System.out.printf("%-12s %-10s %7s %6s %7s %7s %10s %11s %11s%n",
                "algorithm", "heuristic", "problem", "size", "length", "cost", "wall(s)", "generated", "expanded");
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            int size = ((PuzzleBoard) problem.root().getState()).getSize();
            if (size > maxSize) {
                System.out.println("Skipped problem " + (i + 1) + " (size " + size + " exceeds maxSize)");
                continue;
            }
            Result dijkstra = measure(feeder, problem, i + 1, size, "Dijkstra", "ZERO", repetitions);
            Result greedyM = measure(feeder, problem, i + 1, size, "Greedy", "MISPLACED", repetitions);
            Result greedyD = measure(feeder, problem, i + 1, size, "Greedy", "MANHATTAN", repetitions);
            Result aStarM = measure(feeder, problem, i + 1, size, "AStar", "MISPLACED", repetitions);
            Result aStarD = measure(feeder, problem, i + 1, size, "AStar", "MANHATTAN", repetitions);
            if (dijkstra.length != aStarM.length || dijkstra.length != aStarD.length) {
                throw new AssertionError("Dijkstra and A* disagree on problem " + (i + 1));
            }
            results.addAll(List.of(dijkstra, greedyM, greedyD, aStarM, aStarD));
            for (Result result : List.of(dijkstra, greedyM, greedyD, aStarM, aStarD)) {
                System.out.printf(Locale.ROOT, "%-12s %-10s %7d %6d %7d %7d %10.6f %11d %11d%n",
                        result.algorithm, result.heuristic, result.problem, result.size,
                        result.length, result.cost, result.seconds, result.generated, result.expanded);
            }
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        List<String> csv = new ArrayList<>();
        csv.add("algorithm,heuristic,problem,size,solution_length,path_cost,elapsed_wall_seconds,nodes_generated,nodes_expanded");
        for (Result result : results) csv.add(result.csv());
        Files.write(output, csv, StandardCharsets.UTF_8);
        raw.add(0,"run_id,repetition,"+csv.get(0));
        Files.write(output.resolveSibling(output.getFileName()+".raw.csv"),raw,StandardCharsets.UTF_8);
        System.out.println("CSV: " + output);
    }

    private static Result measure(PuzzleFeeder feeder, Problem problem, int index, int size,
                                  String algorithm, String heuristic, int repetitions) {
        List<Result> runs = new ArrayList<>();
        for (int run = 0; run < repetitions; run++) {
            AbstractSearcher searcher;
            if (algorithm.equals("Dijkstra")) {
                searcher = feeder.getDijkstra();
            } else {
                HeuristicType type = HeuristicType.valueOf(heuristic);
                searcher = algorithm.equals("AStar") ? feeder.getAStar(type)
                        : new BestFirstSearcher(feeder.getFrontier(EvaluationType.HEURISTIC),
                                feeder.getPredictor(type));
            }
            long start = System.nanoTime();
            Deque<Node> path = searcher.search(problem);
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            if (path == null) throw new AssertionError("No solution for problem " + index);
            int cost = path.isEmpty() ? 0 : path.getLast().getPathCost();
            Result result = new Result(algorithm, heuristic, index, size, path.size(), cost,
                    seconds, searcher.nodesGenerated(), searcher.nodesExpanded());
            runs.add(result);
            raw.add(runId+","+(run+1)+","+result.csv());
        }
        runs.sort(Comparator.comparingDouble(Result::seconds));
        Result median = runs.get(runs.size() / 2);
        if(runs.size()%2==0) return new Result(median.algorithm,median.heuristic,median.problem,median.size,
                median.length,median.cost,(runs.get(runs.size()/2-1).seconds+median.seconds)/2,median.generated,median.expanded);
        return median;
    }
}
