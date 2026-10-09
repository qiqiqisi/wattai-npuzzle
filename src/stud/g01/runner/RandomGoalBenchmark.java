package stud.g01.runner;

import core.solver.algorithm.heuristic.HeuristicType;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Teacher-suggested random initial/goal pairs, with both boards stored in each raw row. */
public final class RandomGoalBenchmark {
    private static final long SEED = 20261002L;
    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        if (count < 1) throw new IllegalArgumentException("Positive case count required");
        Path raw = Path.of("results", "stage1_random_pairs_benchmark.csv");
        String run = Instant.now().toString().replace(':', '-');
        Random random = new Random(SEED);
        Set<String> seen = new HashSet<>();
        List<String> rows = new ArrayList<>();
        for (int caseIndex = 0; caseIndex < count;) {
            int[] shuffled = {0,1,2,3,4,5,6,7,8};
            for (int i = shuffled.length-1; i > 0; i--) {
                int j=random.nextInt(i+1), temp=shuffled[i]; shuffled[i]=shuffled[j]; shuffled[j]=temp;
            }
            PuzzleBoard goal = new PuzzleBoard(shuffled, 3);
            PuzzleBoard initial = goal, previous = null;
            for (int step=0;step<22;step++) {
                List<PuzzleBoard> options = new ArrayList<>();
                for (PuzzleMove move:initial.actions()) {
                    PuzzleBoard next=initial.next(move);
                    if (!next.equals(previous)) options.add(next);
                }
                previous=initial;
                initial=options.get(random.nextInt(options.size()));
            }
            String identity=encode(initial)+"|"+encode(goal);
            if (initial.equals(goal) || !seen.add(identity)) continue;
            if (!new NPuzzleProblem(initial,goal).solvable()) throw new AssertionError("Generated unsolvable pair");
            caseIndex++;
            for (HeuristicType type:List.of(HeuristicType.MISPLACED,HeuristicType.MANHATTAN)) {
                Stage1ExtendedBenchmark.measure(initial,goal,type,"PqFrontier");
                for(int repetition=1;repetition<=3;repetition++) {
                    Stage1ExtendedBenchmark.Observation result =
                            Stage1ExtendedBenchmark.measure(initial,goal,type,"PqFrontier");
                    rows.add(String.format(Locale.ROOT,
                            "%s,pair_%d,%d,AStar,%s,PqFrontier,%d,%d,%d,%.6f,%d,%d,SOLVED,%s,%s",
                            run,caseIndex,SEED,type,repetition,result.length(),result.cost(),result.ms(),
                            result.generated(),result.expanded(),encode(initial),encode(goal)));
                }
            }
            if(caseIndex%10==0 || caseIndex==count) System.out.println("random pairs completed "+caseIndex+"/"+count);
        }
        Files.createDirectories(raw.toAbsolutePath().getParent());
        if(!Files.exists(raw)) Files.writeString(raw,
                "run_id,problem_id,seed,algorithm,heuristic,frontier,repetition,solution_length,path_cost,elapsed_ms,nodes_generated,nodes_expanded,status,initial,goal\n",
                StandardCharsets.UTF_8);
        Files.write(raw,rows,StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
        Stage1Summary.write(raw,Path.of("results","stage1_random_pairs_summary.csv"));
        System.out.println("run_id="+run);
    }

    private static String encode(PuzzleBoard board) {
        List<String> tiles=new ArrayList<>();
        for(int row=0;row<3;row++) for(int col=0;col<3;col++)
            tiles.add(Integer.toString(board.tileAt(row,col)));
        return String.join("-",tiles);
    }
}
