import stud.g01.pdb.*;
import stud.g01.problem.npuzzle.*;
import java.util.*;
import java.nio.file.*;

/** New partition validation against shallow exact distances and full ranking recomputation. */
public final class PuzzleAlternativePdbTest {
    private static int checks;
    private static void check(boolean b) { if(!b) throw new AssertionError("check "+checks); checks++; }
    public static void main(String[] args) throws Exception {
        PuzzleBoard goal=new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0},4);
        long begin=System.nanoTime();
        DisjointPatternDatabase first=new DisjointPatternDatabase(goal,DisjointPatternDatabase.FIVE_FIVE_FIVE,Path.of("generated/pdb"));
        DisjointPatternDatabase second=new DisjointPatternDatabase(goal,DisjointPatternDatabase.FIVE_FIVE_FIVE_TRANSPOSED,
            Path.of("generated/pdb-alternative-validation"));
        long build=0, load=0; for(PatternTable table:second.tables()) { if(table.loaded()) load+=table.preparationMs(); else build+=table.preparationMs(); }
        System.out.println("ALTERNATIVE_PREPARE_MS="+(System.nanoTime()-begin)/1e6+" BUILD_MS="+build+" LOAD_MS="+load);
        check(second.heuristics(goal,goal)==0);
        Map<PuzzleBoard,Integer> exact=new HashMap<>(); ArrayDeque<PuzzleBoard> queue=new ArrayDeque<>();
        exact.put(goal,0); queue.add(goal);
        while(!queue.isEmpty()) {
            PuzzleBoard state=queue.remove(); int d=exact.get(state);
            int h1=first.heuristics(state,goal), h2=second.heuristics(state,goal);
            check(h1>=0 && h2>=0 && Math.max(h1,h2)<=d);
            for(PuzzleMove move:state.actions()) {
                PuzzleBoard next=state.next(move);
                check(Math.max(h1,h2)<=1+Math.max(first.heuristics(next,goal),second.heuristics(next,goal)));
                if(d<7 && !exact.containsKey(next)) { exact.put(next,d+1); queue.add(next); }
            }
        }
        PuzzleBoard state=goal; Random random=new Random(20261008);
        for(int i=0;i<500;i++) {
            List<PuzzleMove> moves=new ArrayList<>(); state.actions().forEach(moves::add);
            state=state.next(moves.get(random.nextInt(moves.size())));
            for(PatternTable table:second.tables()) {
                int[] tiles=table.tiles(), positions=new int[tiles.length+1]; positions[0]=state.positionOf(0);
                for(int j=0;j<tiles.length;j++) positions[j+1]=state.positionOf(tiles[j]);
                check(table.value(state)==table.valueAt(table.rank(positions)));
            }
            int h=second.heuristics(state,goal);
            for(PuzzleMove move:state.actions()) check(h<=1+second.heuristics(state.next(move),goal));
        }
        System.out.println("PASS: "+checks+" alternative additive partition/max/ranking checks");
    }
}
