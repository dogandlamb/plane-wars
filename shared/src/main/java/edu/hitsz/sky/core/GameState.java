package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.List;

/** A detached frame. The engine owns mutable state; consumers only read its snapshots. */
public final class GameState {
    public static final float WIDTH = 480f, HEIGHT = 800f;
    public long tick;
    public int stage, score;
    public float elapsed, freezeSeconds;
    public Difficulty difficulty;
    public Mode mode;
    public boolean developer;
    public Phase phase;
    public String notice = "";
    public final List<Pilot> pilots = new ArrayList<>();
    public final List<Body> enemies = new ArrayList<>();
    public final List<Body> shots = new ArrayList<>();
    public final List<Body> pickups = new ArrayList<>();
    public final List<Body> effects = new ArrayList<>();
    public Night night;

    public static final class Pilot {
        public int id, hp, maxHp, score, weapon, bombs;
        public String name = "";
        public float x, y, invulnerable, weaponSeconds, reviveProgress, charge;
        public boolean downed;
        public final float[] cooldowns = new float[4];
    }
    /** Enemy kinds: scout, gunner, dart, ace, boss, shield, carrier, storm (0..7).
     * Pickup kinds: repair, spread, ring, bomb, freeze (0..4). Shot owner -1 means hostile.
     * Effect kinds: explosion, hit, pickup, shockwave, beam, dash (0..5). */
    public static class Body {
        public int id, kind, owner, hp, maxHp, flags;
        public float x, y, vx, vy, radius, age, angle;
    }
    public static final class Night {
        public float energy = 100f, elapsed;
        public int camera, hour;
        public boolean leftDoor, rightDoor, leftLight, rightLight, tabletOpen;
        public final float[] intruders = new float[3];
        public final int[] routes = new int[3];
    }
}
