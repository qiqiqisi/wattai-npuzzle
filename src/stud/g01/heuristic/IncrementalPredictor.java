package stud.g01.heuristic;
import core.solver.algorithm.heuristic.Predictor;

import core.problem.State;

/** Optional exact update after one transition; ordinary predictors still use full evaluation. */
public interface IncrementalPredictor extends Predictor {
    int successorHeuristics(State parent, State child, State goal, int parentHeuristic);
}
