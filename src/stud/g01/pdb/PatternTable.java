package stud.g01.pdb;

import stud.g01.problem.npuzzle.PuzzleBoard;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;

/** One additive pattern table. A move costs one only when it moves a tile in this pattern. */
public final class PatternTable {
    private static final int MAGIC = 0x50444231;
    private static final int VERSION = 1;
    private static final int COST_PARTITION = 1;
    private final int size;
    private final int cells;
    private final int[] tiles;
    private final int[] goalPositions;
    private final int states;
    private final byte[] distance;
    private final boolean loaded;
    private final long preparationMs;
    private final Path file;

    public PatternTable(PuzzleBoard goal, int[] pattern, Path file) throws IOException {
        size = goal.getSize();
        cells = size * size;
        tiles = pattern.clone();
        this.file = file;
        boolean[] seen = new boolean[cells];
        for (int tile : tiles) {
            if (tile <= 0 || tile >= cells || seen[tile]) throw new IllegalArgumentException("Invalid pattern tile");
            seen[tile] = true;
        }
        goalPositions = positions(goal);
        states = stateCount(cells, tiles.length + 1);
        long start = System.nanoTime();
        byte[] cached = load();
        loaded = cached != null;
        distance = cached != null ? cached : build();
        if (cached == null) save();
        preparationMs = (System.nanoTime() - start) / 1_000_000;
    }

    public static int stateCount(int cells, int items) {
        long count = 1;
        for (int i = 0; i < items; i++) count *= cells - i;
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Pattern table exceeds index limit");
        return (int) count;
    }

    public int rank(int[] positions) {
        if (positions.length != tiles.length + 1) throw new IllegalArgumentException("Wrong position count");
        int used = 0;
        int rank = 0;
        for (int i = 0; i < positions.length; i++) {
            int position = positions[i];
            if (position < 0 || position >= cells || (used & (1 << position)) != 0)
                throw new IllegalArgumentException("Repeated or invalid position");
            int ordinal = position - Integer.bitCount(used & ((1 << position) - 1));
            rank = rank * (cells - i) + ordinal;
            used |= 1 << position;
        }
        return rank;
    }

    public int[] unrank(int rank) {
        if (rank < 0 || rank >= states) throw new IllegalArgumentException("Rank out of range");
        int[] digits = new int[tiles.length + 1];
        for (int i = digits.length - 1; i >= 0; i--) {
            digits[i] = rank % (cells - i);
            rank /= cells - i;
        }
        int[] positions = new int[digits.length];
        int used = 0;
        for (int i = 0; i < digits.length; i++) {
            int ordinal = digits[i];
            for (int position = 0; position < cells; position++) {
                if ((used & (1 << position)) != 0) continue;
                if (ordinal-- == 0) {
                    positions[i] = position;
                    used |= 1 << position;
                    break;
                }
            }
        }
        return positions;
    }

    public int value(PuzzleBoard board) {
        if (board.getSize() != size) throw new IllegalArgumentException("Board size mismatch");
        int used = 0;
        int rank = 0;
        for (int i = 0; i <= tiles.length; i++) {
            int position = board.positionOf(i == 0 ? 0 : tiles[i - 1]);
            int ordinal = position - Integer.bitCount(used & ((1 << position) - 1));
            rank = rank * (cells - i) + ordinal;
            used |= 1 << position;
        }
        return Byte.toUnsignedInt(distance[rank]);
    }
    public int valueAt(int rank) { return Byte.toUnsignedInt(distance[rank]); }
    public int states() { return states; }
    public int[] tiles() { return tiles.clone(); }
    public boolean loaded() { return loaded; }
    public long preparationMs() { return preparationMs; }
    public Path file() { return file; }
    public long fileBytes() throws IOException { return Files.size(file); }

    /** Encodes each abstract edge as (neighborRank << 1) | additiveMoveCost. */
    public int[] neighbors(int rank) {
        int[] positions = unrank(rank);
        int blank = positions[0];
        int row = blank / size;
        int col = blank % size;
        int[] result = new int[4];
        int count = 0;
        if (row > 0) result[count++] = encodedNeighbor(positions, blank - size);
        if (row + 1 < size) result[count++] = encodedNeighbor(positions, blank + size);
        if (col > 0) result[count++] = encodedNeighbor(positions, blank - 1);
        if (col + 1 < size) result[count++] = encodedNeighbor(positions, blank + 1);
        return Arrays.copyOf(result, count);
    }

