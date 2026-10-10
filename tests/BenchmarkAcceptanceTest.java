import stud.g01.runner.BenchmarkAcceptance;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression for the former false positive when parent exits zero after TIMEOUT. */
public final class BenchmarkAcceptanceTest {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of("generated/acceptance-test");Files.createDirectories(dir);
        Path csv=dir.resolve("stage3.csv");
        String header="run_id,problem,algorithm,heuristic,pattern,solution_length,path_cost,elapsed_ms,generated,expanded,iterations,pdb_build_ms,pdb_load_ms,pdb_lookup_count,pdb_entries,pdb_file_bytes,max_depth,peak_table_size,status,solution_file\n";
        for(String status:new String[]{"TIMEOUT","OOM","FAIL","UNSOLVABLE"}) {
            Files.writeString(csv,header+"r,problems3_10,IDA,DISJOINT_PATTERN,555T,,,,,,,,,,,,,,"+status+",\n");
            boolean rejected=false;
            try {BenchmarkAcceptance.verify(csv,Path.of("resources/problems3.txt"),9,70,60000);}
            catch(IllegalArgumentException expected) {rejected=expected.getMessage().startsWith("Benchmark status:");}
            if(!rejected) throw new AssertionError("False acceptance of "+status);
        }
        System.out.println("PASS: BenchmarkAcceptance rejects TIMEOUT/OOM/FAIL/UNSOLVABLE independently of parent exit");
    }
}
