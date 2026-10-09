package stud.g01.solver;

/** Optional state key for collision-checked transposition lookup. */
public interface ZobristKey {
    long zobristHash();
}
