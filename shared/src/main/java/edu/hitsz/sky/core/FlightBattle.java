package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.List;

/** Framework-owned composition and final result storage for one flight battle. */
final class FlightBattle implements AutoCloseable {
    final HeroManager heroes;
    final BulletManager bullets;
    final EnemyManager enemies;
    final PickupManager pickups;
    final EffectManager effects;
    final ScoreManager scores;
    final BattleOutcomeRules outcome;
    final EventManager events;
    private final GameState state;
    private int nextId = 1;
    private BattleResult result;
    private GameState terminalFrame;

    FlightBattle(GameState state, EventManager events, HeroManager heroes) {
        this.state = state; this.events = events; this.heroes = heroes;
        scores = new ScoreManager(state.stage, heroes.identities());
        bullets = new BulletManager(state.shots, events, () -> nextId++);
        effects = new EffectManager(state.effects, events, () -> nextId++);
        pickups = new PickupManager(state.pickups, events, () -> nextId++, scores);
        enemies = new EnemyManager(state.enemies, events, () -> nextId++, scores, pickups, effects);
        outcome = new BattleOutcomeRules(state.mode, heroes, events, this::finish);
    }

    private BattleResult finish(Phase phase, BattleResult.Reason reason) {
        scores.freeze();
        List<BattleResult.HeroResult> values = new ArrayList<>();
        for (HeroManager.Identity hero : heroes.identities()) {
            values.add(new BattleResult.HeroResult(hero.id, hero.name, scores.getHeroScore(hero.id)));
        }
        result = new BattleResult(state, phase, reason, scores.getTotalScore(), values);
        state.phase = phase;
        if (phase == Phase.VICTORY) state.notice = state.stage == 5
                ? "航线恢复！大块头，跟我们回家。" : "航标找到了，下一站见！";
        terminalFrame = currentSnapshot();
        // Legacy consumers still require final hero values. These are detached, not live heroes.
        terminalFrame.shots.clear(); terminalFrame.enemies.clear();
        terminalFrame.pickups.clear(); terminalFrame.effects.clear();
        return result;
    }

    BattleResult result() { return result; }

    GameState snapshot() {
        return terminalFrame == null ? currentSnapshot() : StateSnapshots.copy(terminalFrame);
    }

    private GameState currentSnapshot() {
        GameState frame = StateSnapshots.copy(state);
        frame.score = scores.getTotalScore();
        for (GameState.Pilot hero : frame.pilots) hero.score = scores.getHeroScore(hero.id);
        return frame;
    }

    @Override public void close() {
        outcome.close(); enemies.close(); bullets.close(); pickups.close(); effects.close(); heroes.close();
    }
}
