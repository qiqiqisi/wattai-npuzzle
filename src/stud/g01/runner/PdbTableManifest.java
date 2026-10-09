package stud.g01.runner;

import stud.g01.pdb.DisjointPatternDatabase;
import stud.g01.pdb.PatternTable;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Records actual PDB cache files separately from grouping-level search measurements. */
public final class PdbTableManifest {
    public static void main(String[] args) throws Exception {
        PuzzleBoard goal = new PuzzleBoard(new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0},4);
        String run = Instant.now().toString().replace(':','-');
        List<String> rows = new ArrayList<>();
        for (String grouping : List.of("4443","555")) {
            DisjointPatternDatabase pdb = new DisjointPatternDatabase(goal,
                    grouping.equals("4443") ? DisjointPatternDatabase.FOUR_FOUR_FOUR_THREE
                            : DisjointPatternDatabase.FIVE_FIVE_FIVE,Path.of("generated","pdb"));
            for (PatternTable table : pdb.tables()) {
                StringBuilder pattern = new StringBuilder();
                for (int tile : table.tiles()) {
                    if (pattern.length()>0) pattern.append('-');
                    pattern.append(tile);
                }
                rows.add(run+","+grouping+","+pattern+","+table.states()+",1,"+table.states()+","+
                        table.fileBytes()+","+table.preparationMs()+","+table.loaded()+",SOLVED,"+
                        table.file().toString().replace('\\','/'));
            }
        }
        Path output=Path.of("results","pdb_tables_manifest.csv");
        rows.add(0,"run_id,grouping,pattern_tiles,state_count,bytes_per_entry,estimated_memory_bytes,actual_file_size_bytes,preparation_ms,loaded,status,cache_file");
        Files.write(output,rows,StandardCharsets.UTF_8);
        System.out.println("PDB_TABLE_ROWS="+(rows.size()-1)+" OUTPUT="+output);
    }
}
