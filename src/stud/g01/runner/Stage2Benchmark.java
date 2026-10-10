package stud.g01.runner;

import core.problem.Problem;
import stud.g01.heuristic.PuzzleHeuristic;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.time.Instant;

/** Process-isolated experiment runner: an OOM or timeout cannot end the whole suite. */
public final class Stage2Benchmark {
    private static Path rawOutput;
    private static String suiteRun;
    private static final String HEADER = "problem_id,board_size,algorithm,heuristic,optimization_mode,status,solved,"
            + "solution_length,path_cost,elapsed_ms,nodes_generated,nodes_expanded,iterations,initial_bound,"
            + "final_bound,cycle_prunes,transposition_prunes,max_depth,peak_path_states,"
            + "peak_transposition_entries,runs,threshold_sequence";
    private static final List<String[]> CONFIGS = List.of(
            new String[]{"ASTAR", "MANHATTAN"},
            new String[]{"IDA_BASIC", "MANHATTAN"},
            new String[]{"IDA_TT", "MANHATTAN"},
            new String[]{"IDA_ZOBRIST", "MANHATTAN"},
            new String[]{"IDA_ZOBRIST", "MISPLACED"},
            new String[]{"IDA_ZOBRIST", "MANHATTAN_LC"});

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--worker")) {
            worker(args);
            return;
        }
        suiteRun = Instant.now().toString().replace(':','-');
        Path input = Path.of(args.length > 0 ? args[0] : "resources/problems_stage2_15puzzle.txt");
        Path output = Path.of(args.length > 1 ? args[1] : "results/stage2-"+suiteRun+".csv");
        rawOutput = output.resolveSibling(output.getFileName()+".raw.csv");
        int maxProblem = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
        long strongTimeout = args.length > 3 ? Long.parseLong(args[3]) : 120;
        long weakTimeout = args.length > 4 ? Long.parseLong(args[4]) : 15;
        boolean retryManhattanTimeouts = args.length > 5 && Boolean.parseBoolean(args[5]);
        String onlyAlgorithm = args.length > 6 ? args[6] : "ALL";
        String onlyProblem = args.length > 7 ? args[7] : "ALL";
        String onlyHeuristic = args.length > 8 ? args[8] : "ALL";
        if (maxProblem < 1 || strongTimeout < 1 || weakTimeout < 1)
            throw new IllegalArgumentException("Positive problem count and timeouts required");
        PuzzleFeeder feeder = new PuzzleFeeder();
        List<Problem> problems = feeder.getProblems(new ArrayList<>(Files.readAllLines(input, StandardCharsets.UTF_8)));
        List<String> rows = new ArrayList<>();
        rows.add(HEADER);
        if (Files.exists(output)) {
            List<String> previous = Files.readAllLines(output, StandardCharsets.UTF_8);
            if (!previous.isEmpty() && previous.get(0).equals(HEADER))
                rows.addAll(previous.subList(1, previous.size()));
            else throw new IOException("Refusing to overwrite incompatible historical CSV: "+output);
        }
        for (int index = 0; index < Math.min(maxProblem, problems.size()); index++) {
            Problem problem = problems.get(index);
            int size = ((PuzzleBoard) problem.root().getState()).getSize();
            String id = (size == 4 ? "15p_" + (index + 4) : "8p_" + (index + 1));
            if (!onlyProblem.equals("ALL") && !onlyProblem.equals(id)) continue;
            for (String[] configuration : CONFIGS) {
                String algorithm = configuration[0];
                String heuristic = configuration[1];
                if (!onlyAlgorithm.equals("ALL") && !onlyAlgorithm.equals(algorithm)) continue;
                if (!onlyHeuristic.equals("ALL") && !onlyHeuristic.equals(heuristic)) continue;
                String mode = algorithm.equals("ASTAR") ? "NONE" : algorithm.substring(4);
                String displayAlgorithm = algorithm.equals("ASTAR") ? "AStar" : "IDAStar";
                int previousIndex = -1;
                for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                    String[] cells = rows.get(rowIndex).split(",", -1);
                    if (cells[0].equals(id) && cells[2].equals(displayAlgorithm)
                            && cells[3].equals(heuristic) && cells[4].equals(mode)) {
                        previousIndex = rowIndex;
                        break;
                    }
                }
                if (previousIndex >= 0) {
                    String previousStatus = rows.get(previousIndex).split(",", -1)[5];
                    if (!(retryManhattanTimeouts && heuristic.equals("MANHATTAN")
                            && previousStatus.equals("TIMEOUT"))) continue;
                    // Keep the failed historical observation; append a separately identified attempt below.
                }
                long timeout = heuristic.equals("MISPLACED") ? weakTimeout : strongTimeout;
                String row = measure(input, index, id, size, algorithm, heuristic, timeout);
                rows.add(row);
                Files.createDirectories(output.toAbsolutePath().getParent());
                Files.write(output, rows, StandardCharsets.UTF_8);
                String[] cells = row.split(",", -1);
                System.out.printf(Locale.ROOT, "%s %-12s %-9s %-8s length=%s ms=%s runs=%s%n",
                        id, algorithm, heuristic, cells[5], cells[7], cells[9], cells[20]);
            }
        }
        System.out.println("CSV: " + output);
    }

    private static String measure(Path input, int index, String id, int size, String algorithm,
                                  String heuristic, long timeoutSeconds) throws Exception {
        String first = recordedRun(input, index, id, size, algorithm, heuristic, timeoutSeconds, 0);
        String[] cells = first.split(",", -1);
        if (!cells[5].equals("SOLVED")) return first;
        double elapsed = Double.parseDouble(cells[9]);
        if (elapsed > 2000) return first; // Long runs are reported once; quick runs get a warm-up.
        List<String[]> official = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            String[] result = recordedRun(input, index, id, size, algorithm, heuristic, timeoutSeconds, run+1)
                    .split(",", -1);
            if (!result[5].equals("SOLVED")) return String.join(",", result);
            official.add(result);
        }
        official.sort(Comparator.comparingDouble(result -> Double.parseDouble(result[9])));
        String[] median = official.get(1);
        median[20] = "3";
        return String.join(",", median);
    }

    private static String recordedRun(Path input, int index, String id, int size, String algorithm,
                                     String heuristic, long timeout, int repetition) throws Exception {
        String row = runOnce(input,index,id,size,algorithm,heuristic,timeout);
        Files.createDirectories(rawOutput.toAbsolutePath().getParent());
        if(!Files.exists(rawOutput)) Files.writeString(rawOutput,"run_id,repetition,timeout_s,"+HEADER+"\n",StandardCharsets.UTF_8);
        Files.writeString(rawOutput,suiteRun+","+repetition+","+timeout+","+row+"\n",StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        return row;
    }

    private static String runOnce(Path input, int index, String id, int size, String algorithm,
                                  String heuristic, long timeoutSeconds) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-Xmx1024m", "-cp",
                System.getProperty("java.class.path"), Stage2Benchmark.class.getName(),
                "--worker", input.toString(), Integer.toString(index), id, algorithm, heuristic);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            return emptyRow(id, size, algorithm, heuristic, "TIMEOUT");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (output.startsWith("SOLVED,")) return output.substring("SOLVED,".length());
        if (output.startsWith("UNSOLVABLE")) return emptyRow(id, size, algorithm, heuristic, "UNSOLVABLE");
        if (output.contains("OutOfMemoryError") || output.startsWith("OOM"))
            return emptyRow(id, size, algorithm, heuristic, "OOM");
        System.err.println("Worker error for " + id + " " + algorithm + ": " + output);
        return emptyRow(id, size, algorithm, heuristic, "ERROR");
    }

    private static String emptyRow(String id, int size, String algorithm, String heuristic, String status) {
        String[] cells = new String[22];
        Arrays.fill(cells, "");
        cells[0] = id;
        cells[1] = Integer.toString(size);
        cells[2] = algorithm.equals("ASTAR") ? "AStar" : "IDAStar";
        cells[3] = heuristic;
        cells[4] = algorithm.equals("ASTAR") ? "NONE" : algorithm.substring(4);
        cells[5] = status;
        cells[6] = "false";
        cells[20] = "0";
        return String.join(",", cells);
    }

    private static void worker(String[] args) throws IOException {
        Path input = Path.of(args[1]);
        int index = Integer.parseInt(args[2]);
        String id = args[3];
        String algorithm = args[4];
        String heuristic = args[5];
        PuzzleFeeder feeder = new PuzzleFeeder();
        List<Problem> problems = feeder.getProblems(new ArrayList<>(Files.readAllLines(input, StandardCharsets.UTF_8)));
        Problem problem = problems.get(index);
        int size = ((PuzzleBoard) problem.root().getState()).getSize();
        PuzzleHeuristic type = PuzzleHeuristic.valueOf(heuristic);
        AbstractSearcher searcher = algorithm.equals("ASTAR") ? feeder.getAStar(type)
                : new IdAStar(new StackFrontier(), feeder.getPredictor(type),
                IdAStar.Mode.valueOf(algorithm.substring(4)));
        try {
            long begin = System.nanoTime();
            Deque<Node> path = searcher.search(problem);
            double elapsed = (System.nanoTime() - begin) / 1_000_000.0;
            if (path == null) {
                System.out.println(problem.solvable() ? "ERROR" : "UNSOLVABLE");
                return;
            }
            IdAStar ida = searcher instanceof IdAStar found ? found : null;
            String[] cells = new String[22];
            Arrays.fill(cells, "");
            cells[0] = id;
            cells[1] = Integer.toString(size);
            cells[2] = algorithm.equals("ASTAR") ? "AStar" : "IDAStar";
            cells[3] = heuristic;
            cells[4] = ida == null ? "NONE" : ida.mode().toString();
            cells[5] = "SOLVED";
            cells[6] = "true";
            cells[7] = Integer.toString(path.size());
            cells[8] = Integer.toString(path.isEmpty() ? 0 : path.getLast().getPathCost());
            cells[9] = String.format(Locale.ROOT, "%.3f", elapsed);
            cells[10] = Integer.toString(searcher.nodesGenerated());
            cells[11] = Integer.toString(searcher.nodesExpanded());
            if (ida != null) {
                cells[12] = Integer.toString(ida.iterations());
                cells[13] = Long.toString(ida.initialBound());
                cells[14] = Long.toString(ida.finalBound());
                cells[15] = Long.toString(ida.cyclePrunes());
                cells[16] = Long.toString(ida.transpositionPrunes());
                cells[17] = Integer.toString(ida.maxDepth());
                cells[18] = Integer.toString(ida.peakPathStates());
                cells[19] = Integer.toString(ida.peakTranspositionEntries());
                cells[21] = ida.thresholdSequence().toString().replace(", ", "|");
            }
            cells[20] = "1";
            System.out.println("SOLVED," + String.join(",", cells));
        } catch (OutOfMemoryError exhausted) {
            System.out.println("OOM");
        }
    }
}
