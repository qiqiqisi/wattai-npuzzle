import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.IdAStar;
import stud.g01.visual.*;
import stud.queue.StackFrontier;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Independent shallow BFS oracle, paired search and playback failure checks. */
public class PuzzleCloseoutTest {
    private static int checks;
    private static void check(boolean value) { if(!value) throw new AssertionError("check "+checks); checks++; }
    public static void main(String[] args) throws Exception {
        for (int size : new int[]{3,4}) {
            int[] tiles=new int[size*size]; for(int i=0;i<tiles.length-1;i++) tiles[i]=i+1;
            PuzzleBoard standard=new PuzzleBoard(tiles,size);
            PuzzleBoard goal=standard.next(new PuzzleMove(PuzzleMove.Direction.UP));
            Map<PuzzleBoard,Integer> exact=new LinkedHashMap<>();
            ArrayDeque<PuzzleBoard> queue=new ArrayDeque<>(); exact.put(goal,0); queue.add(goal);
            while(!queue.isEmpty()) {
                PuzzleBoard state=queue.remove(); int depth=exact.get(state);
                if(depth==6) continue;
                for(PuzzleMove move:state.actions()) {
                    PuzzleBoard child=state.next(move);
                    if(!exact.containsKey(child)) { exact.put(child,depth+1); queue.add(child); }
                }
            }
            int index=0;
            for(var entry:exact.entrySet()) {
                if(index++%5!=0) continue;
                for(IdAStar.Mode mode:IdAStar.Mode.values()) for(int capacity:new int[]{0,1,128}) {
                    System.setProperty("wattai.fast","false");
                    IdAStar reference=new IdAStar(new StackFrontier(),PuzzleBoard.predictor(HeuristicType.MANHATTAN),mode,capacity);
                    var before=reference.search(new NPuzzleProblem(entry.getKey(),goal));
                    System.setProperty("wattai.fast","true");
                    IdAStar optimized=new IdAStar(new StackFrontier(),PuzzleBoard.predictor(HeuristicType.MANHATTAN),mode,capacity);
                    List<Integer> snapshots=new ArrayList<>();
                    optimized.progressListener(s->snapshots.add(s.nodesExpanded()));
                    var after=optimized.search(new NPuzzleProblem(entry.getKey(),goal));
                    check(before.size()==entry.getValue() && after.size()==entry.getValue());
                    check(reference.nodesExpanded()==optimized.nodesExpanded()
                        && reference.thresholdSequence().equals(optimized.thresholdSequence()));
                    PuzzleBoard state=entry.getKey(); int cost=0;
                    for(Node node:after) { state=state.next(node.getAction()); check(state.equals(node.getState()) && node.getPathCost()==++cost); }
                    check(state.equals(goal) && entry.getKey().equals(new PuzzleBoard(encode(entry.getKey()),size)));
                    check(!snapshots.isEmpty() && snapshots.get(snapshots.size()-1)==optimized.nodesExpanded());
                }
            }
            for(int last:new int[]{0,1,31,52}) {
                PlaybackModel model=new PlaybackModel(last);
                model.previous(); check(model.step()==0);
                model.play(); model.pause(); model.tick(); check(model.step()==0);
                model.play(); for(int i=0;i<last+2;i++) model.tick();
                check(model.step()==last && !model.playing());
                model.next(); check(model.step()==last);
                model.play(); check(!model.playing());
                model.restart(); check(model.step()==0 && !model.playing());
                for(int speed=0;speed<3;speed++) { model.speed(speed); check(model.delay()==new int[]{1400,700,350}[speed]); }
            }
        }
        System.clearProperty("wattai.fast");
        Path dir=Path.of("generated/closeout-test"); Files.createDirectories(dir);
        for(int size:new int[]{3,4}) {
            int[] tiles=new int[size*size]; for(int i=0;i<tiles.length-1;i++) tiles[i]=i+1;
            PuzzleBoard goal=new PuzzleBoard(tiles,size), initial=goal.next(new PuzzleMove(PuzzleMove.Direction.LEFT));
            String text="format\tNPUZZLE_SOLUTION_V1\nalgorithm\tIDA\nheuristic\tMANHATTAN\nsize\t"+size
                +"\nelapsed_ms\t0.1\ngenerated\t2\nexpanded\t1\nsolution_length\t1\ngoal\t"+serialize(goal)
                +"\nstep\tmove\tstate\n0\tSTART\t"+serialize(initial)+"\n1\tRIGHT\t"+serialize(goal)+"\n";
            Path file=dir.resolve("valid-"+size+".tsv"); Files.writeString(file,text);
            check(SolutionTrace.read(file).states().size()==2);
            for(String invalid:List.of(text.replace("elapsed_ms\t0.1","elapsed_ms\tNaN"),
                text.replace("generated\t2","generated\t-1"),text.replace("solution_length\t1","solution_length\t2"),
                text.replace("1\tRIGHT","1\tUP"),text.replace("0\tSTART","1\tSTART"),
                text.replace("algorithm\tIDA","algorithm\t"),text.substring(0,text.indexOf("step\tmove")))) {
                Path bad=dir.resolve("invalid-"+size+".tsv"); Files.writeString(bad,invalid);
                boolean rejected=false; try { SolutionTrace.read(bad); } catch(IOException expected) { rejected=true; }
                check(rejected);
            }
        }
        System.out.println("PASS: "+checks+" closeout BFS/search/progress/playback/parser checks");
    }
    private static int[] encode(PuzzleBoard b) { int[] a=new int[b.getSize()*b.getSize()]; for(int i=0;i<a.length;i++) a[i]=b.tileAt(i/b.getSize(),i%b.getSize()); return a; }
    private static String serialize(PuzzleBoard b) { return String.join(" ",Arrays.stream(encode(b)).mapToObj(Integer::toString).toList()); }
}
