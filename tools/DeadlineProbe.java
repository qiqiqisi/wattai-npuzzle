import java.nio.file.*;
/** Watchdog fixture: a killed JVM must never reach the delayed marker. */
public final class DeadlineProbe {
    public static void main(String[] args) throws Exception {
        Files.writeString(Path.of(args[0]),Long.toString(ProcessHandle.current().pid()),StandardOpenOption.CREATE_NEW);
        Thread.sleep(6000);
        Files.writeString(Path.of(args[1]),"unexpected surviving JVM",StandardOpenOption.CREATE_NEW);
    }
}
