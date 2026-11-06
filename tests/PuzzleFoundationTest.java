import core.problem.Action;
import core.problem.State;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.problem.npuzzle.PuzzleMove.Direction;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;

/** Standalone checks for the N-Puzzle foundation; run with Java 19. */
public final class PuzzleFoundationTest {
    private static int checks;

    public static void main(String[] args) {
        PuzzleBoard goal3 = board(3, 1, 2, 3, 4, 5, 6, 7, 8, 0);
        PuzzleBoard goal4 = board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 0);

        check("A 3x3 construction", goal3.getSize() == 3 && goal3.tileAt(2, 2) == 0);
        check("B 4x4 construction", goal4.getSize() == 4 && goal4.tileAt(3, 2) == 15);
        PuzzleBoard display = board(3, 8, 6, 7, 2, 5, 4, 3, 0, 1);
        check("C draw", draw(display).equals("8 6 7\n2 5 4\n3 # 1" + System.lineSeparator()));

        check("D top-left corner", directions(withBlankAt(3, 0)).equals(EnumSet.of(Direction.DOWN, Direction.RIGHT)));
        check("D top-right corner", directions(withBlankAt(3, 2)).equals(EnumSet.of(Direction.DOWN, Direction.LEFT)));
        check("D bottom-left corner", directions(withBlankAt(3, 6)).equals(EnumSet.of(Direction.UP, Direction.RIGHT)));
        check("D bottom-right corner", directions(withBlankAt(3, 8)).equals(EnumSet.of(Direction.UP, Direction.LEFT)));
        check("D 4x4 corner", directions(withBlankAt(4, 15)).equals(EnumSet.of(Direction.UP, Direction.LEFT)));
        check("E top edge", directions(withBlankAt(3, 1)).equals(EnumSet.of(Direction.DOWN, Direction.LEFT, Direction.RIGHT)));
        check("E left edge", directions(withBlankAt(3, 3)).equals(EnumSet.of(Direction.UP, Direction.DOWN, Direction.RIGHT)));
        check("E right edge", directions(withBlankAt(3, 5)).equals(EnumSet.of(Direction.UP, Direction.DOWN, Direction.LEFT)));
        check("E bottom edge", directions(withBlankAt(3, 7)).equals(EnumSet.of(Direction.UP, Direction.LEFT, Direction.RIGHT)));

        PuzzleBoard center = board(3, 1, 2, 3, 4, 0, 5, 6, 7, 8);
        check("F center", directions(center).equals(EnumSet.allOf(Direction.class)));
        check("G UP", center.next(move(Direction.UP)).equals(board(3, 1, 0, 3, 4, 2, 5, 6, 7, 8)));
        check("G DOWN", center.next(move(Direction.DOWN)).equals(board(3, 1, 2, 3, 4, 7, 5, 6, 0, 8)));
        check("G LEFT", center.next(move(Direction.LEFT)).equals(board(3, 1, 2, 3, 0, 4, 5, 6, 7, 8)));
        check("G RIGHT", center.next(move(Direction.RIGHT)).equals(board(3, 1, 2, 3, 4, 5, 0, 6, 7, 8)));
        check("G 4x4 move", goal4.next(move(Direction.UP)).equals(board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 0, 13, 14, 15, 12)));
        check("H original unchanged", center.equals(board(3, 1, 2, 3, 4, 0, 5, 6, 7, 8)));
        expectIllegal("invalid edge move", () -> goal3.next(move(Direction.RIGHT)));
        expectIllegal("wrong Action type", () -> goal3.next(new Action() {
            public void draw() {}
            public int stepCost() { return 1; }
        }));

        PuzzleBoard duplicate = board(3, 1, 2, 3, 4, 5, 6, 7, 8, 0);
        check("I equals", goal3.equals(duplicate) && duplicate.equals(goal3));
        check("J hashCode", goal3.hashCode() == duplicate.hashCode());
        HashSet<State> explored = new HashSet<>();
        explored.add(goal3);
        check("K HashSet", explored.contains(duplicate) && !explored.add(duplicate) && explored.size() == 1);
        int[] source = {1, 2, 3, 4, 5, 6, 7, 8, 0};
        PuzzleBoard copied = new PuzzleBoard(source, 3);
        source[0] = 8;
        check("L defensive copy", copied.equals(goal3) && copied.tileAt(0, 0) == 1);
        check("M different states", !goal3.equals(goal3.next(move(Direction.LEFT))));
        check("M different sizes", !goal3.equals(goal4));
        expectIllegal("duplicate tile", () -> board(3, 1, 1, 2, 3, 4, 5, 6, 7, 0));
        expectIllegal("out-of-range tile", () -> board(3, 1, 2, 3, 4, 5, 6, 7, 9, 0));
        expectIllegal("wrong tile count", () -> new PuzzleBoard(new int[]{0, 1}, 3));

        check("N solvable 8", solvable(board(3, 1, 2, 3, 4, 5, 6, 7, 0, 8), goal3));
        check("O unsolvable 8", !solvable(board(3, 1, 2, 3, 4, 5, 6, 8, 7, 0), goal3));
        check("P equal goal 8", solvable(goal3, goal3));
        check("Q solvable 15", solvable(board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 0, 15), goal4));
        check("Q vertical move 15", solvable(goal4.next(move(Direction.UP)), goal4));
        PuzzleBoard swapped4 = board(4, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 15, 14, 0);
        check("R unsolvable 15", !solvable(swapped4, goal4));
        check("S equal goal 15", solvable(goal4, goal4));
        PuzzleBoard custom3 = board(3, 1, 2, 3, 4, 5, 6, 8, 7, 0);
        check("T arbitrary 8 goal reachable", solvable(custom3.next(move(Direction.LEFT)), custom3));
        check("T arbitrary 8 goal opposite class", !solvable(goal3, custom3));
        check("T arbitrary 15 goal reachable", solvable(swapped4.next(move(Direction.LEFT)), swapped4));
        check("T arbitrary 15 goal opposite class", !solvable(goal4, swapped4));
        check("different dimensions", !solvable(goal3, goal4));
        expectIllegal("declared size mismatch", () -> new NPuzzleProblem(goal3, goal3, 4).solvable());

        System.out.println("PASS: " + checks + " N-Puzzle foundation checks");
    }

    private static PuzzleBoard board(int size, int... tiles) {
        return new PuzzleBoard(tiles, size);
    }

    private static PuzzleBoard withBlankAt(int size, int index) {
        int[] tiles = new int[size * size];
        for (int i = 0; i < tiles.length - 1; i++) tiles[i] = i + 1;
        tiles[tiles.length - 1] = tiles[index];
        tiles[index] = 0;
        return board(size, tiles);
    }

    private static PuzzleMove move(Direction direction) {
        return new PuzzleMove(direction);
    }

    private static EnumSet<Direction> directions(PuzzleBoard board) {
        EnumSet<Direction> result = EnumSet.noneOf(Direction.class);
        for (PuzzleMove action : board.actions()) result.add(action.getDirection());
        return result;
    }

    private static String draw(PuzzleBoard board) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            board.draw();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static boolean solvable(PuzzleBoard initial, PuzzleBoard goal) {
        return new NPuzzleProblem(initial, goal).solvable();
    }

    private static void check(String label, boolean result) {
        if (!result) throw new AssertionError(label);
        checks++;
    }

    private static void expectIllegal(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label + " did not reject invalid input");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }
}
