package stud.g01.runner;



import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Writes the teacher tester's output as UTF-8 without console transcoding. */
public final class LoggedSearchTester {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Usage: log.txt input type step feeder");
        Path log = Path.of(args[0]);
        Files.createDirectories(log.toAbsolutePath().getParent());
        PrintStream console = System.out;
        try (PrintStream file = new PrintStream(Files.newOutputStream(log), true, StandardCharsets.UTF_8)) {
            System.setOut(file);
            StudentSearchTester.main(Arrays.copyOfRange(args, 1, args.length));
        } finally {
            System.setOut(console);
        }
        System.out.println("Saved UTF-8 tester output: " + log);
    }
}
