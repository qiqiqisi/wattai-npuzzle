package stud.g01.solver;

import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.heuristic.IncrementalPredictor;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Threshold bounded depth first search. The inherited frontier is retained for framework compatibility. */
public final class IdAStar extends AbstractSearcher {
    public enum Mode { BASIC, TT, ZOBRIST }

    private static final long FOUND = -1;
    // A full table may be abandoned without affecting search completeness or optimality.
    public static final int DEFAULT_TRANSPOSITION_LIMIT = 1_000_000;
    private final Predictor predictor;
    private final Mode mode;
    private final int transpositionLimit;
    private final Set<State> pathStates = new HashSet<>();
    private final Map<State, Integer> bestG = new HashMap<>();
    private final Map<Long, List<Entry>> zobristTable = new HashMap<>();
    private final List<Long> thresholdSequence = new ArrayList<>();
    private Node solution;
    private int transpositionEntries;
    private int iterations;
    private long initialBound;
    private long finalBound;
    private long cyclePrunes;
    private long transpositionPrunes;
    private int maxDepth;
    private int peakPathStates;
    private int peakTranspositionEntries;
    private long reverseMovePrunes;
    private long childStatesCreated;
    private long childNodesCreated;
    private long fullHeuristicEvaluations;
    private long incrementalHeuristicEvaluations;
    private Consumer<IdAStar> progressListener;
    private long lastProgress;
    // Opt-in until paired evidence is collected; false restores original child generation.
    private final boolean fastPuzzle = Boolean.parseBoolean(System.getProperty("wattai.fast", "false"));

    /** Called on the search thread, so all snapshot counters describe the same instant. */
    public void progressListener(Consumer<IdAStar> listener) { progressListener = listener; }
    private void progress(boolean force) {
        if (progressListener == null) return;
        long now = System.nanoTime();
        if (force || now - lastProgress >= 500_000_000L) {
            lastProgress = now;
            progressListener.accept(this);
        }
    }
    public int transpositionEntries() { return transpositionEntries; }

    private static final class Entry {
        final State state;
        int g;
        Entry(State state, int g) { this.state = state; this.g = g; }
    }

    /** Older framework calls remain valid and perform uninformed threshold search. */
    public IdAStar(Frontier frontier) {
        this(frontier, (state, goal) -> 0, Mode.BASIC);
    }

    public IdAStar(Frontier frontier, Predictor predictor) {
        this(frontier, predictor, Mode.ZOBRIST);
    }

    public IdAStar(Frontier frontier, Predictor predictor, Mode mode) {
        this(frontier, predictor, mode, DEFAULT_TRANSPOSITION_LIMIT);
    }

    public IdAStar(Frontier frontier, Predictor predictor, Mode mode, int transpositionLimit) {
        super(Objects.requireNonNull(frontier));
        this.predictor = Objects.requireNonNull(predictor);
        this.mode = Objects.requireNonNull(mode);
        if (transpositionLimit < 0) throw new IllegalArgumentException("Negative transposition limit");
        this.transpositionLimit = transpositionLimit;
    }

    @Override
    public Deque<Node> search(Problem problem) {
        Objects.requireNonNull(problem);
        frontier.clear();
        explored.clear(); // Never a cross-iteration closed set.
        nodesGenerated = nodesExpanded = 0;
        iterations = maxDepth = peakPathStates = peakTranspositionEntries = transpositionEntries = 0;
        initialBound = finalBound = cyclePrunes = transpositionPrunes = 0;
        reverseMovePrunes = childStatesCreated = childNodesCreated = 0;
        fullHeuristicEvaluations = incrementalHeuristicEvaluations = 0;
        solution = null;
        pathStates.clear();
        bestG.clear();
        zobristTable.clear();
        thresholdSequence.clear();
        if (!problem.solvable()) return null;

        fullHeuristicEvaluations++;
        Node root = problem.root(predictor);
        long bound = root.getHeuristic();
        initialBound = bound;
        while (true) {
            iterations++;
            finalBound = bound;
            thresholdSequence.add(bound);
            // A best-g entry is valid only for the threshold in which it was expanded.
            bestG.clear();
            zobristTable.clear();
            transpositionEntries = 0;
            pathStates.clear();
            pathStates.add(root.getState());
            peakPathStates = Math.max(peakPathStates, 1);
            progress(true);
            long next = depthFirstSearch(problem, root, bound, 0);
            pathStates.clear();
            if (next == FOUND) { progress(true); return generatePath(solution); }
            if (next == Long.MAX_VALUE) return null;
            bound = next;
        }
    }

