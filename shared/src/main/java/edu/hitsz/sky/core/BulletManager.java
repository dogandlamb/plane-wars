package edu.hitsz.sky.core;

import java.util.List;
import java.util.function.IntSupplier;

/** Bullet cleanup for one battlefield, independent of its outcome rules. */
public final class BulletManager extends AbstractEntityManager<GameState.Body> {
    public static final int MAX_BULLETS = 512;
    private final IntSupplier nextId;

    /** Binds to the live bullet collection; does not copy or clear it. */
    public BulletManager(List<GameState.Body> bullets) {
        this(bullets, null, new IntSupplier() {
            private int id;
            @Override public int getAsInt() { return ++id; }
        });
    }

    BulletManager(List<GameState.Body> bullets, EventManager events, IntSupplier nextId) {
        super(bullets, events); this.nextId = nextId;
    }

    public GameState.Body spawn(int owner, int kind, float x, float y, float vx, float vy,
                               int damage, float radius) {
        if (!isAcceptingSpawns() || count() >= MAX_BULLETS) return null;
        GameState.Body shot = new GameState.Body();
        shot.id = nextId.getAsInt(); shot.owner = owner; shot.kind = kind;
        shot.x = x; shot.y = y; shot.vx = vx; shot.vy = vy; shot.radius = radius;
        shot.hp = damage; shot.maxHp = damage; shot.angle = (float) Math.atan2(vy, vx);
        register(shot);
        return shot;
    }
}
