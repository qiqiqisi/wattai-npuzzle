package stud.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import java.util.Deque;

/** Student compatibility adapter for the teacher EngineFeeder's original import. */
public final class IdAStar extends AbstractSearcher {
    private final stud.g01.solver.IdAStar delegate;
    public IdAStar(Frontier frontier) { this(frontier, (state, goal) -> 0); }
    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        delegate = new stud.g01.solver.IdAStar(frontier, predictor);
    }
    @Override public Deque<Node> search(Problem problem) {
        Deque<Node> result = delegate.search(problem);
        nodesGenerated = delegate.nodesGenerated();
        nodesExpanded = delegate.nodesExpanded();
        return result;
    }
}