    private long depthFirstSearch(Problem problem, Node node, long bound, int depth) {
        long f = (long) node.getPathCost() + node.getHeuristic();
        if (f > bound) return f;
        if (problem.goal(node.getState())) {
            solution = node;
            return FOUND;
        }
        if (dominated(node)) {
            transpositionPrunes++;
            return Long.MAX_VALUE;
        }
        nodesExpanded++;
        if ((nodesExpanded & 16383) == 0) progress(false);
        maxDepth = Math.max(maxDepth, depth);
        long nextBound = Long.MAX_VALUE;
        if (fastPuzzle && problem.getClass() == NPuzzleProblem.class
                && problem instanceof NPuzzleProblem puzzle && node.getState() instanceof PuzzleBoard board) {
            PuzzleBoard parent = node.getParent() == null ? null : (PuzzleBoard) node.getParent().getState();
            for (PuzzleMove move : board.actions()) {
                if (parent != null && reverses((PuzzleMove) node.getAction(), move)) {
                    cyclePrunes++;
                    reverseMovePrunes++;
                    continue;
                }
                PuzzleBoard state = board.next(move);
                childStatesCreated++;
                nodesGenerated++;
                if (pathStates.contains(state)) { cyclePrunes++; continue; }
                int h;
                if (predictor instanceof IncrementalPredictor incremental) {
                    incrementalHeuristicEvaluations++;
                    h = incremental.successorHeuristics(board, state, puzzle.targetBoard(), node.getHeuristic());
                } else {
                    fullHeuristicEvaluations++;
                    h = predictor.heuristics(state, puzzle.targetBoard());
                }
                Node child = new Node(state, node, move, node.getPathCost()+1, h);
                childNodesCreated++;
                pathStates.add(state);
                peakPathStates = Math.max(peakPathStates, pathStates.size());
                long result = depthFirstSearch(problem, child, bound, depth+1);
                pathStates.remove(state);
                if (result == FOUND) return FOUND;
                nextBound = Math.min(nextBound, result);
            }
            return nextBound;
        }
        // The framework eagerly creates every sibling, even when an earlier one finds the goal.
        List<Node> children = problem.childNodes(node, predictor);
        childStatesCreated += children.size();
        childNodesCreated += children.size();
        fullHeuristicEvaluations += children.size();
        for (Node child : children) {
            nodesGenerated++;
            State state = child.getState();
            if (pathStates.contains(state)) {
                cyclePrunes++;
                continue;
            }
            pathStates.add(state);
            peakPathStates = Math.max(peakPathStates, pathStates.size());
            long result = depthFirstSearch(problem, child, bound, depth + 1);
            pathStates.remove(state);
            if (result == FOUND) return FOUND;
            nextBound = Math.min(nextBound, result);
        }
        return nextBound;
    }

    private static boolean reverses(PuzzleMove previous, PuzzleMove candidate) {
        return switch (previous.getDirection()) {
            case UP -> candidate.getDirection() == PuzzleMove.Direction.DOWN;
            case DOWN -> candidate.getDirection() == PuzzleMove.Direction.UP;
            case LEFT -> candidate.getDirection() == PuzzleMove.Direction.RIGHT;
            case RIGHT -> candidate.getDirection() == PuzzleMove.Direction.LEFT;
        };
    }

    private boolean dominated(Node node) {
        if (mode == Mode.BASIC) return false;
        State state = node.getState();
        int g = node.getPathCost();
        if (mode == Mode.TT) {
            Integer known = bestG.get(state);
            if (known != null && g >= known) return true;
            if (known != null || transpositionEntries < transpositionLimit) {
                bestG.put(state, g);
                if (known == null) transpositionEntries++;
            }
        } else {
            long hash = state instanceof ZobristKey key ? key.zobristHash() : state.hashCode();
            List<Entry> bucket = zobristTable.get(hash);
            if (bucket != null) for (Entry entry : bucket) {
                // The 64-bit value selects a bucket; equality alone authorizes a prune.
                if (entry.state.equals(state)) {
                    if (g >= entry.g) return true;
                    entry.g = g;
                    return false;
                }
            }
            if (transpositionEntries < transpositionLimit) {
                if (bucket == null) {
                    bucket = new ArrayList<>(1);
                    zobristTable.put(hash, bucket);
                }
                bucket.add(new Entry(state, g));
                transpositionEntries++;
            }
        }
        peakTranspositionEntries = Math.max(peakTranspositionEntries, transpositionEntries);
        return false;
    }

    public Mode mode() { return mode; }
    public long reverseMovePrunes() { return reverseMovePrunes; }
    public long childStatesCreated() { return childStatesCreated; }
    public long childNodesCreated() { return childNodesCreated; }
    public long fullHeuristicEvaluations() { return fullHeuristicEvaluations; }
    public long incrementalHeuristicEvaluations() { return incrementalHeuristicEvaluations; }
    public int transpositionLimit() { return transpositionLimit; }
    public int iterations() { return iterations; }
    public long initialBound() { return initialBound; }
    public long finalBound() { return finalBound; }
    public List<Long> thresholdSequence() { return List.copyOf(thresholdSequence); }
    public long cyclePrunes() { return cyclePrunes; }
    public long transpositionPrunes() { return transpositionPrunes; }
    public int maxDepth() { return maxDepth; }
    public int peakPathStates() { return peakPathStates; }
    public int peakTranspositionEntries() { return peakTranspositionEntries; }
}
