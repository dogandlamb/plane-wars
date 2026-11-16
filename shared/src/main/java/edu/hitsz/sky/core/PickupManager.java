package edu.hitsz.sky.core;

import java.util.List;
import java.util.function.IntSupplier;

public final class PickupManager extends AbstractEntityManager<GameState.Body> {
    private final IntSupplier nextId;
    private final ScoreManager scores;
    private int specialKills, supplySequence;

    PickupManager(List<GameState.Body> pickups, EventManager events, IntSupplier nextId, ScoreManager scores) {
        super(pickups, events); this.nextId = nextId; this.scores = scores;
    }

    void dropFor(EnemyDeath death) {
        if (!isAcceptingSpawns() || death.kind <= 0 || death.kind == 4) return;
        if (++specialKills % 2 != 0 || count() >= 20) return;
        GameState.Body pickup = new GameState.Body();
        pickup.id = nextId.getAsInt(); pickup.kind = supplySequence++ % 5;
        pickup.x = death.x; pickup.y = death.y; pickup.radius = 16;
        register(pickup);
    }

    boolean collect(GameState.Body pickup, int heroId) {
        if (!isAcceptingSpawns() || !remove(pickup)) return false;
        scores.recordPickup(heroId);
        return true;
    }

    void update(float seconds) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameState.Body pickup = entities.get(i);
            pickup.age += seconds; pickup.y += 58 * seconds;
            if (pickup.y > 840) remove(pickup);
        }
    }
}
