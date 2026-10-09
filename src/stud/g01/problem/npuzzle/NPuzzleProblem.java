package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.Deque;

public class NPuzzleProblem extends Problem {
    /** Immutable goal exposed to student-side search optimizations. */
    public PuzzleBoard targetBoard() { return (PuzzleBoard) goal; }
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal);
    }

    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    @Override
    public boolean solvable() {
        if (!(initialState instanceof PuzzleBoard initial) || !(goal instanceof PuzzleBoard target)) {
            throw new IllegalArgumentException("NPuzzleProblem requires PuzzleBoard states");
        }
        if (initial.getSize() != target.getSize()) return false;
        if (size > 0 && size != initial.getSize()) {
            throw new IllegalArgumentException("Problem size does not match board size");
        }

        int initialParity = initial.inversionParity();
        int goalParity = target.inversionParity();
        if (initial.getSize() % 2 == 0) {
            initialParity = (initialParity + initial.rowFromBottom()) % 2;
            goalParity = (goalParity + target.rowFromBottom()) % 2;
        }
        return initialParity == goalParity;
    }

    @Override
    public int stepCost(State state, Action action) {
        if (!(state instanceof PuzzleBoard) || !(action instanceof PuzzleMove)) {
            throw new IllegalArgumentException("Expected a PuzzleBoard and a PuzzleMove");
        }
        return 1;
    }

    @Override
    public boolean applicable(State state, Action action) {
        if (!(state instanceof PuzzleBoard board) || !(action instanceof PuzzleMove)) return false;
        for (PuzzleMove move : board.actions()) {
            if (move.equals(action)) return true;
        }
        return false;
    }

    @Override
    public void showSolution(Deque<Node> path) {
        System.out.println("Initial state:");
        initialState.draw();
        int step = 0;
        for (Node node : path) {
            System.out.println("Step " + ++step + ": blank " + node.getAction());
            node.getState().draw();
        }
        System.out.println("Goal state:");
        goal.draw();
        System.out.println("Total steps: " + step);
    }
}
