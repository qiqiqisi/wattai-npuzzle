package stud.g01.runner;

import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.*;
import core.solver.queue.*;
import stud.g01.heuristic.PuzzleHeuristic;
import stud.g01.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.queue.PqFrontier;
import stud.g01.solver.IdAStar;
import stud.g01.visual.SolutionTrace;
import stud.queue.StackFrontier;
import java.nio.file.*;
import java.util.*;

/** One fresh JVM; the Python parent owns its deadline and preserves all outcomes. */
public final class OptimizationWorker {
    public static void main(String[] args) throws Exception {
        if (args.length!=9) throw new IllegalArgumentException("input index algorithm heuristic pattern mode limit cache output");
        Path output=Path.of(args[8]);
        if(!Files.isDirectory(output) || Files.exists(output.resolve("metrics.properties")))
            throw new IllegalArgumentException("Require a fresh parent-created run directory");
        int[] input=Arrays.stream(Files.readAllLines(Path.of(args[0])).get(Integer.parseInt(args[1])).trim().split("\\s+"))
                .mapToInt(Integer::parseInt).toArray();
        int size=input[0], count=size*size;
        if(input.length!=1+2*count)throw new IllegalArgumentException("Input length");
        PuzzleBoard initial=new PuzzleBoard(Arrays.copyOfRange(input,1,1+count),size);
        PuzzleBoard goal=new PuzzleBoard(Arrays.copyOfRange(input,1+count,input.length),size);
        NPuzzleProblem problem=new NPuzzleProblem(initial,goal);
        Properties data=new Properties();
        data.setProperty("java_version",System.getProperty("java.version"));
        data.setProperty("jvm_heap_limit_bytes",Long.toString(Runtime.getRuntime().maxMemory()));
        data.setProperty("initial_state",serialize(initial));data.setProperty("goal_state",serialize(goal));
        data.setProperty("pdb_build_ms","0");data.setProperty("pdb_load_ms","0");
        if(!problem.solvable()) { data.setProperty("status","UNSOLVABLE");save(output.resolve("metrics.properties"),data);return; }
        System.out.println("PHASE PREPARE");
        DisjointPatternDatabase pdb=null;
        Predictor predictor;
        long prepare=System.nanoTime();
        if(args[3].equals("DISJOINT_PATTERN")) {
            int[][] pattern=size==3 ? DisjointPatternDatabase.EIGHT_PUZZLE
                : args[4].equals("555T") ? DisjointPatternDatabase.FIVE_FIVE_FIVE_TRANSPOSED
                : DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE;
            pdb=new DisjointPatternDatabase(goal,pattern,Path.of(args[7]));predictor=pdb;
            long build=0,load=0;
            for(PatternTable table:pdb.tables())if(table.loaded())load+=table.preparationMs();else build+=table.preparationMs();
            data.setProperty("pdb_build_ms",Long.toString(build));data.setProperty("pdb_load_ms",Long.toString(load));
            data.setProperty("pdb_lookup_count","0");
        } else predictor=PuzzleBoard.predictor(PuzzleHeuristic.valueOf(args[3]));
        data.setProperty("prepare_ms",Double.toString((System.nanoTime()-prepare)/1e6));
        save(output.resolve("preparation.properties"),data);
        AbstractSearcher solver=args[2].equals("ASTAR")
            ? new BestFirstSearcher(new PqFrontier(Node.evaluator(EvaluationType.FULL)),predictor)
            : new IdAStar(new StackFrontier(),predictor,IdAStar.Mode.valueOf(args[5]),Integer.parseInt(args[6]));
        DisjointPatternDatabase database=pdb;
        long begin=System.nanoTime();
        if(solver instanceof IdAStar ida)ida.progressListener(current->{
            try {
                Properties snapshot=new Properties();snapshot.putAll(data);
                metrics(snapshot,current,database);
                snapshot.setProperty("snapshot_search_ms",Double.toString((System.nanoTime()-begin)/1e6));
                // Append-free snapshots: every threshold is retained, plus a final successful snapshot.
                Path path=output.resolve("snapshot-"+current.iterations()+"-"+current.nodesExpanded()+".properties");
                if(!Files.exists(path))save(path,snapshot);
            } catch(Exception error){throw new IllegalStateException(error);}
        });
        System.out.println("PHASE SEARCH");
        var path=solver.search(problem);
        data.setProperty("elapsed_ms",Double.toString((System.nanoTime()-begin)/1e6));
        data.setProperty("generated",Integer.toString(solver.nodesGenerated()));
        data.setProperty("expanded",Integer.toString(solver.nodesExpanded()));
        if(solver instanceof IdAStar ida)metrics(data,ida,pdb);
        if(pdb!=null)data.setProperty("pdb_lookup_count",Long.toString(pdb.lookups()));
        if(path==null) {data.setProperty("status","FAIL");save(output.resolve("metrics.properties"),data);return;}
        PuzzleBoard at=initial;int step=0;
        List<String> trace=new ArrayList<>(List.of("format\tNPUZZLE_SOLUTION_V1","algorithm\t"+args[2],
            "heuristic\t"+args[3],"size\t"+size,"elapsed_ms\t"+data.getProperty("elapsed_ms"),
            "generated\t"+solver.nodesGenerated(),"expanded\t"+solver.nodesExpanded(),"solution_length\t"+path.size(),
            "goal\t"+serialize(goal),"step\tmove\tstate","0\tSTART\t"+serialize(initial)));
        for(Node node:path) {
            if(!problem.applicable(at,node.getAction()))throw new AssertionError("Illegal action");
            at=at.next(node.getAction());
            if(!at.equals(node.getState()) || node.getPathCost()!=++step)throw new AssertionError("Discontinuous path");
            trace.add(step+"\t"+node.getAction()+"\t"+serialize(at));
        }
        if(!at.equals(goal))throw new AssertionError("Wrong goal");
        Path solution=output.resolve("solution.tsv");Files.write(solution,trace,StandardOpenOption.CREATE_NEW);
        SolutionTrace.read(solution); // Existing strict parser is an additional check.
        data.setProperty("status","SOLVED");data.setProperty("solution_length",Integer.toString(path.size()));
        data.setProperty("path_cost",Integer.toString(step));data.setProperty("solution_file",solution.toString().replace('\\','/'));
        save(output.resolve("metrics.properties"),data);
        System.out.println("SOLVED length="+step+" search_ms="+data.getProperty("elapsed_ms"));
    }
    private static void metrics(Properties p,IdAStar ida,DisjointPatternDatabase pdb) {
        p.setProperty("generated",Integer.toString(ida.nodesGenerated()));p.setProperty("expanded",Integer.toString(ida.nodesExpanded()));
        p.setProperty("iterations",Integer.toString(ida.iterations()));p.setProperty("initial_bound",Long.toString(ida.initialBound()));
        p.setProperty("final_bound",Long.toString(ida.finalBound()));p.setProperty("threshold_sequence",ida.thresholdSequence().toString());
        p.setProperty("cycle_prunes",Long.toString(ida.cyclePrunes()));p.setProperty("transposition_prunes",Long.toString(ida.transpositionPrunes()));
        p.setProperty("peak_transposition_entries",Integer.toString(ida.peakTranspositionEntries()));
        p.setProperty("reverse_move_prunes",Long.toString(ida.reverseMovePrunes()));
        p.setProperty("child_states_created",Long.toString(ida.childStatesCreated()));p.setProperty("child_nodes_created",Long.toString(ida.childNodesCreated()));
        p.setProperty("full_heuristic_evaluations",Long.toString(ida.fullHeuristicEvaluations()));
        p.setProperty("incremental_heuristic_evaluations",Long.toString(ida.incrementalHeuristicEvaluations()));
        p.setProperty("peak_path_states",Integer.toString(ida.peakPathStates()));
        if(pdb!=null)p.setProperty("pdb_lookup_count",Long.toString(pdb.lookups()));
    }
    private static void save(Path path,Properties p)throws Exception {
        try(var stream=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){p.store(stream,"Actual worker measurements; snapshots are partial, never final timeout metrics");}
    }
    private static String serialize(PuzzleBoard board) {
        List<String> cells=new ArrayList<>();for(int i=0;i<board.getSize()*board.getSize();i++)cells.add(Integer.toString(board.tileAt(i/board.getSize(),i%board.getSize())));
        return String.join(" ",cells);
    }
}
