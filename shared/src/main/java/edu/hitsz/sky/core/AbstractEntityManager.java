package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns collection lifecycle; clearing is never a death or pickup operation. */
public abstract class AbstractEntityManager<T extends GameObject> implements AutoCloseable, CollisionSource {
    protected final List<T> entities;
    private final Map<Integer, T> byId = new HashMap<>();
    private final EventManager.Subscription endSubscription;
    private boolean acceptingSpawns = true;

    protected AbstractEntityManager(List<T> entities, EventManager events) {
        this.entities = Objects.requireNonNull(entities, "entities");
        endSubscription = events == null ? null : events.subscribe(BattleEndedEvent.class, event -> {
            acceptingSpawns = false;
            clearAll();
        });
    }

    public final int count() { return entities.size(); }
    public final boolean isAcceptingSpawns() { return acceptingSpawns; }

    protected final boolean register(T entity) {
        if (!acceptingSpawns) return false;
        if (entity.entityId > 0 && byId.putIfAbsent(entity.entityId, entity) != null)
            throw new IllegalArgumentException("Duplicate entity ID: " + entity.entityId);
        entities.add(entity);
        return true;
    }

    protected void release(T entity) { }

    public final boolean remove(T entity) {
        if (!entities.remove(entity)) return false;
        byId.remove(entity.entityId, entity);
        entity.active = false;
        release(entity);
        return true;
    }

    public final void clearAll() {
        for (T entity : new ArrayList<>(entities)) { entity.active = false; release(entity); }
        entities.clear();
        byId.clear();
    }

    @Override public final void collectColliders(List<CollisionBody> destination) {
        for (T value : entities) if (value instanceof Entity) {
            Entity entity = (Entity) value;
            if (entity.isCollidable()) destination.add(entity.collisionBody());
        }
    }

    final boolean collisionActive(int id) {
        T value = byId.get(id);
        return value instanceof Entity && ((Entity) value).isCollidable();
    }

    final void notifyCollision(int id, CollisionInfo information) {
        T value = byId.get(id);
        if (value instanceof CollisionReceiver) ((CollisionReceiver) value).onCollision(information);
    }

    @Override public void close() {
        acceptingSpawns = false;
        clearAll();
        if (endSubscription != null) endSubscription.close();
    }
}
