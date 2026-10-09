package stud.g01.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Recomputes case medians and cross-case mean/median/P95 from all saved raw runs. */
public final class Stage1Summary {
    private static final String HEADER = "row_type,run_id,problem_id,heuristic,solution_length,path_cost,elapsed_ms,nodes_generated,nodes_expanded,mean_elapsed_ms,median_elapsed_ms,p95_elapsed_ms,mean_nodes_generated,median_nodes_generated,p95_nodes_generated,mean_nodes_expanded,median_nodes_expanded,p95_nodes_expanded";

    public static void main(String[] args) throws IOException {
        write(Path.of(args.length > 0 ? args[0] : "results/stage1_random_benchmark.csv"),
                Path.of(args.length > 1 ? args[1] : "results/stage1_random_summary.csv"));
    }

    public static void write(Path raw, Path output) throws IOException {
        List<String> lines = Files.readAllLines(raw, StandardCharsets.UTF_8);
        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] fields = lines.get(i).split(",", -1);
            if (fields.length < 13 || !fields[12].equals("SOLVED")) throw new IOException("Invalid raw row " + i);
            groups.computeIfAbsent(fields[0]+"|"+fields[1]+"|"+fields[4], key -> new ArrayList<>()).add(fields);
        }
        List<String[]> cases = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        summary.add(HEADER);
        for (List<String[]> values : groups.values()) {
            if (values.size() != 3) throw new IOException("Expected three formal runs per case");
            values.sort(Comparator.comparingDouble(row -> Double.parseDouble(row[9])));
            String[] median = values.get(1);
            cases.add(median);
            summary.add(String.format(Locale.ROOT,"CASE,%s,%s,%s,%s,%s,%s,%s,%s,,,,,,,,,",
                    median[0], median[1], median[4], median[7], median[8], median[9], median[10], median[11]));
        }
        Map<String, List<String[]>> byHeuristic = new LinkedHashMap<>();
        for (String[] row : cases) byHeuristic.computeIfAbsent(row[0]+"|"+row[4], key -> new ArrayList<>()).add(row);
        for (List<String[]> rows : byHeuristic.values()) {
            String[] example = rows.get(0);
            double[] time = sorted(rows, 9), generated = sorted(rows, 10), expanded = sorted(rows, 11);
            summary.add(String.format(Locale.ROOT,"AGGREGATE,%s,ALL,%s,,,,,,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    example[0], example[4], mean(time), median(time), p95(time),
                    mean(generated), median(generated), p95(generated),
                    mean(expanded), median(expanded), p95(expanded)));
        }
        Files.write(output, summary, StandardCharsets.UTF_8);
        System.out.println("CASE_ROWS="+cases.size()+" AGGREGATE_ROWS="+byHeuristic.size()+" OUTPUT="+output);
    }

    private static double[] sorted(List<String[]> rows, int column) {
        double[] values = rows.stream().mapToDouble(row -> Double.parseDouble(row[column])).toArray();
        Arrays.sort(values);
        return values;
    }
    private static double mean(double[] values) { return Arrays.stream(values).average().orElseThrow(); }
    private static double median(double[] values) {
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2
                : values[middle];
    }
    private static double p95(double[] values) { return values[(int)Math.ceil(values.length*0.95)-1]; }
}
