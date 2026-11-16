package edu.hitsz.sky.core;

import java.util.List;
import java.util.function.IntSupplier;

/** Coordinates death consequences; normal enemies do not broadcast events. */
public final class EnemyManager extends AbstractEntityManager<GameState.Body> {
    public static final int MAX_ENEMIES = 24;
    private final EventManager events;
    private final IntSupplier nextId;
    private final ScoreManager scores;
    private final PickupManager pickups;
    private final EffectManager effects;
    private final EventManager.Subscription bossSubscription;

    EnemyManager(List<GameState.Body> enemies, EventManager events, IntSupplier nextId,
                 ScoreManager scores, PickupManager pickups, EffectManager effects) {
        super(enemies, events); this.events = events; this.nextId = nextId;
        this.scores = scores; this.pickups = pickups; this.effects = effects;
        bossSubscription = events.subscribe(BossDiedEvent.class, this::onEnemyDied);
    }

    Enemy spawn(int kind, float x, float y, float radius) {
        if (!isAcceptingSpawns() || count() >= MAX_ENEMIES) return null;
        Enemy enemy = kind == 4 ? new Boss(this, events) : new Enemy(this);
        enemy.id = nextId.getAsInt(); enemy.kind = kind; enemy.owner = -1;
        enemy.x = x; enemy.y = y; enemy.radius = radius;
        register(enemy);
        return enemy;
    }

    void defeat(GameState.Body enemy, int creditedHeroId) {
        if (isAcceptingSpawns() && entities.contains(enemy)) ((Enemy) enemy).die(creditedHeroId);
    }

    void onEnemyDied(EnemyDeath death) {
        GameState.Body found = null;
        for (GameState.Body enemy : entities) if (enemy.id == death.enemyId) { found = enemy; break; }
        if (found == null || !((Enemy) found).isDead()) return;
        remove(found);
        scores.recordEnemyKill(death);
        effects.spawn(0, death.creditedHeroId, death.x, death.y, death.radius * 1.6f);
        pickups.dropFor(death);
    }

    @Override public void close() { bossSubscription.close(); super.close(); }
}
