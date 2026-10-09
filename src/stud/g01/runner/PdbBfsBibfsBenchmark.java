package stud.g01.runner;

import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Unit-cost abstract-distance queries. BiBFS here does not construct a complete PDB. */
public final class PdbBfsBibfsBenchmark {
    private record Result(int distance, int examined, int peak, double ms) {}

    public static void main(String[] args) throws IOException {
        PuzzleBoard goal = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,0}, 3);
        PatternTable table = new PatternTable(goal, new int[]{1,2,3,4},
                Path.of("generated", "pdb", "query-unit-abstract-additive.pdb"));
        int target = table.rank(new int[]{8,0,1,2,3});
        long start = System.nanoTime();
        int[] fullDistances = fullBfs(table, target);
        double fullMs = (System.nanoTime() - start) / 1_000_000.0;
        String runId = Instant.now().toString().replace(':', '-');
        List<String> rows = new ArrayList<>();
        rows.add(String.format(Locale.ROOT, "%s,1-2-3-4,BFS,full-unit-cost-table,%d,%.6f,%d,ALL_REACHABLE,SOLVED",
                runId, table.states(), fullMs, table.states()));
        Random random = new Random(20261002);
        for (int i = 0; i < 20; i++) {
            int source = random.nextInt(table.states());
            Result bfs = queryBfs(table, source, target);
            Result bi = queryBiBfs(table, source, target);
            boolean valid = bfs.distance == bi.distance && bfs.distance == fullDistances[source];
            if (!valid) throw new AssertionError("BFS and BiBFS distance disagreement");
            rows.add(row(runId, "BFS", i, bfs, valid));
            rows.add(row(runId, "BiBFS", i, bi, valid));
        }
        Path output = Path.of("results", "pdb_bfs_bibfs_benchmark.csv");
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (!Files.exists(output)) Files.writeString(output,
                "run_id,pattern,method,workload,states_examined,elapsed_ms,memory_or_peak_entries,distance_validation,status\n",
                StandardCharsets.UTF_8);
        Files.write(output, rows, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        System.out.println("run_id=" + runId + " validated_queries=20 full_states=" + table.states());
    }

    private static String row(String run, String method, int query, Result result, boolean valid) {
        return String.format(Locale.ROOT, "%s,1-2-3-4,%s,query_%d,%d,%.6f,%d,%s,SOLVED",
                run, method, query, result.examined, result.ms, result.peak,
                valid ? "MATCH_DISTANCE_" + result.distance : "MISMATCH");
    }

    private static int[] fullBfs(PatternTable table, int goal) {
        int[] d = new int[table.states()];
        Arrays.fill(d, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(goal);
        d[goal] = 0;
        while (!queue.isEmpty()) {
            int state = queue.remove();
            for (int edge : table.neighbors(state)) {
                int next = edge >>> 1;
                if (d[next] < 0) { d[next] = d[state] + 1; queue.add(next); }
            }
        }
        for (int value : d) if (value < 0) throw new AssertionError("Incomplete unit-cost table");
        return d;
    }

    private static Result queryBfs(PatternTable table, int source, int target) {
        long start = System.nanoTime();
        int[] d = new int[table.states()];
        Arrays.fill(d, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        d[source] = 0;
        int examined = 0;
        int seen = 1;
        while (!queue.isEmpty()) {
            int state = queue.remove();
            examined++;
            if (state == target) return new Result(d[state], examined, seen, (System.nanoTime()-start)/1e6);
            for (int edge : table.neighbors(state)) {
                int next = edge >>> 1;
                if (d[next] < 0) { d[next] = d[state] + 1; queue.add(next); seen++; }
            }
        }
        throw new AssertionError("Unreachable abstract target");
    }

    private static Result queryBiBfs(PatternTable table, int source, int target) {
        long start = System.nanoTime();
        if (source == target) return new Result(0, 0, 1, (System.nanoTime()-start)/1e6);
        int[][] d = {new int[table.states()], new int[table.states()]};
        Arrays.fill(d[0], -1);
        Arrays.fill(d[1], -1);
        List<ArrayDeque<Integer>> queues = List.of(new ArrayDeque<>(), new ArrayDeque<>());
        d[0][source] = 0;
        d[1][target] = 0;
        queues.get(0).add(source);
        queues.get(1).add(target);
        int seen = 2;
        int examined = 0;
        while (!queues.get(0).isEmpty() && !queues.get(1).isEmpty()) {
            int side = queues.get(0).size() <= queues.get(1).size() ? 0 : 1;
            int layer = queues.get(side).size();
            while (layer-- > 0) {
                int state = queues.get(side).remove();
                examined++;
                for (int edge : table.neighbors(state)) {
                    int next = edge >>> 1;
                    if (d[side][next] >= 0) continue;
                    d[side][next] = d[side][state] + 1;
                    seen++;
                    if (d[1-side][next] >= 0)
                        return new Result(d[side][next] + d[1-side][next], examined, seen,
                                (System.nanoTime()-start)/1e6);
                    queues.get(side).add(next);
                }
            }
        }
        throw new AssertionError("Unreachable abstract target");
    }
}
