import stud.g01.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.heuristic.PuzzleHeuristic;
import java.nio.file.*;
import java.util.*;

/** Fixed-state query microbenchmark; setup and board generation excluded from query timing. */
public class PdbLookupProbe {
    public static void main(String[] args) throws Exception {
        PuzzleBoard goal=new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0},4);
        int[] data=Arrays.stream(Files.readAllLines(Path.of("resources/problems3.txt")).get(9).split(" ")).mapToInt(Integer::parseInt).toArray();
        PuzzleBoard hard=new PuzzleBoard(Arrays.copyOfRange(data,1,17),4);
        System.out.println("Manhattan="+PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN).heuristics(hard,goal)
            +" LC="+PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC).heuristics(hard,goal));
        PuzzleBoard[] states=new PuzzleBoard[10000]; Random random=new Random(20261008); PuzzleBoard state=goal;
        for(int i=0;i<states.length;i++) {
            List<PuzzleMove> moves=new ArrayList<>(); state.actions().forEach(moves::add);
            state=state.next(moves.get(random.nextInt(moves.size()))); state.positionOf(0); states[i]=state;
        }
        System.out.println("pattern,repetition,queries,query_ns_per_board,checksum,hard_h,load_ms,build_ms");
        for(String group:List.of("4443","555")) {
            DisjointPatternDatabase pdb=new DisjointPatternDatabase(goal,group.equals("555")?
                DisjointPatternDatabase.FIVE_FIVE_FIVE:DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE,Path.of("generated/pdb"));
            long load=0,build=0; for(PatternTable table:pdb.tables()) { if(table.loaded()) load+=table.preparationMs(); else build+=table.preparationMs(); }
            for(int rep=0;rep<=3;rep++) {
                long start=System.nanoTime(), sum=0;
                for(int pass=0;pass<10;pass++) for(PuzzleBoard b:states) sum+=pdb.heuristics(b,goal);
                double ns=(System.nanoTime()-start)/100000.0;
                if(rep>0) System.out.printf(Locale.ROOT,"%s,%d,100000,%.3f,%d,%d,%d,%d%n",group,rep,ns,sum,pdb.heuristics(hard,goal),load,build);
            }
        }
    }
}
