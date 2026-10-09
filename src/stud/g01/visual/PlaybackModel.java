package stud.g01.visual;

/** Deterministic playback state shared by Swing controls and headless tests. */
public final class PlaybackModel {
    private final int last;
    private int step;
    private boolean playing;
    private int delay = 700;
    public PlaybackModel(int lastStep) {
        if (lastStep < 0) throw new IllegalArgumentException("Negative final step");
        last = lastStep;
    }
    public int step() { return step; }
    public boolean playing() { return playing; }
    public int delay() { return delay; }
    public void play() { playing = step < last; }
    public void pause() { playing = false; }
    public void previous() { pause(); step = Math.max(0, step-1); }
    public void next() { pause(); advance(); }
    public void restart() { pause(); step = 0; }
    public void tick() { if (playing) advance(); }
    private void advance() { step = Math.min(last, step+1); if (step == last) pause(); }
    public void speed(int index) {
        delay = switch(index) { case 0 -> 1400; case 1 -> 700; case 2 -> 350;
            default -> throw new IllegalArgumentException("Unknown speed"); };
    }
}
