import core.problem.*;
import core.solver.queue.Node;
import stud.g01.heuristic.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.runner.PuzzleFeeder;
import stud.g01.solver.*;
import stud.queue.StackFrontier;
import java.nio.file.*;
import java.util.*;

/** Independent packed-board BFS, exhaustive LC edges, and adversarial TT tests. */
public final class PuzzleOptimizationTest {
    private static long checks, edges, states, randomEdges;
    private static Path output;
    private static String context = "startup";
    private static final long SEED = 20261106L;
    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label + " :: " + context);
    }
    public static void main(String[] args) throws Exception {
        output = Path.of(args.length == 0 ? "generated/optimization-test" : args[0]);
        Files.createDirectories(output);
        try {
            for (int[] tiles : List.of(new int[]{1,2,3,4,5,6,7,8,0},
                    new int[]{1,2,3,4,0,5,6,7,8}, new int[]{8,0,7,6,5,4,3,2,1}))
                exhaustive(new PuzzleBoard(tiles,3), 31, true);
            PuzzleBoard standard = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0},4);
            PuzzleBoard arbitrary = walk(standard, new Random(SEED), 21);
            exhaustive(standard, 9, false);
            exhaustive(arbitrary, 9, false);
            random4(standard, arbitrary);
            tableDecisions();
            graphOracles();
            System.out.println("PASS: checks="+checks+" BFS_states="+states+" directed_edges="+edges+" random_edges="+randomEdges);
            Files.writeString(output.resolve("correctness.json"), "{\n  \"status\": \"PASS\",\n  \"seed\": "+SEED+
                ",\n  \"checks\": "+checks+",\n  \"bfs_states\": "+states+",\n  \"directed_edges\": "+edges+
                ",\n  \"random_permutation_edges\": "+randomEdges+",\n  \"full_8puzzle_goals\": 3,\n  \"states_per_8puzzle_goal\": 181440,\n  \"random_4x4_exact_astar_pairs\": 48,\n  \"random_graph_oracles\": 300\n}\n", StandardOpenOption.CREATE_NEW);
        } catch (Throwable failure) {
            Files.writeString(output.resolve("counterexample.txt"), "seed="+SEED+"\n"+context+"\n"+failure,
                    StandardOpenOption.CREATE_NEW);
            throw failure;
        }
    }
    // This oracle does not use PuzzleBoard.next/actions, NPuzzleProblem, A*, or LC.
    private static Map<Long,Integer> bfs(PuzzleBoard goal, int max) {
        Map<Long,Integer> exact = new LinkedHashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long key = pack(goal); exact.put(key,0); queue.add(key);
        while (!queue.isEmpty()) {
            key = queue.remove(); int d = exact.get(key);
            if (d == max) continue;
            for (long child : neighbors(key, goal.getSize())) if (!exact.containsKey(child)) {
                exact.put(child,d+1); queue.add(child);
            }
        }
        return exact;
    }
    private static List<Long> neighbors(long key, int size) {
        int blank = 0; while (((key >>> (blank*4)) & 15) != 0) blank++;
        List<Long> result = new ArrayList<>();
        for (int other=0; other<size*size; other++) {
            if (Math.abs(blank/size-other/size)+Math.abs(blank%size-other%size)!=1) continue;
            long tile = (key >>> (other*4)) & 15;
            result.add(key ^ (tile << (other*4)) ^ (tile << (blank*4)));
        }
        return result;
    }
    private static void exhaustive(PuzzleBoard goal, int depth, boolean full) {
        var exact = bfs(goal,depth);
        var m = PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN);
        var lc = (IncrementalPredictor)PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC);
        context="goal="+text(goal);
        check(lc.heuristics(goal,goal)==0,"h(goal)=0");
        if (full) check(exact.size()==181440,"complete reachability class");
        int sample=0;
        for (var entry : exact.entrySet()) {
            PuzzleBoard state=unpack(entry.getKey(),goal.getSize());
            int h=lc.heuristics(state,goal), md=m.heuristics(state,goal);
            context="goal="+text(goal)+" state="+text(state)+" h="+h+" Manhattan="+md+" exact="+entry.getValue();
            check(md<=h && h<=entry.getValue(),"LC dominance and admissibility");
            Set<Long> actual = new HashSet<>();
            for (PuzzleMove move:state.actions()) actual.add(pack(state.next(move)));
            check(actual.equals(new HashSet<>(neighbors(entry.getKey(),goal.getSize()))),"independent successor set");
            for (long successor : neighbors(entry.getKey(),goal.getSize())) {
                PuzzleBoard child=unpack(successor,goal.getSize());
                int next=lc.heuristics(child,goal);
                String saved=context; context += " child="+text(child)+" child_h="+next;
                check(h<=1+next,"LC consistency on every legal directed edge");
                for (PuzzleHeuristic type:List.of(PuzzleHeuristic.MISPLACED,PuzzleHeuristic.MANHATTAN,PuzzleHeuristic.MANHATTAN_LC)) {
                    var inc=(IncrementalPredictor)PuzzleBoard.predictor(type);
                    check(inc.successorHeuristics(state,child,goal,inc.heuristics(state,goal))==inc.heuristics(child,goal),
                            "incremental equals full: "+type);
                }
                context=saved; edges++;
            }
            if (sample++ % (full ? 9000 : 41)==0) paired(state,goal,entry.getValue());
        }
        states+=exact.size();
        System.out.println("BFS goal="+text(goal)+" states="+exact.size()+" complete="+full+" depth_limit="+depth);
    }
    private static void paired(PuzzleBoard start, PuzzleBoard goal, int distance) {
        for (boolean fast:new boolean[]{false,true}) for (IdAStar.Mode mode:IdAStar.Mode.values()) {
            System.setProperty("wattai.fast",Boolean.toString(fast));
            IdAStar ida=new IdAStar(new StackFrontier(),PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC),mode,128);
            var path=ida.search(new NPuzzleProblem(start,goal));
            check(path!=null && path.size()==distance,"BFS/A* optimal length "+mode+" fast="+fast);
            PuzzleBoard at=start;
            for(Node node:path) { at=at.next(node.getAction()); check(at.equals(node.getState()),"legal continuous path"); }
            check(at.equals(goal),"final goal");
        }
    }
    private static void random4(PuzzleBoard standard, PuzzleBoard arbitrary) {
        Random random=new Random(SEED);
        for(PuzzleBoard goal:List.of(standard,arbitrary)) {
            for(int trial=0;trial<24;trial++) {
                PuzzleBoard state=walk(goal,random,8+random.nextInt(15));
                var oracle=new PuzzleFeeder().getAStar(PuzzleHeuristic.MANHATTAN).search(new NPuzzleProblem(state,goal));
                int h=PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC).heuristics(state,goal);
                context="random4 goal="+text(goal)+" state="+text(state)+" h="+h+" AStarManhattan_exact="+oracle.size();
                check(h<=oracle.size(),"random 4x4 admissibility vs independent A* Manhattan");
                paired(state,goal,oracle.size());
            }
            for(int trial=0;trial<2500;trial++) {
                List<Integer> tiles=new ArrayList<>(); for(int i=0;i<16;i++)tiles.add(i);
                Collections.shuffle(tiles,random);
                PuzzleBoard state=new PuzzleBoard(tiles.stream().mapToInt(Integer::intValue).toArray(),4);
                int h=PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC).heuristics(state,goal);
                for(PuzzleMove move:state.actions()) {
                    PuzzleBoard child=state.next(move);
                    int ch=PuzzleBoard.predictor(PuzzleHeuristic.MANHATTAN_LC).heuristics(child,goal);
                    context="random permutation goal="+text(goal)+" state="+text(state)+" h="+h+" child="+text(child)+" child_h="+ch;
                    check(h<=1+ch,"random deep 4x4 consistency (not an exact-distance test)"); randomEdges++;
                }
            }
        }
    }
    private static void tableDecisions() throws Exception {
        var method=IdAStar.class.getDeclaredMethod("dominated",Node.class); method.setAccessible(true);
        for(IdAStar.Mode mode:List.of(IdAStar.Mode.TT,IdAStar.Mode.ZOBRIST)) {
            IdAStar ida=new IdAStar(new StackFrontier(),(s,g)->0,mode,1);
            GState a=new GState(1), equal=new GState(1), collision=new GState(2);
            context="direct table decisions mode="+mode;
            check(!(boolean)method.invoke(ida,new Node(a,null,null,5)),"first state admitted");
            check((boolean)method.invoke(ida,new Node(equal,null,null,6)),"larger g pruned through equals");
            check((boolean)method.invoke(ida,new Node(equal,null,null,5)),"equal g pruned");
            check(!(boolean)method.invoke(ida,new Node(equal,null,null,3)),"smaller g reopens even at capacity");
            check(!(boolean)method.invoke(ida,new Node(collision,null,null,7)),"different state same hash admitted");
            check(!(boolean)method.invoke(ida,new Node(collision,null,null,8)),"unrecorded state not pruned at capacity");
            check(ida.peakTranspositionEntries()==1,"capacity cap");
        }
    }
    private static final Map<Integer,List<Integer>> graph=new HashMap<>();
    private static final class GState extends State implements ZobristKey {
        final int id; GState(int id){this.id=id;}
        public State next(Action a){return new GState(((GMove)a).id);}
        public Iterable<GMove> actions(){return graph.get(id).stream().map(GMove::new).toList();}
        public void draw(){}
        public long zobristHash(){return 7;} // Force every state into one bucket.
        public int hashCode(){return 1;} // Also exercise Java HashMap collision equality.
        public boolean equals(Object o){return o instanceof GState s && s.id==id;}
    }
    private static final class GMove extends Action {
        final int id;GMove(int id){this.id=id;}public void draw(){} public int stepCost(){return 1;}
    }
    private static final class GProblem extends Problem {
        GProblem(){super(new GState(0),new GState(8));}
        public boolean solvable(){return true;} public int stepCost(State s,Action a){return 1;}
        public boolean applicable(State s,Action a){return true;} public void showSolution(Deque<Node> p){}
    }
    private static void graphOracles() {
        Random random=new Random(SEED);
        for(int trial=0;trial<300;trial++) {
            graph.clear();
            for(int i=0;i<9;i++) {
                List<Integer> list=new ArrayList<>();
                for(int j=0;j<9;j++)if(j!=i && (j==i+1 || random.nextDouble()<.20))list.add(j);
                Collections.shuffle(list,random);graph.put(i,list);
            }
            int[] distance=new int[9];Arrays.fill(distance,999);distance[8]=0;
            for(int k=0;k<9;k++)for(int i=0;i<9;i++)for(int j:graph.get(i))distance[i]=Math.min(distance[i],1+distance[j]);
            int[] h=new int[9];for(int i=0;i<9;i++)h[i]=random.nextInt(distance[i]+1); // admissible, often inconsistent
            context="graph trial="+trial+" edges="+graph+" exact="+Arrays.toString(distance)+" h="+Arrays.toString(h);
            for(IdAStar.Mode mode:IdAStar.Mode.values())for(int cap:new int[]{0,1,3,100}) {
                IdAStar ida=new IdAStar(new StackFrontier(),(s,g)->h[((GState)s).id],mode,cap);
                for(int repeat=0;repeat<2;repeat++) {
                    var path=ida.search(new GProblem());
                    check(path!=null && path.size()==distance[0],"TT optimum including next-bound and reset: "+mode+" cap="+cap);
                    var bounds=ida.thresholdSequence();
                    check(bounds.get(bounds.size()-1)==distance[0],"next threshold never skipped optimal cost");
                    for(int i=1;i<bounds.size();i++)check(bounds.get(i)>bounds.get(i-1),"strictly increasing thresholds");
                }
            }
        }
    }
    private static PuzzleBoard walk(PuzzleBoard state,Random random,int length) {
        PuzzleBoard parent=null;
        for(int i=0;i<length;i++) {
            List<PuzzleBoard> children=new ArrayList<>();
            for(PuzzleMove move:state.actions()){var next=state.next(move);if(!next.equals(parent))children.add(next);}
            parent=state;state=children.get(random.nextInt(children.size()));
        }
        return state;
    }
    private static long pack(PuzzleBoard b){long value=0;for(int i=0;i<b.getSize()*b.getSize();i++)value|=(long)b.tileAt(i/b.getSize(),i%b.getSize())<<(4*i);return value;}
    private static PuzzleBoard unpack(long key,int size){int[] a=new int[size*size];for(int i=0;i<a.length;i++)a[i]=(int)((key>>>(4*i))&15);return new PuzzleBoard(a,size);}
    private static String text(PuzzleBoard b){return Long.toUnsignedString(pack(b),16);}
}
