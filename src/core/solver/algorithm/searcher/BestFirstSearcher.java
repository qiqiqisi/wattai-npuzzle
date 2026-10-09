package core.solver.algorithm.searcher;

import java.util.*;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

/**
 * Teacher-provided best-first search. Frontier ordering and Node evaluation determine
 * whether the run is A*, Dijkstra, or greedy best-first search.
 * Only damaged comments were repaired; search logic is unchanged.
 */
public final class BestFirstSearcher extends AbstractSearcher {
	private final Predictor predictor;

	/** Construct a best-first searcher with the supplied frontier and heuristic. */
	public BestFirstSearcher(Frontier frontier, Predictor predictor) {
		super(frontier);
		this.predictor = predictor;
	}

	@Override
	public Deque<Node> search(Problem problem) {
		// Reject an unsolvable input before entering the search loop.
		if (!problem.solvable()) {
			return null;
		}

		// Reset the frontier, explored set, and counters for this problem.
		frontier.clear();
		explored.clear();
		nodesExpanded = 0;
		nodesGenerated = 0;

		// Root node.
		Node root = problem.root(predictor);
		frontier.offer(root);

		// Expand nodes in frontier priority order.
		while (true) {
			if (frontier.isEmpty())
				return null;

			Node node = frontier.poll();

			if (problem.goal(node.getState())) {
				return generatePath(node);
			}

			explored.add(node.getState());

			// Generate every legal child and add it unless its state is explored.
			for (Node child : problem.childNodes(node, predictor)) {
				nodesGenerated++;
				if (!expanded(child))
					frontier.offer(child);
			}
			nodesExpanded++;
		}
	}
}
