package edu.hitsz.sky.core;

import java.util.List;
import java.util.function.IntSupplier;

/** Bullet cleanup for one battlefield, independent of its outcome rules. */
public final class BulletManager extends AbstractEntityManager<GameState.Body> {
    public static final int MAX_BULLETS = 512;
    private final IntSupplier nextId;
    private final BattleConfig config;

    /** Binds to the live bullet collection; does not copy or clear it. */
    public BulletManager(List<GameState.Body> bullets) {
        this(bullets, null, new IntSupplier() {
            private int id;
            @Override public int getAsInt() { return ++id; }
        });
    }

    BulletManager(List<GameState.Body> bullets, EventManager events, IntSupplier nextId) {
        this(bullets, events, nextId, BattleConfig.loadDefault());
    }
    BulletManager(List<GameState.Body> bullets, EventManager events, IntSupplier nextId, BattleConfig config) {
        super(bullets, events); this.nextId = nextId; this.config = config;
    }

    public GameState.Body spawn(int owner, int kind, float x, float y, float vx, float vy,
                               int damage, float radius) {
        if (!isAcceptingSpawns() || count() >= MAX_BULLETS) return null;
        return spawn(owner, kind, x, y, vx, vy, config.legacyBulletTag(owner, damage), radius);
    }

    GameState.Body spawn(int owner, int kind, float x, float y, float vx, float vy, String tag, float radius) {
        if (!isAcceptingSpawns() || count() >= MAX_BULLETS) return null;
        Bullet shot = new Bullet(this);
        shot.id = nextId.getAsInt(); shot.owner = owner; shot.kind = kind;
        shot.entityId = shot.id; shot.configurationTag = tag;
        shot.x = x; shot.y = y; shot.vx = vx; shot.vy = vy; shot.radius = radius;
        // hp is retained only for the existing frame/wire shape, never read for collision damage.
        shot.hp = config.damage(tag); shot.maxHp = shot.hp; shot.angle = (float) Math.atan2(vy, vx);
        register(shot);
        return shot;
    }
}
