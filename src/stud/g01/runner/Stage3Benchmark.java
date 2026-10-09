package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.pdb.DisjointPatternDatabase;
import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.queue.PqFrontier;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.nio.file.StandardCopyOption;
import java.lang.management.ManagementFactory;

/** Isolated third-stage search with explicit timeout, heap and replay validation. */
public final class Stage3Benchmark {
    private static final String HEADER = "run_id,problem,algorithm,heuristic,pattern,solution_length,path_cost,elapsed_ms,generated,expanded,iterations,pdb_build_ms,pdb_load_ms,pdb_lookup_count,pdb_entries,pdb_file_bytes,max_depth,peak_table_size,status,solution_file";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--worker")) { worker(args); return; }
        if (args.length != 6) throw new IllegalArgumentException(
                "Usage: input problem_index ASTAR|IDA heuristic 4443|555|555T timeout_seconds");
        if (!List.of("ASTAR","IDA").contains(args[2]) || !List.of("4443","555","555T").contains(args[4])
                || Integer.parseInt(args[1])<0 || Long.parseLong(args[5])<1)
            throw new IllegalArgumentException("Invalid algorithm, pattern, index or timeout");
        String runId = Instant.now().toString().replace(':', '-');
        String id = inputId(args[0], Integer.parseInt(args[1]));
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path diagnostics = Path.of(System.getProperty("wattai.output", "results/diagnostics-20261008"));
        Files.createDirectories(diagnostics);
        Path log = diagnostics.resolve(runId + ".log");
        Path snapshot = diagnostics.resolve(runId + ".properties");
        List<String> command = new ArrayList<>(List.of(javaCommand, "-Xmx1024m"));
        for (String key : List.of("wattai.mode", "wattai.limit", "wattai.fast", "wattai.cache"))
            if (System.getProperty(key) != null) command.add("-D"+key+"="+System.getProperty(key));
        command.add("-Dwattai.snapshot="+snapshot);
        if (Boolean.getBoolean("wattai.jfr")) command.add("-XX:StartFlightRecording=settings=profile,duration="
                + Math.max(1, Long.parseLong(args[5])-3) + "s,filename=" + diagnostics.resolve(runId+".jfr"));
        command.addAll(List.of("-cp", System.getProperty("java.class.path"), Stage3Benchmark.class.getName(),
                "--worker", runId, args[0], args[1], args[2], args[3], args[4]));
        long parentStart = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        String row;
        if (!process.waitFor(Long.parseLong(args[5]), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            row = empty(runId, id, args[2], args[3], args[4], "TIMEOUT");
        } else {
            String output = Files.readString(log, StandardCharsets.UTF_8);
            int marker = output.lastIndexOf("RESULT:");
            if (marker >= 0) row = output.substring(marker + 7).strip().lines().findFirst().orElseThrow();
            else row = empty(runId, id, args[2], args[3], args[4],
                    output.contains("OutOfMemoryError") ? "OOM" : "FAIL");
        }
        Path csv = diagnostics.resolve("stage3.csv");
        Files.createDirectories(csv.toAbsolutePath().getParent());
        if (!Files.exists(csv)) Files.writeString(csv, HEADER + "\n", StandardCharsets.UTF_8);
        Files.writeString(csv, row + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(diagnostics.resolve(runId+"-parent.txt"), "end_to_end_ms="
                +(System.nanoTime()-parentStart)/1e6+"\ntimeout_s="+args[5]+"\ncommand="+command+"\nstatus="
                +row.split(",",-1)[18]+"\n", StandardCharsets.UTF_8);
        System.out.println(row);
    }

    private static String empty(String run, String id, String algorithm, String heuristic, String pattern, String status) {
        String[] cells = new String[20];
        Arrays.fill(cells, "");
        cells[0] = run; cells[1] = id; cells[2] = algorithm; cells[3] = heuristic; cells[4] = pattern;
        cells[18] = status;
        return String.join(",", cells);
    }

    private static String inputId(String input, int index) {
        return Path.of(input).getFileName().toString().replaceFirst("\\.[^.]+$", "")+"_"+(index+1);
    }

    private static void worker(String[] args) throws Exception {
        long workerStart = System.nanoTime();
        String run = args[1];
        Path input = Path.of(args[2]);
        int index = Integer.parseInt(args[3]);
        String algorithm = args[4];
        String heuristicName = args[5];
        HeuristicType type = heuristicName.equals("PDB_MAX_LC") || heuristicName.equals("PDB_MAX_555")
                ? null : HeuristicType.valueOf(heuristicName);
        String pattern = args[6];
        List<Problem> problems = new PuzzleFeeder().getProblems(new ArrayList<>(Files.readAllLines(input)));
        Problem problem = problems.get(index);
        PuzzleBoard initial = (PuzzleBoard) problem.root().getState();
        PuzzleBoard goal;
        // Teacher input stores the goal separately; read it from the second half of the same line.
        String[] cells = Files.readAllLines(input).get(index).trim().split("\\s+");
        int length = initial.getSize() * initial.getSize();
        int[] goalTiles = new int[length];
        for (int i = 0; i < length; i++) goalTiles[i] = Integer.parseInt(cells[1 + length + i]);
        goal = new PuzzleBoard(goalTiles, initial.getSize());
        DisjointPatternDatabase pdb = null;
        List<DisjointPatternDatabase> databases = new ArrayList<>();
        Predictor predictor;
        System.out.println("PHASE PREPARE");
        if (type == HeuristicType.DISJOINT_PATTERN || heuristicName.equals("PDB_MAX_LC") || heuristicName.equals("PDB_MAX_555")) {
            int[][] grouping = initial.getSize() == 3 ? DisjointPatternDatabase.EIGHT_PUZZLE
                    : pattern.equals("555") ? DisjointPatternDatabase.FIVE_FIVE_FIVE
                    : pattern.equals("555T") ? DisjointPatternDatabase.FIVE_FIVE_FIVE_TRANSPOSED
                    : DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE;
            pdb = new DisjointPatternDatabase(goal, grouping, Path.of(System.getProperty("wattai.cache", "generated/pdb")));
            databases.add(pdb);
            if (heuristicName.equals("PDB_MAX_555")) {
                if (initial.getSize()!=4 || !pattern.equals("555")) throw new IllegalArgumentException("PDB_MAX_555 requires 4x4 and 555");
                DisjointPatternDatabase first = pdb;
                DisjointPatternDatabase second = new DisjointPatternDatabase(goal,DisjointPatternDatabase.FIVE_FIVE_FIVE_TRANSPOSED,
                        Path.of(System.getProperty("wattai.cache", "generated/pdb")));
                databases.add(second);
                predictor = (state,target) -> Math.max(first.heuristics(state,target),second.heuristics(state,target));
            } else if (heuristicName.equals("PDB_MAX_LC")) {
                DisjointPatternDatabase database = pdb;
                Predictor lc = PuzzleBoard.predictor(HeuristicType.MANHATTAN_LC);
                predictor = (state, target) -> Math.max(database.heuristics(state, target),
                        lc.heuristics(state, target));
            } else predictor = pdb;
        } else predictor = PuzzleBoard.predictor(type);
        AbstractSearcher searcher = algorithm.equals("ASTAR")
                ? new BestFirstSearcher(new PqFrontier(Node.evaluator(EvaluationType.FULL)), predictor)
                : new IdAStar(new StackFrontier(), predictor, IdAStar.Mode.valueOf(System.getProperty("wattai.mode", "ZOBRIST")),
                        Integer.getInteger("wattai.limit", IdAStar.DEFAULT_TRANSPOSITION_LIMIT));
        long start = System.nanoTime();
        System.out.println("PHASE SEARCH prepare_ms="+(start-workerStart)/1e6);
        if (searcher instanceof IdAStar ida && System.getProperty("wattai.snapshot") != null) {
            ida.progressListener(current -> {
                try {
                    java.util.Properties data = new java.util.Properties();
                    data.setProperty("search_ms", Double.toString((System.nanoTime()-start)/1e6));
                    data.setProperty("prepare_ms", Double.toString((start-workerStart)/1e6));
                    data.setProperty("expanded", Integer.toString(current.nodesExpanded()));
                    data.setProperty("generated", Integer.toString(current.nodesGenerated()));
                    data.setProperty("initial_h", Long.toString(current.initialBound()));
                    data.setProperty("bound", Long.toString(current.finalBound()));
                    data.setProperty("thresholds", current.thresholdSequence().toString());
                    data.setProperty("cycle_prunes", Long.toString(current.cyclePrunes()));
                    data.setProperty("tt_prunes", Long.toString(current.transpositionPrunes()));
                    data.setProperty("tt_entries", Integer.toString(current.transpositionEntries()));
                    data.setProperty("max_depth", Integer.toString(current.maxDepth()));
                    long queries = 0;
                    for(DisjointPatternDatabase database:databases) queries+=database.lookups();
                    data.setProperty("pdb_lookups", databases.isEmpty() ? "NOT_APPLICABLE" : Long.toString(queries));
                    long build=0, load=0;
                    for(DisjointPatternDatabase database:databases) for (PatternTable table:database.tables()) {
                        if(table.loaded()) load+=table.preparationMs(); else build+=table.preparationMs();
                    }
                    data.setProperty("pdb_build_ms", Long.toString(build));
                    data.setProperty("pdb_load_ms", Long.toString(load));
                    long gcMs=0, gcCount=0;
                    for (var gc:ManagementFactory.getGarbageCollectorMXBeans()) {
                        gcMs+=Math.max(0,gc.getCollectionTime()); gcCount+=Math.max(0,gc.getCollectionCount());
                    }
                    data.setProperty("gc_ms", Long.toString(gcMs));
                    data.setProperty("gc_count", Long.toString(gcCount));
                    data.setProperty("heap_used_bytes", Long.toString(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()));
                    data.setProperty("jit_ms", Long.toString(ManagementFactory.getCompilationMXBean().getTotalCompilationTime()));
                    Path target=Path.of(System.getProperty("wattai.snapshot"));
                    Path temp=target.resolveSibling(target.getFileName()+".tmp");
                    try(var stream=Files.newOutputStream(temp)) { data.store(stream,"Search-thread coherent snapshot; not final timeout counters"); }
                    atomicSnapshot(temp,target);
                    System.out.println("PROGRESS "+data.getProperty("search_ms")+" bound="+current.finalBound()+" expanded="+current.nodesExpanded());
                } catch (Exception error) { throw new IllegalStateException("Cannot persist diagnostic snapshot",error); }
            });
        }
        Deque<Node> path = searcher.search(problem);
        double elapsed = (System.nanoTime() - start) / 1e6;
        if (path == null) throw new AssertionError("No path");
        long replayStart = System.nanoTime();
        if (!replay(initial, goal, path)) throw new AssertionError("Invalid solution replay");
        double replayMs = (System.nanoTime()-replayStart)/1e6;
        long build = 0, load = 0, entries = 0, bytes = 0;
        for(DisjointPatternDatabase database:databases) for (PatternTable table : database.tables()) {
            if (table.loaded()) load += table.preparationMs(); else build += table.preparationMs();
            entries += table.states(); bytes += table.fileBytes();
        }
        String id = inputId(input.toString(), index);
        Path solution = Path.of("results", "solutions", run + "-" + id + "-" + algorithm + "-" + heuristicName + ".tsv");
        long exportStart = System.nanoTime();
        export(solution, initial, goal, path, algorithm, heuristicName, elapsed,
                searcher.nodesGenerated(), searcher.nodesExpanded());
        double exportMs = (System.nanoTime()-exportStart)/1e6;
        if(System.getProperty("wattai.snapshot")!=null) {
            Path snapshot=Path.of(System.getProperty("wattai.snapshot"));
            Path timings=snapshot.resolveSibling(run+"-timings.properties");
            Files.writeString(timings,"search_ms="+elapsed+"\nreplay_ms="+replayMs+"\nexport_ms="+exportMs
                    +"\nworker_total_ms="+(System.nanoTime()-workerStart)/1e6+"\nprepare_ms="+(start-workerStart)/1e6
                    +"\npdb_build_ms="+build+"\npdb_load_ms="+load+"\n",StandardCharsets.UTF_8);
        }
        IdAStar ida = searcher instanceof IdAStar found ? found : null;
        String[] row = new String[20];
        Arrays.fill(row, "");
        row[0]=run; row[1]=id; row[2]=algorithm; row[3]=heuristicName; row[4]=pattern;
        row[5]=Integer.toString(path.size());
        row[6]=Integer.toString(path.isEmpty()?0:path.getLast().getPathCost());
        row[7]=String.format(Locale.ROOT,"%.3f",elapsed);
        row[8]=Integer.toString(searcher.nodesGenerated());
        row[9]=Integer.toString(searcher.nodesExpanded());
        row[10]=ida==null?"":Integer.toString(ida.iterations());
        row[11]=Long.toString(build); row[12]=Long.toString(load);
        long queries=0; for(DisjointPatternDatabase database:databases) queries+=database.lookups();
        row[13]=databases.isEmpty()?"":Long.toString(queries);
        row[14]=pdb==null?"":Long.toString(entries);
        row[15]=pdb==null?"":Long.toString(bytes);
        row[16]=ida==null?"":Integer.toString(ida.maxDepth());
        row[17]=ida==null?"":Integer.toString(ida.peakTranspositionEntries());
        row[18]="SOLVED"; row[19]=solution.toString().replace('\\','/');
        System.out.println("RESULT:"+String.join(",",row));
    }

    /** Windows file watchers can briefly deny replacement; retry only the atomic move, with a fixed budget. */
    private static void atomicSnapshot(Path temp, Path target) throws Exception {
        for(int attempt=0;;attempt++) {
            try {
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch(java.nio.file.AccessDeniedException locked) {
                if(attempt==3) throw locked;
                Thread.sleep(new long[]{5,20,50}[attempt]);
            }
        }
    }

    private static boolean replay(PuzzleBoard initial, PuzzleBoard goal, Deque<Node> path) {
        PuzzleBoard state = initial;
        int step = 0;
        for (Node node : path) {
            boolean legal = false;
            for (PuzzleMove move : state.actions()) if (move.equals(node.getAction())) legal = true;
            if (!legal) return false;
            state = state.next(node.getAction());
            if (!state.equals(node.getState()) || node.getPathCost() != ++step) return false;
        }
        return state.equals(goal);
    }

    private static void export(Path file, PuzzleBoard initial, PuzzleBoard goal, Deque<Node> path,
                               String algorithm, String heuristic, double ms, int generated, int expanded) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>();
        lines.add("format\tNPUZZLE_SOLUTION_V1");
        lines.add("algorithm\t"+algorithm);
        lines.add("heuristic\t"+heuristic);
        lines.add("size\t"+initial.getSize());
        lines.add("elapsed_ms\t"+String.format(Locale.ROOT,"%.3f",ms));
        lines.add("generated\t"+generated);
        lines.add("expanded\t"+expanded);
        lines.add("solution_length\t"+path.size());
        lines.add("goal\t"+serialize(goal));
        lines.add("step\tmove\tstate");
        lines.add("0\tSTART\t"+serialize(initial));
        int step=0;
        for (Node node:path) lines.add((++step)+"\t"+node.getAction()+"\t"+serialize((PuzzleBoard)node.getState()));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static String serialize(PuzzleBoard board) {
        List<String> cells = new ArrayList<>();
        for (int row=0;row<board.getSize();row++) for(int col=0;col<board.getSize();col++)
            cells.add(Integer.toString(board.tileAt(row,col)));
        return String.join(" ",cells);
    }
}
