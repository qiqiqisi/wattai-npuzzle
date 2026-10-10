package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

import java.util.ArrayList;

public class PuzzleFeeder extends EngineFeeder {
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        ArrayList<Problem> problems = new ArrayList<>();
        for (int lineNo = 0; lineNo < problemLines.size(); lineNo++) {
            String line = problemLines.get(lineNo).trim();
            if (line.isEmpty()) continue;
            String[] cells = line.split("\\s+");
            try {
                int size = Integer.parseInt(cells[0]);
                if (size <= 0 || (long) size * size > (Integer.MAX_VALUE - 1L) / 2
                        || cells.length != 1 + 2 * size * size) {
                    throw new IllegalArgumentException("Expected size followed by two size*size boards");
                }
                int[] initial = new int[size * size];
                int[] goal = new int[size * size];
                for (int i = 0; i < initial.length; i++) {
                    initial[i] = Integer.parseInt(cells[i + 1]);
                    goal[i] = Integer.parseInt(cells[i + 1 + initial.length]);
                }
                problems.add(new NPuzzleProblem(new PuzzleBoard(initial, size),
                        new PuzzleBoard(goal, size), size));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Invalid N-Puzzle at line " + (lineNo + 1)
                        + ": " + error.getMessage(), error);
            }
        }
        return problems;
    }

    @Override
    public Frontier getFrontier(EvaluationType type) {
        if (type == null) throw new IllegalArgumentException("Evaluation type is required");
        return new PqFrontier(Node.evaluator(type));
    }

    @Override
    public Predictor getPredictor(HeuristicType type) {
        return PuzzleBoard.predictor(type);
    }
    public Predictor getPredictor(stud.g01.heuristic.PuzzleHeuristic type) {
        return PuzzleBoard.predictor(type);
    }

    public core.solver.algorithm.searcher.AbstractSearcher getAStar(stud.g01.heuristic.PuzzleHeuristic type) {
        return new core.solver.algorithm.searcher.BestFirstSearcher(
                getFrontier(EvaluationType.FULL), getPredictor(type));
    }

}
