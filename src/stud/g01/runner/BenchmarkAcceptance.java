package stud.g01.runner;

import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.visual.SolutionTrace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fails closed on status, identity, replay and parent wall time, independently of worker exit. */
public final class BenchmarkAcceptance {
    public static void verify(Path csv, Path input, int index, int length, double budgetMs) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        if (lines.size() != 2) throw new IllegalArgumentException("Expected one independent benchmark row");
        String[] names = lines.get(0).split(",", -1), cells = lines.get(1).split(",", -1);
        if (names.length != cells.length) throw new IllegalArgumentException("CSV columns differ");
        Map<String,String> row = new HashMap<>();
        for (int i=0; i<names.length; i++) row.put(names[i], cells[i]);
        if (!"SOLVED".equals(row.get("status"))) throw new IllegalArgumentException("Benchmark status: " + row.get("status"));
        String expected = input.getFileName().toString().replaceFirst("\\.[^.]+$", "") + "_" + (index+1);
        if (!expected.equals(row.get("problem"))) throw new IllegalArgumentException("Wrong problem identity");
        int[] data = Arrays.stream(Files.readAllLines(input).get(index).trim().split("\\s+")).mapToInt(Integer::parseInt).toArray();
        int size=data[0], count=size*size;
        PuzzleBoard initial=new PuzzleBoard(Arrays.copyOfRange(data,1,1+count),size);
        PuzzleBoard goal=new PuzzleBoard(Arrays.copyOfRange(data,1+count,data.length),size);
        SolutionTrace trace=SolutionTrace.read(Path.of(row.get("solution_file")));
        if (!initial.equals(trace.states().get(0)) || !goal.equals(trace.goal())
                || trace.states().size()!=length+1 || Integer.parseInt(row.get("solution_length"))!=length
                || Integer.parseInt(row.get("path_cost"))!=length
                || !row.get("algorithm").equals(trace.algorithm()) || !row.get("heuristic").equals(trace.heuristic())
                || trace.generated()!=Long.parseLong(row.get("generated")) || trace.expanded()!=Long.parseLong(row.get("expanded")))
            throw new IllegalArgumentException("Input/CSV/trace mismatch");
        Path parent=csv.resolveSibling(row.get("run_id")+"-parent.txt");
        String value=Files.readAllLines(parent).stream().filter(s->s.startsWith("end_to_end_ms=")).findFirst().orElseThrow().split("=",2)[1];
        double ms=Double.parseDouble(value);
        if (!Double.isFinite(ms) || ms<0 || ms>budgetMs) throw new IllegalArgumentException("Parent wall time exceeds budget: "+ms);
        System.out.println("ACCEPTED: SOLVED, " + length + " legal steps, input/goal match, parent_ms=" + ms);
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=5) throw new IllegalArgumentException("Usage: csv input index expected_length parent_budget_ms");
        verify(Path.of(args[0]),Path.of(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]),Double.parseDouble(args[4]));
    }
}
