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
        GameState.Body effect = new GameState.Body();
        effect.id = nextId.getAsInt(); effect.kind = kind; effect.owner = owner;
        effect.x = x; effect.y = y; effect.radius = radius;
        register(effect);
    }

    void update(float seconds) {
        for (int i = entities.size() - 1; i >= 0; i--) {
            GameState.Body effect = entities.get(i);
            effect.age += seconds;
            float lifetime = effect.kind == 1 ? .22f : effect.kind == 5 ? .32f : .65f;
            if (effect.kind == 4) lifetime = effect.owner < 0 ? 1.15f : .48f;
            if (effect.age >= lifetime) remove(effect);
        }
    }
}
