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
        spawn(supplySequence++ % 5, death.x, death.y);
    }

    Pickup spawn(int kind, float x, float y) {
        if (!isAcceptingSpawns() || count() >= 20) return null;
        if (kind < 0 || kind > 4) throw new IllegalArgumentException("Unknown pickup kind: " + kind);
        Pickup pickup = new Pickup(this);
        pickup.id = nextId.getAsInt(); pickup.kind = kind;
        pickup.entityId = pickup.id;
        pickup.configurationTag = new String[]{"pickup.repair", "pickup.spread", "pickup.ring", "pickup.bomb", "pickup.freeze"}[pickup.kind];
        pickup.x = x; pickup.y = y; pickup.radius = 16; pickup.vy = 58;
        register(pickup);
        return pickup;
    }

    boolean collect(GameState.Body pickup, int heroId) {
        if (!isAcceptingSpawns() || !remove(pickup)) return false;
        scores.recordPickup(heroId);
        return true;
    }

    void update(float seconds) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameState.Body pickup = entities.get(i);
            pickup.age += seconds; pickup.y += pickup.vy * seconds;
            if (pickup.y > 840) remove(pickup);
        }
    }
}
