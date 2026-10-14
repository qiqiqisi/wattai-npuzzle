package stud.g01.visual;

/**
 * 只处理逻辑
 */
public final class PlaybackModel {
    private final int last; // 最后一步的步号，也是解的总步数
    private int step;
    private boolean playing;
    /** 自动播放时的间隔 */
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

    /** 后退一 */
    public void previous() { pause(); step = Math.max(0, step-1); }

    /** 前进 */
    public void next() { pause(); advance(); }

    public void restart() { pause(); step = 0; }

    /** 播放状态下进入下一帧。 */
    public void tick() { if (playing) advance(); }

    /** 末步暂停。 */
    private void advance() { step = Math.min(last, step+1); if (step == last) pause(); }

    /**
     * 设置播放速度
     *
     * @param index 速度档位：0 → 0.5 倍速（1400 毫秒/帧），1 → 1 倍速（700 毫秒/帧），
     *              2 → 2 倍速（350 毫秒/帧）
     */
    public void speed(int index) {
        delay = switch(index) { case 0 -> 1400; case 1 -> 700; case 2 -> 350;
            default -> throw new IllegalArgumentException("Unknown speed"); };
    }
}
