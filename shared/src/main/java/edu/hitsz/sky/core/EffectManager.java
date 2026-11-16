package edu.hitsz.sky.core;

import java.util.List;
import java.util.function.IntSupplier;

public final class EffectManager extends AbstractEntityManager<GameState.Body> {
    private final IntSupplier nextId;
    EffectManager(List<GameState.Body> effects, EventManager events, IntSupplier nextId) {
        super(effects, events); this.nextId = nextId;
    }

    public void spawn(int kind, int owner, float x, float y, float radius) {
        if (!isAcceptingSpawns()) return;
        if (count() >= 72) remove(entities.get(0));
        GameState.Body effect = new Effect();
        effect.id = nextId.getAsInt(); effect.entityId = effect.id; effect.kind = kind; effect.owner = owner;
        effect.x = x; effect.y = y; effect.radius = radius;
        register(effect);
    }

}
