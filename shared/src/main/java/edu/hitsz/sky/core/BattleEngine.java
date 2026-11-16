package edu.hitsz.sky.core;

/** Platform-free owner of a single flight or night-shift simulation. */
public final class BattleEngine implements AutoCloseable {
    private static final float MAX_ADVANCE_SECONDS = .1f;
    private static final float MAX_STEP_SECONDS = 1f / 60f;
    private final GameState state = new GameState();
    private final Simulation simulation;
    private final EventManager events = new EventManager();
    private final FlightBattle flight;
    private boolean closed;

    public BattleEngine(int stage, Difficulty difficulty, Mode mode, boolean developer, long seed) {
        StageCatalog.get(stage);
        if (difficulty == null || mode == null) throw new IllegalArgumentException("Difficulty and mode are required");
        state.stage = stage; state.difficulty = difficulty; state.mode = mode;
        state.developer = developer; state.phase = Phase.RUNNING;
        state.notice = StageCatalog.get(stage).subtitle;
        int count = mode == Mode.COOP ? 2 : 1;
        HeroManager heroes = new HeroManager(state.pilots, stage == 6 ? null : events, count);
        for (int i = 0; i < count; i++) {
            heroes.create(i, i == 0 ? "小夏" : "阿蓝", count == 1 ? 240 : 180 + i * 120, 680, developer);
        }
        flight = stage == 6 ? null : new FlightBattle(state, events, heroes);
        simulation = flight == null ? new NightWatchSimulation(state, seed) : new FlightSimulation(state, seed, flight);
    }

    public synchronized void accept(InputCommand command) {
        if (closed || command == null || command.type == null || state.phase != Phase.RUNNING) return;
        if (command.playerId < 0 || command.playerId >= state.pilots.size()) return;
        simulation.accept(command);
    }

    public synchronized void step(float seconds) {
        if (closed || !Float.isFinite(seconds) || seconds <= 0 || state.phase != Phase.RUNNING) return;
        float remaining = Math.min(seconds, MAX_ADVANCE_SECONDS);
        while (remaining > .000001f && state.phase == Phase.RUNNING) {
            float step = Math.min(remaining, MAX_STEP_SECONDS);
            state.tick++; state.elapsed += step;
            simulation.step(step);
            remaining -= step;
        }
    }

    public synchronized GameState snapshot() { return flight == null ? StateSnapshots.copy(state) : flight.snapshot(); }

    /** Flight result, or null before completion (and for the separate night-shift game). */
    public synchronized BattleResult result() { return flight == null ? null : flight.result(); }

    /** Called synchronously on the simulation thread; Android callers must marshal to their UI thread. */
    public synchronized EventManager.Subscription onBattleEnded(java.util.function.Consumer<BattleEndedEvent> listener) {
        return events.subscribe(BattleEndedEvent.class, listener);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (flight != null) flight.close();
        events.close();
    }

    interface Simulation {
        void accept(InputCommand command);
        void step(float seconds);
    }
}
