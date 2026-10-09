import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** Summarizes sampled evidence without exporting a very large JSON event stream. */
public class JfrSummary {
    public static void main(String[] args) throws Exception {
        Map<String,Long> leaves=new HashMap<>(), allocation=new HashMap<>();
        long samples=0, bytes=0;
        try (RecordingFile recording=new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event=recording.readEvent();
                if(event.getEventType().getName().equals("jdk.ExecutionSample")) {
                    samples++;
                    RecordedStackTrace stack=event.getStackTrace();
                    if(stack!=null && !stack.getFrames().isEmpty()) {
                        var method=stack.getFrames().get(0).getMethod();
                        leaves.merge(method.getType().getName()+"."+method.getName(),1L,Long::sum);
                    }
                } else if(event.getEventType().getName().equals("jdk.ObjectAllocationSample")) {
                    long weight=event.getLong("weight"); bytes+=weight;
                    allocation.merge(event.getClass("objectClass").getName(),weight,Long::sum);
                }
            }
        }
        System.out.println("Execution samples="+samples+" allocation sampled weight bytes="+bytes);
        for(var map:List.of(leaves,allocation)) {
            System.out.println(map==leaves?"Leaf samples":"Allocation weights (estimate, not heap peak)");
            map.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(20)
                .forEach(e->System.out.println(e.getKey()+"\t"+e.getValue()));
        }
    }
}
