package stud.g01.pdb;

import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** Distance of the blank alone; a weak but admissible and consistent student-side heuristic. */
public final class BlankDistance implements Predictor {
    @Override
    public int heuristics(State state, State goal) {
        PuzzleBoard current = (PuzzleBoard) state;
        PuzzleBoard target = (PuzzleBoard) goal;
        if (current.getSize() != target.getSize()) throw new IllegalArgumentException("Board size mismatch");
        int size = current.getSize();
        int currentBlank = -1, targetBlank = -1;
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) {
            if (current.tileAt(row, col) == 0) currentBlank = row * size + col;
            if (target.tileAt(row, col) == 0) targetBlank = row * size + col;
        }
        return Math.abs(currentBlank / size - targetBlank / size)
                + Math.abs(currentBlank % size - targetBlank % size);
    }
}
