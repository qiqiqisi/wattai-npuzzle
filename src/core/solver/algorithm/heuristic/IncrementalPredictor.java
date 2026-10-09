package core.solver.algorithm.heuristic;

import core.problem.State;

/** Optional exact update after one transition; ordinary predictors still use full evaluation. */
public interface IncrementalPredictor extends Predictor {
    int successorHeuristics(State parent, State child, State goal, int parentHeuristic);
}
