package stud.g01.visual;

import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 解路径文件的解析，和校验
 * 解路径文件的格式详见报告
 *
 * @param algorithm 产生该解的算法
 * @param heuristic 产生该解的启发函数
 * @param elapsedMs 耗时（毫秒）
 * @param generated 生成的结点数
 * @param expanded  扩展的结点数
 * @param goal      目标棋盘
 * @param states    每一帧的棋盘状态
 * @param moves     动作名
 */
public record SolutionTrace(String algorithm, String heuristic, double elapsedMs, long generated,
                            long expanded, PuzzleBoard goal, List<PuzzleBoard> states, List<String> moves) {
    /**
     * 读取并校验一个解路径文件
     */
    public static SolutionTrace read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() < 11) throw new IOException("Incomplete solution file");
        Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            String[] fields = lines.get(i).split("\t", -1);
            if (fields.length != 2 || metadata.put(fields[0], fields[1]) != null)
                throw new IOException("Invalid solution metadata");
        }
        if (!"NPUZZLE_SOLUTION_V1".equals(metadata.get("format"))
                || !"step\tmove\tstate".equals(lines.get(9))) throw new IOException("Unknown solution format");
        try {
            int size = Integer.parseInt(metadata.get("size"));
            if (size != 3 && size != 4) throw new IOException("Only 3x3 and 4x4 solutions are supported");
            PuzzleBoard goal = board(metadata.get("goal"), size);
            int declared = Integer.parseInt(metadata.get("solution_length"));
            double elapsed = Double.parseDouble(metadata.get("elapsed_ms"));
            long generated = Long.parseLong(metadata.get("generated"));
            long expanded = Long.parseLong(metadata.get("expanded"));
            if (declared < 0 || !Double.isFinite(elapsed) || elapsed < 0 || generated < 0 || expanded < 0
                    || metadata.getOrDefault("algorithm", "").isBlank()
                    || metadata.getOrDefault("heuristic", "").isBlank()) throw new IOException("Invalid metrics");
            List<PuzzleBoard> states = new ArrayList<>();
            List<String> moves = new ArrayList<>();
            for (int i = 10; i < lines.size(); i++) {
                String[] fields = lines.get(i).split("\t", -1);
                if (fields.length != 3 || Integer.parseInt(fields[0]) != states.size())
                    throw new IOException("Invalid step sequence at line " + (i + 1));
                states.add(board(fields[2], size));
                moves.add(fields[1]);
            }
            if (states.size() != declared + 1 || !moves.get(0).equals("START"))
                throw new IOException("Solution length mismatch");
            for (int i = 1; i < states.size(); i++) {
                PuzzleMove move = new PuzzleMove(PuzzleMove.Direction.valueOf(moves.get(i)));
                boolean legal = false;
                for (PuzzleMove candidate : states.get(i - 1).actions()) if (candidate.equals(move)) legal = true;
                if (!legal || !states.get(i - 1).next(move).equals(states.get(i)))
                    throw new IOException("Illegal move at step " + i);
            }
            if (!states.get(states.size() - 1).equals(goal)) throw new IOException("Last state is not goal");
            return new SolutionTrace(metadata.get("algorithm"), metadata.get("heuristic"),
                    elapsed, generated, expanded, goal, List.copyOf(states), List.copyOf(moves));
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Invalid solution contents", error);
        }
    }

    /**
     * 将数字解析为棋盘状态。
     *
     * @param text 一行数据（数字）
     * @param size 棋盘边长
     * @return 对应的棋盘对象
     * @throws IOException 数字个数与边长不符时抛出
     */
    private static PuzzleBoard board(String text, int size) throws IOException {
        String[] fields = text.trim().split("\\s+");
        if (fields.length != size * size) throw new IOException("Wrong board length");
        int[] tiles = new int[fields.length];
        for (int i = 0; i < fields.length; i++) tiles[i] = Integer.parseInt(fields[i]);
        return new PuzzleBoard(tiles, size);
    }
}
