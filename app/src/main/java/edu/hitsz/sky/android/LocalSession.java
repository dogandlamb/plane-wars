package edu.hitsz.sky.android;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import edu.hitsz.sky.core.BattleEngine;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.InputCommand;
import edu.hitsz.sky.core.Phase;

final class LocalSession implements AutoCloseable {
    interface Listener { void onFrame(GameState frame); }
    private static final long STEP_NANOS = 16_666_667L;
    private final BattleEngine engine;
    private final Listener listener;
    private final ConcurrentLinkedQueue<InputCommand> actions = new ConcurrentLinkedQueue<>();
    private final AtomicReference<InputCommand> movement = new AtomicReference<>();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "solo-clock"); thread.setDaemon(true); return thread;
    });
    private volatile boolean paused, closed;
    private long lastNanos, accumulated;
    private ScheduledFuture<?> pending;
    private boolean started;

    LocalSession(BattleEngine engine, Listener listener) {
        this.engine = engine; this.listener = listener;
    }
    synchronized void start() {
        if(started || closed)return;
        started=true;
        lastNanos = System.nanoTime();
        listener.onFrame(engine.snapshot());
        pending=clock.schedule(this::advance, 0, TimeUnit.NANOSECONDS);
    }
    void send(InputCommand input) {
        if (closed || paused) return;
        if (input.type == InputCommand.Type.MOVE) movement.set(input);
        else if (actions.size() < 24) actions.offer(input);
    }
    synchronized void pause() {
        paused = true; movement.set(null); actions.clear();
        if(pending!=null)pending.cancel(false);
        pending=null;
    }
    synchronized void resume() {
        if(closed || !paused || !started)return;
        paused=false;lastNanos=System.nanoTime();accumulated=0;
        pending=clock.schedule(this::advance,0,TimeUnit.NANOSECONDS);
    }
    private synchronized void advance() {
        pending=null;
        if (closed || paused) return;
        long now = System.nanoTime();
        long delta = Math.min(now - lastNanos, STEP_NANOS * 6);
        lastNanos = now;
        accumulated += delta;
        boolean advanced = false;
        while (accumulated >= STEP_NANOS && !closed) {
            InputCommand move = movement.getAndSet(null);
            if (move != null) engine.accept(move);
            InputCommand action;
            while ((action = actions.poll()) != null) engine.accept(action);
            engine.step(1f / 60f);
            accumulated -= STEP_NANOS;
            advanced = true;
        }
        if (!advanced) { scheduleNext(now); return; }
        GameState frame = engine.snapshot();
        listener.onFrame(frame);
        if (frame.phase != Phase.RUNNING) close();
        else scheduleNext(now);
    }
    private void scheduleNext(long started) {
        if (closed || paused) return;
        long delay = Math.max(1_000_000L, STEP_NANOS - accumulated - (System.nanoTime() - started));
        try { pending=clock.schedule(this::advance, delay, TimeUnit.NANOSECONDS); }
        catch (java.util.concurrent.RejectedExecutionException closedDuringSchedule) {
            if (!closed) throw closedDuringSchedule;
        }
    }
    @Override public synchronized void close() {
        if(pending!=null)pending.cancel(false);
        pending=null;
        closed = true; actions.clear(); movement.set(null); clock.shutdownNow();
    }
}
