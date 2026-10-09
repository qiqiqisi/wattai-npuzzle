package stud.g01.runner;

import core.problem.Problem;
import core.solver.queue.Node;
import stud.g01.pdb.DisjointPatternDatabase;
import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Measures table cost and heuristic strength on the same deterministic states and search case. */
public final class PatternGroupingBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || (!args[0].equals("4443") && !args[0].equals("555")))
            throw new IllegalArgumentException("Usage: 4443|555");
        String grouping = args[0];
        List<Problem> problems = new PuzzleFeeder().getProblems(new ArrayList<>(
                Files.readAllLines(Path.of("resources", "problems3.txt"), StandardCharsets.UTF_8)));
        Problem problem = problems.get(3);
        PuzzleBoard start = (PuzzleBoard) problem.root().getState();
        PuzzleBoard goal = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0}, 4);
        long before = System.nanoTime();
        DisjointPatternDatabase pdb = new DisjointPatternDatabase(goal,
                grouping.equals("555") ? DisjointPatternDatabase.FIVE_FIVE_FIVE
                        : DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE, Path.of("generated", "pdb"));
        double prepare = (System.nanoTime() - before) / 1e6;
        long entries=0, bytes=0, build=0, load=0;
        for (PatternTable table:pdb.tables()) {
            entries += table.states(); bytes += table.fileBytes();
            if (table.loaded()) load += table.preparationMs(); else build += table.preparationMs();
        }
        Random random = new Random(20261002);
        double totalH = 0;
        PuzzleBoard board = goal;
        for (int i=0;i<100;i++) {
            for (int step=0;step<30;step++) {
                List<PuzzleMove> moves=new ArrayList<>(); board.actions().forEach(moves::add);
                board=board.next(moves.get(random.nextInt(moves.size())));
            }
            totalH += pdb.heuristics(board,goal);
        }
        IdAStar searcher = new IdAStar(new StackFrontier(), pdb, IdAStar.Mode.ZOBRIST);
        before = System.nanoTime();
        Deque<Node> path = searcher.search(problem);
        double searchMs = (System.nanoTime()-before)/1e6;
        if (path==null || !path.getLast().getState().equals(goal)) throw new AssertionError("Grouping search failed");
        String row=String.format(Locale.ROOT,"%s,%s,%.3f,%d,%d,%d,1,%d,%d,%.3f,%d,%.3f,%d,%d,SOLVED",
                Instant.now().toString().replace(':','-'),grouping,prepare,build,load,entries,entries,bytes,
                totalH/100, path.size(),searchMs,searcher.nodesGenerated(),searcher.nodesExpanded());
        Path csv=Path.of("results","pdb_grouping_benchmark.csv");
        if(!Files.exists(csv)) Files.writeString(csv,
                "run_id,pattern,prepare_ms,build_ms,load_ms,entries,bytes_per_entry,estimated_table_bytes,file_bytes,average_heuristic_100_walks,solution_length,search_ms,generated,expanded,status\n",
                StandardCharsets.UTF_8);
        Files.writeString(csv,row+"\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
        System.out.println(row);
    }
}
