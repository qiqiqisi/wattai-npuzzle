package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.heuristic.IncrementalPredictor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import stud.g01.solver.ZobristKey;
import stud.g01.pdb.DisjointPatternDatabase;

/** A row-major, immutable N x N board; 0 is the blank. */
public final class PuzzleBoard extends State implements ZobristKey {
    public static final long ZOBRIST_SEED = 0x5A17_2026_0930_4B1DL;
    private static final ConcurrentHashMap<Integer, long[][]> ZOBRIST_TABLES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, int[]> HASH_WEIGHTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, List<List<PuzzleMove>>> ACTIONS = new ConcurrentHashMap<>();
    private final int size;
    private final int[] tiles;
    private final int blankIndex;
    private final long zobristHash;
    private final int cachedHashCode;
    private volatile int[] tilePositions;

    public PuzzleBoard(int[] tiles, int size) {
        if (tiles == null || size <= 0 || (long) size * size != tiles.length) {
            throw new IllegalArgumentException("Expected an N x N tile array");
        }
        this.size = size;
        this.tiles = tiles.clone();
        boolean[] seen = new boolean[tiles.length];
        int blank = -1;
        for (int i = 0; i < this.tiles.length; i++) {
            int tile = this.tiles[i];
            if (tile < 0 || tile >= seen.length || seen[tile]) {
                throw new IllegalArgumentException("Tiles must be a permutation of 0..N*N-1");
            }
            seen[tile] = true;
            if (tile == 0) blank = i;
        }
        this.blankIndex = blank;
        this.zobristHash = computeZobrist(this.tiles, size);
        this.cachedHashCode = 31 * size + Arrays.hashCode(this.tiles);
    }

    private PuzzleBoard(int[] tiles, int size, int blankIndex, long zobristHash, int cachedHashCode) {
        this.tiles = tiles;
        this.size = size;
        this.blankIndex = blankIndex;
        this.zobristHash = zobristHash;
        this.cachedHashCode = cachedHashCode;
    }

    public int getSize() {
        return size;
    }

