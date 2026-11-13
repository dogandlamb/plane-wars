package edu.hitsz.sky.core;

/** Platform-free owner of a single flight or night-shift simulation. */
public final class BattleEngine {
    private static final float MAX_ADVANCE_SECONDS = .1f;
    private static final float MAX_STEP_SECONDS = 1f / 60f;
    private final GameState state = new GameState();
    private final Simulation simulation;

    public BattleEngine(int stage, Difficulty difficulty, Mode mode, boolean developer, long seed) {
        StageCatalog.get(stage);
        if (difficulty == null || mode == null) throw new IllegalArgumentException("Difficulty and mode are required");
        state.stage = stage; state.difficulty = difficulty; state.mode = mode;
        state.developer = developer; state.phase = Phase.RUNNING;
        state.notice = StageCatalog.get(stage).subtitle;
        int count = mode == Mode.COOP ? 2 : 1;
        for (int i = 0; i < count; i++) {
            GameState.Pilot pilot = new GameState.Pilot();
            pilot.id = i; pilot.name = i == 0 ? "小夏" : "阿蓝";
            pilot.x = count == 1 ? 240 : 180 + i * 120;
            pilot.y = 680; pilot.hp = 100; pilot.maxHp = 100;
            pilot.bombs = developer ? 99 : 2; pilot.charge = 1;
            state.pilots.add(pilot);
        }
        simulation = stage == 6 ? new NightWatchSimulation(state, seed) : new FlightSimulation(state, seed);
    }

    public synchronized void accept(InputCommand command) {
        if (command == null || command.type == null || state.phase != Phase.RUNNING) return;
        if (command.playerId < 0 || command.playerId >= state.pilots.size()) return;
        simulation.accept(command);
    }

    public synchronized void step(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0 || state.phase != Phase.RUNNING) return;
        float remaining = Math.min(seconds, MAX_ADVANCE_SECONDS);
        while (remaining > .000001f && state.phase == Phase.RUNNING) {
            float step = Math.min(remaining, MAX_STEP_SECONDS);
            state.tick++; state.elapsed += step;
            simulation.step(step);
            remaining -= step;
        }
    }

    public synchronized GameState snapshot() { return StateSnapshots.copy(state); }

    interface Simulation {
        void accept(InputCommand command);
        void step(float seconds);
    }
}
