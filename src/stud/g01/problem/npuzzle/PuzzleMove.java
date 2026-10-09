package stud.g01.problem.npuzzle;

import core.problem.Action;

import java.util.Objects;

/** Direction in which the blank moves, not the numbered tile. */
public final class PuzzleMove extends Action {
    public enum Direction { UP, DOWN, LEFT, RIGHT }

    private final Direction direction;

    public PuzzleMove(Direction direction) {
        this.direction = Objects.requireNonNull(direction);
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public void draw() {
        System.out.println(this);
    }

    @Override
    public int stepCost() {
        return 1;
    }

    @Override
    public String toString() {
        return direction.name();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PuzzleMove other && direction == other.direction;
    }

    @Override
    public int hashCode() {
        return direction.hashCode();
    }
}
