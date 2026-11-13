package edu.hitsz.sky.net;

import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;
import java.util.Arrays;

final class RoomLifecycle {
    enum ReadyAction { NONE, BEGIN_BATTLE, SHOW_STORY }

    RoomOptions options;
    boolean storySent, running, finished;
    private final boolean[] ready = new boolean[2];
    private final boolean[] suspended = new boolean[2];
    private boolean manualPause, cleared;

    RoomLifecycle(RoomOptions options) { this.options = options; }

    boolean beginStory() {
        if (storySent) return false;
        storySent = true;
        return true;
    }

    ReadyAction ready(int member) {
        if (!storySent || (running && !finished)) return ReadyAction.NONE;
        ready[member] = true;
        if (!ready[0] || !ready[1]) return ReadyAction.NONE;
        if (finished) {
            prepareStory(cleared && options.stage < 5 ? options.stage + 1 : options.stage);
            return ReadyAction.SHOW_STORY;
        }
        running = true;
        return ReadyAction.BEGIN_BATTLE;
    }

    boolean finish(Phase first, Phase second) {
        if (finished || first == Phase.RUNNING || (options.mode == Mode.VERSUS && second == Phase.RUNNING)) return false;
        finished = true;
        cleared = first == Phase.VICTORY && (options.mode != Mode.VERSUS || second == Phase.VICTORY);
        Arrays.fill(ready, false);
        return true;
    }

    void retry() { prepareStory(options.stage); }
    void pause(boolean value) { manualPause = value; }
    void suspend(int member, boolean value) { suspended[member] = value; }
    boolean paused() { return manualPause || hasSuspendedMember(); }
    boolean hasSuspendedMember() { return suspended[0] || suspended[1]; }

    private void prepareStory(int stage) {
        options = new RoomOptions(stage, options.difficulty, options.mode, options.developer, options.seed + 1);
        Arrays.fill(ready, false);
        storySent = true; running = false; finished = false; manualPause = false; cleared = false;
    }
}
