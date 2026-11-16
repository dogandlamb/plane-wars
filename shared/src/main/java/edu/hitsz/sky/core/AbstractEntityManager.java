package edu.hitsz.sky.core;

import java.util.List;
import java.util.Objects;

/** Owns collection lifecycle; clearing is never a death or pickup operation. */
public abstract class AbstractEntityManager<T> implements AutoCloseable {
    protected final List<T> entities;
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
        entities.add(entity);
        return true;
    }

    protected void release(T entity) { }

    public final boolean remove(T entity) {
        if (!entities.remove(entity)) return false;
        release(entity);
        return true;
    }

    public final void clearAll() {
        for (T entity : entities) release(entity);
        entities.clear();
    }

    @Override public void close() {
        acceptingSpawns = false;
        clearAll();
        if (endSubscription != null) endSubscription.close();
    }
}
