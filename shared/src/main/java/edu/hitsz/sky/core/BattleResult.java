package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached final values, assembled by the framework before entity cleanup. */
public final class BattleResult {
    public enum Reason { BOSS_DEFEATED, ALL_HEROES_DOWN }
    public final Phase outcome;
    public final Reason reason;
    public final int stage, score;
    public final float elapsed;
    public final Difficulty difficulty;
    public final Mode mode;
    public final boolean developer;
    public final List<HeroResult> heroes;

    BattleResult(GameState state, Phase outcome, Reason reason, int score, List<HeroResult> heroes) {
        this.outcome = outcome; this.reason = reason; this.score = score;
        stage = state.stage; elapsed = state.elapsed; difficulty = state.difficulty;
        mode = state.mode; developer = state.developer;
        this.heroes = Collections.unmodifiableList(new ArrayList<>(heroes));
    }

    public static final class HeroResult {
        public final int id, score;
        public final String name;
        HeroResult(int id, String name, int score) { this.id = id; this.name = name; this.score = score; }
    }
}
