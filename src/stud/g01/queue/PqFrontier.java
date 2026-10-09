package stud.g01.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/** Priority frontier with one live node per State. */
public final class PqFrontier implements Frontier {
    private final Comparator<Node> evaluator;
    private final PriorityQueue<Node> queue;
    private final Map<State, Node> byState = new HashMap<>();

    public PqFrontier(Comparator<Node> evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.queue = new PriorityQueue<>(evaluator);
    }

    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node);
        Node oldNode = byState.get(node.getState());
        if (oldNode != null) {
            if (evaluator.compare(oldNode, node) <= 0) return false;
            queue.remove(oldNode);
        }
        queue.offer(node);
        byState.put(node.getState(), node);
        return true;
    }

    @Override
    public Node poll() {
        Node node = queue.poll();
        if (node != null) byState.remove(node.getState());
        return node;
    }

    @Override
    public boolean contains(Node node) {
        return node != null && byState.containsKey(node.getState());
    }

    @Override
    public void clear() {
        queue.clear();
        byState.clear();
    }

    @Override
    public int size() {
        return byState.size();
    }

    @Override
    public boolean isEmpty() {
        return byState.isEmpty();
    }
}
