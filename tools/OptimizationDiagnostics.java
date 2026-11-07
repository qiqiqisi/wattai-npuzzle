import stud.g01.problem.npuzzle.*;
import stud.g01.pdb.*;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;
import stud.g01.visual.SolutionTrace;
import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Separate diagnostics, excluded from the primary performance matrix. */
public final class OptimizationDiagnostics {
    public static void main(String[] args)throws Exception {
        if(args[0].equals("summarize")){summarize(Path.of(args[1]),Path.of(args[2]));return;}
        Path out=Path.of(args[2]);
        int[] values=Arrays.stream(Files.readAllLines(Path.of("resources/problems3.txt")).get(Integer.parseInt(args[0])).trim().split("\\s+"))
            .mapToInt(Integer::parseInt).toArray();
        PuzzleBoard initial=new PuzzleBoard(Arrays.copyOfRange(values,1,17),4),goal=new PuzzleBoard(Arrays.copyOfRange(values,17,33),4);
        long prepare=System.nanoTime();
        DisjointPatternDatabase pdb=new DisjointPatternDatabase(goal,DisjointPatternDatabase.FIVE_FIVE_FIVE_TRANSPOSED,Path.of(args[1]));
        Properties preparation=new Properties();long buildMs=0,loadMs=0;
        for(PatternTable table:pdb.tables())if(table.loaded())loadMs+=table.preparationMs();else buildMs+=table.preparationMs();
        preparation.setProperty("pdb_build_ms",Long.toString(buildMs));preparation.setProperty("pdb_load_ms",Long.toString(loadMs));
        preparation.setProperty("prepare_ms",Double.toString((System.nanoTime()-prepare)/1e6));
        preparation.setProperty("initial_state",text(initial));preparation.setProperty("goal_state",text(goal));
        preparation.setProperty("java_version",System.getProperty("java.version"));preparation.setProperty("jvm_heap_limit_bytes",Long.toString(Runtime.getRuntime().maxMemory()));
        save(out.resolve("preparation.properties"),preparation);
        IdAStar ida=new IdAStar(new StackFrontier(),pdb,IdAStar.Mode.BASIC,0);
        // Reuse the exact primary counter serializer without changing frozen production source.
        var serializer=stud.g01.runner.OptimizationWorker.class.getDeclaredMethod("metrics",Properties.class,IdAStar.class,DisjointPatternDatabase.class);
        serializer.setAccessible(true);
        long begin=System.nanoTime();
        if(Boolean.parseBoolean(args[3]))ida.progressListener(current->{
            Properties snapshot=new Properties();
            snapshot.setProperty("initial_state",text(initial));snapshot.setProperty("goal_state",text(goal));
            snapshot.setProperty("java_version",System.getProperty("java.version"));snapshot.setProperty("jvm_heap_limit_bytes",Long.toString(Runtime.getRuntime().maxMemory()));
            snapshot.setProperty("snapshot_search_ms",Double.toString((System.nanoTime()-begin)/1e6));
            long build=0,load=0;for(PatternTable table:pdb.tables())if(table.loaded())load+=table.preparationMs();else build+=table.preparationMs();
            snapshot.setProperty("pdb_build_ms",Long.toString(build));snapshot.setProperty("pdb_load_ms",Long.toString(load));
            try {serializer.invoke(null,snapshot,current,pdb);Path p=out.resolve("snapshot-"+current.iterations()+"-"+current.nodesExpanded()+".properties");if(!Files.exists(p))save(p,snapshot);}
            catch(Exception e){throw new IllegalStateException(e);}
        });
        var path=ida.search(new NPuzzleProblem(initial,goal));
        double ms=(System.nanoTime()-begin)/1e6;
        if(path==null)throw new AssertionError("No solution");
        List<String> lines=new ArrayList<>(List.of("format\tNPUZZLE_SOLUTION_V1","algorithm\tIDA","heuristic\tDISJOINT_PATTERN","size\t4",
            "elapsed_ms\t"+ms,"generated\t"+ida.nodesGenerated(),"expanded\t"+ida.nodesExpanded(),"solution_length\t"+path.size(),
            "goal\t"+text(goal),"step\tmove\tstate","0\tSTART\t"+text(initial)));
        int step=0;for(var node:path)lines.add((++step)+"\t"+node.getAction()+"\t"+text((PuzzleBoard)node.getState()));
        Files.write(out.resolve("solution.tsv"),lines,StandardOpenOption.CREATE_NEW);SolutionTrace.read(out.resolve("solution.tsv"));
        Properties p=new Properties();p.putAll(preparation);serializer.invoke(null,p,ida,pdb);
        p.setProperty("elapsed_ms",Double.toString(ms));p.setProperty("solution_length",Integer.toString(path.size()));
        p.setProperty("path_cost",Integer.toString(path.size()));p.setProperty("generated",Integer.toString(ida.nodesGenerated()));
        p.setProperty("expanded",Integer.toString(ida.nodesExpanded()));p.setProperty("pdb_lookup_count",Long.toString(pdb.lookups()));
        p.setProperty("reverse_move_prunes",Long.toString(ida.reverseMovePrunes()));p.setProperty("cycle_prunes",Long.toString(ida.cyclePrunes()));
        p.setProperty("child_states_created",Long.toString(ida.childStatesCreated()));p.setProperty("child_nodes_created",Long.toString(ida.childNodesCreated()));
        p.setProperty("full_heuristic_evaluations",Long.toString(ida.fullHeuristicEvaluations()));p.setProperty("incremental_heuristic_evaluations",Long.toString(ida.incrementalHeuristicEvaluations()));
        p.setProperty("solution_file",out.resolve("solution.tsv").toString().replace('\\','/'));p.setProperty("status","SOLVED");
        save(out.resolve("metrics.properties"),p);
    }
    private static void save(Path file,Properties p)throws Exception {try(var stream=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){p.store(stream,"Diagnostic measurements; not a primary matrix run");}}
    private static String text(PuzzleBoard b){List<String> tiles=new ArrayList<>();for(int i=0;i<16;i++)tiles.add(Integer.toString(b.tileAt(i/4,i%4)));return String.join(" ",tiles);}
    private static void summarize(Path file,Path output)throws Exception {
        long total=0,search=0,pdb=0,leafPdb=0;
        Map<String,Long> leaves=new HashMap<>();
        try(RecordingFile recording=new RecordingFile(file)) {
            while(recording.hasMoreEvents()) {
                RecordedEvent e=recording.readEvent();if(!e.getEventType().getName().equals("jdk.ExecutionSample"))continue;
                total++;var stack=e.getStackTrace();if(stack==null || stack.getFrames().isEmpty())continue;
                boolean inSearch=false,inPdb=false;
                for(var frame:stack.getFrames()) {
                    String c=frame.getMethod().getType().getName();
                    if(c.equals("stud.g01.solver.IdAStar"))inSearch=true;
                    if(c.startsWith("stud.g01.pdb."))inPdb=true;
                }
                if(!inSearch)continue;search++;if(inPdb)pdb++;
                var method=stack.getFrames().get(0).getMethod();String leaf=method.getType().getName()+"."+method.getName();
                if(leaf.startsWith("stud.g01.pdb."))leafPdb++;
                leaves.merge(leaf,1L,Long::sum);
            }
        }
        List<String> text=new ArrayList<>(List.of("all_execution_samples="+total,"search_stack_samples="+search,
            "pdb_in_search_stack_samples="+pdb,"pdb_leaf_samples="+leafPdb,"Interpretation=sampled CPU stacks, not exact wall-time share; JIT inlining and sampling bias apply"));
        leaves.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(25).forEach(e->text.add(e.getKey()+"="+e.getValue()));
        Files.write(output,text,StandardOpenOption.CREATE_NEW);
    }
}
