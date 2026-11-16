package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Per-battle, synchronous, exact-type dispatch on the simulation thread. */
public final class EventManager implements AutoCloseable {
    public interface Subscription extends AutoCloseable { @Override void close(); }
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new HashMap<>();
    private boolean closed;

    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> listener) {
        if (closed) throw new IllegalStateException("Event manager is closed");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Consumer<Object> handler = event -> listener.accept(type.cast(event));
        List<Consumer<Object>> entries = listeners.computeIfAbsent(type, key -> new ArrayList<>());
        entries.add(handler);
        return () -> entries.remove(handler);
    }

    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        if (closed) return;
        List<Consumer<Object>> entries = listeners.get(event.getClass());
        if (entries == null) return;
        RuntimeException failure = null;
        for (Consumer<Object> listener : new ArrayList<>(entries)) {
            try { listener.accept(event); }
            catch (RuntimeException problem) {
                if (failure == null) failure = problem;
                else if (failure != problem) failure.addSuppressed(problem);
            }
        }
        if (failure != null) throw failure;
    }

    @Override public void close() {
        closed = true;
        for (List<Consumer<Object>> entries : listeners.values()) entries.clear();
        listeners.clear();
    }
}
