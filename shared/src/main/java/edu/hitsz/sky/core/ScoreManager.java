package edu.hitsz.sky.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Single score writer. Hero IDs are values, never references to hero entities. */
public final class ScoreManager {
    private static final int[] KILL_POINTS = {10, 20, 30, 40, 350, 35, 50, 45};
    private final Map<Integer, Integer> personal = new HashMap<>();
    private final int stage;
    private int total;
    private boolean frozen;

    ScoreManager(int stage, List<HeroManager.Identity> heroes) {
        this.stage = stage;
        for (HeroManager.Identity hero : heroes) personal.put(hero.id, 0);
    }

    public int getTotalScore() { return total; }
    public int getHeroScore(int heroId) { return personal.getOrDefault(heroId, 0); }

    void recordEnemyKill(EnemyDeath death) {
        int points = KILL_POINTS[death.kind] + (death.kind == 4 ? stage * 100 : 0);
        credit(death.creditedHeroId, points);
    }

    void recordPickup(int heroId) { credit(heroId, 10); }
    void freeze() { frozen = true; }

    private void credit(int heroId, int amount) {
        if (frozen) return;
        if (!personal.containsKey(heroId)) throw new IllegalArgumentException("Unknown score owner: " + heroId);
        total += amount;
        personal.put(heroId, personal.get(heroId) + amount);
    }
}