    public int tileAt(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IndexOutOfBoundsException("Tile coordinates outside the board");
        }
        return tiles[row * size + col];
    }

    /** Position of a tile, including zero; cached per immutable board for PDB lookup. */
    public int positionOf(int tile) {
        if (tile < 0 || tile >= tiles.length) throw new IllegalArgumentException("Tile outside board");
        return positions()[tile];
    }

    private static final EnumMap<HeuristicType, Predictor> predictors =
            new EnumMap<>(HeuristicType.class);

    static {
        predictors.put(HeuristicType.MISPLACED, new PuzzlePredictor(HeuristicType.MISPLACED));
        predictors.put(HeuristicType.MANHATTAN, new PuzzlePredictor(HeuristicType.MANHATTAN));
        predictors.put(HeuristicType.MANHATTAN_LC, new PuzzlePredictor(HeuristicType.MANHATTAN_LC));
        predictors.put(HeuristicType.DISJOINT_PATTERN, new DisjointPatternDatabase.LazyPredictor());
    }

    private record PuzzlePredictor(HeuristicType type) implements IncrementalPredictor {
        @Override
        public int heuristics(State state, State goal) {
            PuzzleBoard board = (PuzzleBoard) state;
            PuzzleBoard target = (PuzzleBoard) goal;
            return switch (type) {
                case MANHATTAN -> board.manhattan(target);
                case MANHATTAN_LC -> board.manhattan(target) + board.linearConflict(target);
                case MISPLACED -> board.misplaced(target);
                default -> throw new IllegalArgumentException("Unsupported N-Puzzle heuristic: " + type);
            };
        }

        @Override
        public int successorHeuristics(State parent, State child, State goal, int parentHeuristic) {
            PuzzleBoard before = (PuzzleBoard) parent;
            PuzzleBoard after = (PuzzleBoard) child;
            PuzzleBoard target = (PuzzleBoard) goal;
            before.requireSameSize(target);
            if (type == HeuristicType.MANHATTAN_LC) return after.manhattan(target) + after.linearConflict(target);
            int oldPosition = after.blankIndex;
            int newPosition = before.blankIndex;
            int movedTile = before.tiles[oldPosition];
            if (type == HeuristicType.MISPLACED) {
                int oldContribution = target.tiles[oldPosition] == movedTile ? 0 : 1;
                int newContribution = target.tiles[newPosition] == movedTile ? 0 : 1;
                return parentHeuristic - oldContribution + newContribution;
            }
            int goalPosition = target.positions()[movedTile];
            int oldDistance = Math.abs(oldPosition / before.size - goalPosition / before.size)
                    + Math.abs(oldPosition % before.size - goalPosition % before.size);
            int newDistance = Math.abs(newPosition / before.size - goalPosition / before.size)
                    + Math.abs(newPosition % before.size - goalPosition % before.size);
            return parentHeuristic - oldDistance + newDistance;
        }
    }

    public static Predictor predictor(HeuristicType type) {
        Predictor predictor = predictors.get(type);
        if (predictor == null) {
            throw new IllegalArgumentException("Unsupported N-Puzzle heuristic: " + type);
        }
        return predictor;
    }

    private void requireSameSize(PuzzleBoard goal) {
        if (size != goal.size) {
            throw new IllegalArgumentException("Current and goal boards must have the same size");
        }
    }

    private int misplaced(PuzzleBoard goal) {
        requireSameSize(goal);
        int count = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] != 0 && tiles[i] != goal.tiles[i]) count++;
        }
        return count;
    }

    private int manhattan(PuzzleBoard goal) {
        requireSameSize(goal);
        int distance = 0;
        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            if (tile == 0) continue;
            int target = goal.positions()[tile];
            distance += Math.abs(i / size - target / size)
                    + Math.abs(i % size - target % size);
        }
        return distance;
    }

    private int linearConflict(PuzzleBoard goal) {
        requireSameSize(goal);
        int extra = 0;
        int[] targetPositions = goal.positions();
        for (int row = 0; row < size; row++) {
            int[] targets = new int[size];
            int count = 0;
            for (int col = 0; col < size; col++) {
                int tile = tiles[row * size + col];
                if (tile != 0 && targetPositions[tile] / size == row)
                    targets[count++] = targetPositions[tile] % size;
            }
            extra += 2 * tilesThatMustLeaveLine(targets, count);
        }
        for (int col = 0; col < size; col++) {
            int[] targets = new int[size];
            int count = 0;
            for (int row = 0; row < size; row++) {
                int tile = tiles[row * size + col];
                if (tile != 0 && targetPositions[tile] % size == col)
                    targets[count++] = targetPositions[tile] / size;
            }
            extra += 2 * tilesThatMustLeaveLine(targets, count);
        }
        return extra;
    }

    // Ordered tiles can stay in their goal line; all others must leave and return (two extra moves).
    private static int tilesThatMustLeaveLine(int[] targets, int count) {
        int[] lis = new int[count];
        int longest = 0;
        for (int i = 0; i < count; i++) {
            lis[i] = 1;
            for (int j = 0; j < i; j++) {
                if (targets[j] < targets[i]) lis[i] = Math.max(lis[i], lis[j] + 1);
            }
            longest = Math.max(longest, lis[i]);
        }
        return count - longest;
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    @Override
    public PuzzleBoard next(Action action) {
        if (!(action instanceof PuzzleMove move)) {
            throw new IllegalArgumentException("Expected a PuzzleMove");
        }
        int row = blankIndex / size;
        int col = blankIndex % size;
        int target = switch (move.getDirection()) {
            case UP -> row > 0 ? blankIndex - size : -1;
            case DOWN -> row < size - 1 ? blankIndex + size : -1;
            case LEFT -> col > 0 ? blankIndex - 1 : -1;
            case RIGHT -> col < size - 1 ? blankIndex + 1 : -1;
        };
        if (target < 0) {
            throw new IllegalArgumentException("Blank cannot move " + move.getDirection());
        }
        int movedTile = tiles[target];
        long[][] table = zobristTable(size);
        long nextHash = zobristHash ^ table[blankIndex][0] ^ table[target][movedTile]
                ^ table[blankIndex][movedTile] ^ table[target][0];
        int[] weights = hashWeights(size);
        int nextHashCode = cachedHashCode + movedTile * (weights[blankIndex] - weights[target]);
        int[] nextTiles = tiles.clone();
        nextTiles[blankIndex] = nextTiles[target];
        nextTiles[target] = 0;
        PuzzleBoard successor = new PuzzleBoard(nextTiles, size, target, nextHash, nextHashCode);
        int[] knownPositions = tilePositions;
        if (knownPositions != null) {
            int[] successorPositions = knownPositions.clone();
            successorPositions[0] = target;
            successorPositions[movedTile] = blankIndex;
            successor.tilePositions = successorPositions;
        }
        return successor;
    }

    @Override
    public long zobristHash() {
        return zobristHash;
    }

    private static long computeZobrist(int[] tiles, int size) {
        long[][] table = zobristTable(size);
        long hash = 0;
        for (int position = 0; position < tiles.length; position++) hash ^= table[position][tiles[position]];
        return hash;
    }

    private static int[] positions(int[] tiles) {
        int[] positions = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) positions[tiles[i]] = i;
        return positions;
    }

    private int[] positions() {
        int[] found = tilePositions;
        if (found == null) {
            found = positions(tiles);
            tilePositions = found;
        }
        return found;
    }

    private static int[] hashWeights(int size) {
        return HASH_WEIGHTS.computeIfAbsent(size, n -> {
            int[] weights = new int[n * n];
            weights[weights.length - 1] = 1;
            for (int i = weights.length - 2; i >= 0; i--) weights[i] = 31 * weights[i + 1];
            return weights;
        });
    }

    private static long[][] zobristTable(int size) {
        return ZOBRIST_TABLES.computeIfAbsent(size, n -> {
            int count = n * n;
            long[][] table = new long[count][count];
            SplittableRandom random = new SplittableRandom(ZOBRIST_SEED ^ n);
            for (int position = 0; position < count; position++) {
                for (int tile = 0; tile < count; tile++) table[position][tile] = random.nextLong();
            }
            return table;
        });
    }

    @Override
    public Iterable<PuzzleMove> actions() {
        return ACTIONS.computeIfAbsent(size, PuzzleBoard::legalActions).get(blankIndex);
    }

    private static List<List<PuzzleMove>> legalActions(int size) {
        List<List<PuzzleMove>> byBlank = new ArrayList<>(size * size);
        for (int blank = 0; blank < size * size; blank++) {
            List<PuzzleMove> moves = new ArrayList<>(4);
            int row = blank / size;
            int col = blank % size;
            if (row > 0) moves.add(new PuzzleMove(PuzzleMove.Direction.UP));
            if (row < size - 1) moves.add(new PuzzleMove(PuzzleMove.Direction.DOWN));
            if (col > 0) moves.add(new PuzzleMove(PuzzleMove.Direction.LEFT));
            if (col < size - 1) moves.add(new PuzzleMove(PuzzleMove.Direction.RIGHT));
            byBlank.add(List.copyOf(moves));
        }
        return List.copyOf(byBlank);
    }

    // Only parity is needed by NPuzzleProblem; no mutable array leaves the board.
    int inversionParity() {
        int parity = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == 0) continue;
            for (int j = i + 1; j < tiles.length; j++) {
                if (tiles[j] != 0 && tiles[i] > tiles[j]) parity ^= 1;
            }
        }
        return parity;
    }

    int rowFromBottom() {
        return size - blankIndex / size; // bottom row is 1
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof PuzzleBoard other
                && size == other.size && Arrays.equals(tiles, other.tiles);
    }

    @Override
    public int hashCode() {
        return cachedHashCode;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) text.append(i % size == 0 ? '\n' : ' ');
            text.append(tiles[i] == 0 ? "#" : tiles[i]);
        }
        return text.toString();
    }
}
