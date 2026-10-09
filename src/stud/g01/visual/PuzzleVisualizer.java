package stud.g01.visual;

import stud.g01.problem.npuzzle.PuzzleBoard;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.nio.file.Path;

/** Swing playback of a replay-validated 3x3 or 4x4 solution. */
public final class PuzzleVisualizer {
    private final SolutionTrace trace;
    private final JFrame frame = new JFrame("N-Puzzle 解路径可视化");
    private final JPanel currentGrid = new JPanel();
    private final JLabel progress = new JLabel();
    private final Timer timer;
    private final PlaybackModel playback;

    private PuzzleVisualizer(SolutionTrace trace) {
        this.trace = trace;
        playback = new PlaybackModel(trace.states().size()-1);
        timer = new Timer(700, event -> advance());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));
        JPanel boards = new JPanel(new GridLayout(1, 2, 16, 0));
        boards.add(panel("当前状态", currentGrid));
        JPanel goalGrid = new JPanel();
        draw(goalGrid, trace.goal());
        boards.add(panel("目标状态", goalGrid));
        frame.add(boards, BorderLayout.CENTER);
        JLabel metrics = new JLabel(String.format("%s · %s · %d 步 · %.3f ms · generated %d · expanded %d",
                trace.algorithm(), trace.heuristic(), trace.states().size()-1, trace.elapsedMs(),
                trace.generated(), trace.expanded()), SwingConstants.CENTER);
        frame.add(metrics, BorderLayout.NORTH);
        JPanel controls = new JPanel();
        JButton play = button("Play", () -> { playback.play(); if(playback.playing()) timer.start(); });
        JButton pause = button("Pause", () -> { playback.pause(); timer.stop(); });
        JButton previous = button("Previous", () -> { timer.stop(); playback.previous(); refresh(); });
        JButton next = button("Next", () -> { timer.stop(); playback.next(); refresh(); });
        JButton restart = button("Restart", () -> { timer.stop(); playback.restart(); refresh(); });
        JComboBox<String> speed = new JComboBox<>(new String[]{"0.5x", "1x", "2x"});
        speed.setSelectedIndex(1);
        speed.addActionListener(event -> { playback.speed(speed.getSelectedIndex()); timer.setDelay(playback.delay()); });
        controls.add(play); controls.add(pause); controls.add(previous); controls.add(next);
        controls.add(restart); controls.add(new JLabel("Speed")); controls.add(speed);
        JPanel south = new JPanel(new BorderLayout());
        south.add(progress, BorderLayout.NORTH);
        south.add(controls, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);
        refresh();
        frame.pack();
        frame.setMinimumSize(new Dimension(650, 470));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JButton button(String title, Runnable action) {
        JButton button = new JButton(title);
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel panel(String title, JPanel grid) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(title, SwingConstants.CENTER), BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private void advance() {
        playback.tick();
        refresh();
        if (!playback.playing()) timer.stop();
    }

    private void refresh() {
        int step = playback.step();
        draw(currentGrid, trace.states().get(step));
        progress.setHorizontalAlignment(SwingConstants.CENTER);
        progress.setText("Step " + step + " / " + (trace.states().size()-1)
                + " · blank " + trace.moves().get(step));
    }

    private void draw(JPanel grid, PuzzleBoard board) {
        grid.removeAll();
        int size = board.getSize();
        grid.setLayout(new GridLayout(size, size, 5, 5));
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) {
            int tile = board.tileAt(row, col);
            JLabel cell = new JLabel(tile == 0 ? "" : Integer.toString(tile), SwingConstants.CENTER);
            cell.setOpaque(true);
            cell.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size == 3 ? 32 : 25));
            cell.setBackground(tile == 0 ? new Color(230, 235, 240) : new Color(50, 100, 165));
            cell.setForeground(Color.WHITE);
            cell.setPreferredSize(new Dimension(size == 3 ? 74 : 62, size == 3 ? 74 : 62));
            grid.add(cell);
        }
        grid.revalidate();
        grid.repaint();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--environment")) {
            System.out.println("HEADLESS=" + GraphicsEnvironment.isHeadless());
            return;
        }
        if (args.length == 2 && args[0].equals("--validate")) {
            SolutionTrace trace = SolutionTrace.read(Path.of(args[1]));
            System.out.println("TRACE_VALID=" + (trace.states().size() - 1));
            return;
        }
        if (args.length != 1) throw new IllegalArgumentException("Usage: PuzzleVisualizer solution.tsv");
        SolutionTrace trace = SolutionTrace.read(Path.of(args[0]));
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("HEADLESS_UNVERIFIED: no GUI display");
        SwingUtilities.invokeLater(() -> new PuzzleVisualizer(trace));
    }
}