    private int encodedNeighbor(int[] positions, int nextBlank) {
        int oldBlank = positions[0];
        int member = -1;
        for (int i = 1; i < positions.length; i++) if (positions[i] == nextBlank) { member = i; break; }
        positions[0] = nextBlank;
        if (member >= 0) positions[member] = oldBlank;
        int result = rank(positions) * 2 + (member >= 0 ? 1 : 0);
        positions[0] = oldBlank;
        if (member >= 0) positions[member] = nextBlank;
        return result;
    }

    private int[] positions(PuzzleBoard board) {
        if (board.getSize() != size) throw new IllegalArgumentException("Board size mismatch");
        int[] found = new int[tiles.length + 1];
        found[0] = board.positionOf(0);
        for (int i = 0; i < tiles.length; i++) found[i + 1] = board.positionOf(tiles[i]);
        return found;
    }

    private byte[] build() {
        byte[] values = new byte[states];
        Arrays.fill(values, (byte) 0xff);
        IntDeque deque = new IntDeque(1024);
        int goalRank = rank(goalPositions);
        values[goalRank] = 0;
        deque.addFirst(goalRank);
        while (!deque.isEmpty()) {
            int current = deque.removeFirst();
            int oldDistance = Byte.toUnsignedInt(values[current]);
            int[] positions = unrank(current);
            int blank = positions[0];
            int row = blank / size;
            int col = blank % size;
            if (row > 0) relax(values, deque, positions, blank - size, oldDistance);
            if (row + 1 < size) relax(values, deque, positions, blank + size, oldDistance);
            if (col > 0) relax(values, deque, positions, blank - 1, oldDistance);
            if (col + 1 < size) relax(values, deque, positions, blank + 1, oldDistance);
        }
        for (byte value : values) if (Byte.toUnsignedInt(value) == 255)
            throw new IllegalStateException("Unreached abstract state or byte distance overflow");
        return values;
    }

    private void relax(byte[] values, IntDeque deque, int[] positions, int nextBlank, int oldDistance) {
        int oldBlank = positions[0];
        int member = -1;
        for (int i = 1; i < positions.length; i++) if (positions[i] == nextBlank) { member = i; break; }
        positions[0] = nextBlank;
        if (member >= 0) positions[member] = oldBlank;
        int next = rank(positions);
        int candidate = oldDistance + (member >= 0 ? 1 : 0);
        if (candidate >= 255) throw new IllegalStateException("PDB byte distance overflow");
        if (candidate < Byte.toUnsignedInt(values[next])) {
            values[next] = (byte) candidate;
            if (member >= 0) deque.addLast(next); else deque.addFirst(next);
        }
        positions[0] = oldBlank;
        if (member >= 0) positions[member] = nextBlank;
    }

    private byte[] load() throws IOException {
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != COST_PARTITION
                    || in.readInt() != size || in.readInt() != tiles.length) return null;
            for (int tile : tiles) if (in.readInt() != tile) return null;
            for (int position : goalPositions) if (in.readInt() != position) return null;
            if (in.readInt() != states) return null;
            long expectedCrc = Integer.toUnsignedLong(in.readInt());
            byte[] data = in.readNBytes(states);
            CRC32 crc = new CRC32();
            crc.update(data);
            if (data.length != states || crc.getValue() != expectedCrc || in.read() != -1) return null;
            return data;
        } catch (IOException invalid) {
            return null;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "pdb-", ".tmp");
        try {
            CRC32 crc = new CRC32();
            crc.update(distance);
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temp))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(COST_PARTITION);
                out.writeInt(size);
                out.writeInt(tiles.length);
                for (int tile : tiles) out.writeInt(tile);
                for (int position : goalPositions) out.writeInt(position);
                out.writeInt(states);
                out.writeInt((int) crc.getValue());
                out.write(distance);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static final class IntDeque {
        private int[] data;
        private int head;
        private int count;
        IntDeque(int capacity) { data = new int[capacity]; }
        boolean isEmpty() { return count == 0; }
        void addFirst(int value) { grow(); head = (head - 1 + data.length) % data.length; data[head] = value; count++; }
        void addLast(int value) { grow(); data[(head + count) % data.length] = value; count++; }
        int removeFirst() { int value = data[head]; head = (head + 1) % data.length; count--; return value; }
        private void grow() {
            if (count < data.length) return;
            int[] expanded = new int[data.length * 2];
            for (int i = 0; i < count; i++) expanded[i] = data[(head + i) % data.length];
            data = expanded;
            head = 0;
        }
    }
}
