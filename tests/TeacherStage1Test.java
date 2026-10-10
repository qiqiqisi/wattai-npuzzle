import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.*;
import stud.g01.runner.PuzzleFeeder;
import java.util.*;

/** Named teacher board regression with an independent breadth-first distance oracle. */
public final class TeacherStage1Test {
    public static void main(String[] args) {
        PuzzleBoard initial=new PuzzleBoard(new int[]{8,6,7,2,5,4,3,0,1},3);
        PuzzleBoard goal=new PuzzleBoard(new int[]{6,4,7,8,5,0,3,2,1},3);
        Map<PuzzleBoard,Integer> distance=new HashMap<>();
        ArrayDeque<PuzzleBoard> queue=new ArrayDeque<>();distance.put(goal,0);queue.add(goal);
        while (!distance.containsKey(initial)) {
            PuzzleBoard state=queue.remove(); int depth=distance.get(state);
            for(PuzzleMove action:state.actions()) {
                PuzzleBoard child=state.next(action);
                if(!distance.containsKey(child)) {distance.put(child,depth+1);queue.add(child);}
            }
        }
        if(distance.get(initial)!=16) throw new AssertionError("Teacher BFS distance");
        for(HeuristicType h:List.of(HeuristicType.MISPLACED,HeuristicType.MANHATTAN)) {
            var searcher=new PuzzleFeeder().getAStar(h);
            var path=searcher.search(new NPuzzleProblem(initial,goal));
            if(path==null || path.size()!=16) throw new AssertionError("A* shortest length");
            PuzzleBoard state=initial; int step=0;
            for(Node n:path) {
                state=state.next(n.getAction());
                if(!state.equals(n.getState()) || n.getPathCost()!=++step) throw new AssertionError("Replay");
            }
            if(!state.equals(goal)) throw new AssertionError("Goal");
        }
        System.out.println("PASS: TeacherStage1Test independent BFS=16; A* Misplaced/Manhattan replay=16");
    }
}
