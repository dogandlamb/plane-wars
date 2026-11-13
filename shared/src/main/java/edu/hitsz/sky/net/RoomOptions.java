package edu.hitsz.sky.net;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.Mode;

public final class RoomOptions {
    public final int stage;
    public final Difficulty difficulty;
    public final Mode mode;
    public final boolean developer;
    public final long seed;

    public RoomOptions(int stage, Difficulty difficulty, Mode mode, boolean developer, long seed) {
        if (stage < 1 || stage > 6) {
            throw new IllegalArgumentException("请选择一至六关");
        }
        if (difficulty == null || (mode != Mode.COOP && mode != Mode.VERSUS)) {
            throw new IllegalArgumentException("请选择合作或比分对战");
        }
        this.stage = stage; this.difficulty = difficulty; this.mode = mode;
        this.developer = developer; this.seed = seed;
    }
}
