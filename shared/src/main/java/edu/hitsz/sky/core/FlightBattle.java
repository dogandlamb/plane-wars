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
    final BattleConfig config;
    final CombatContext combat;
    final CollisionDetector collisions = new CollisionDetector();
    final CollisionDispatcher collisionDispatcher;
    private final GameState state;
    private BattleResult result;
    private GameState terminalFrame;

    FlightBattle(GameState state, EventManager events, HeroManager heroes) {
        this.state = state; this.events = events; this.heroes = heroes;
        config = heroes.config;
        scores = new ScoreManager(state.stage, heroes.identities());
        bullets = new BulletManager(state.shots, events, heroes.ids, config);
        effects = new EffectManager(state.effects, events, heroes.ids);
        pickups = new PickupManager(state.pickups, events, heroes.ids, scores);
        enemies = new EnemyManager(state.enemies, events, heroes.ids, scores, pickups, effects);
        combat = new CombatContext(state, config, effects);
        heroes.bindCombat(combat); enemies.bindCombat(combat);
        collisionDispatcher = new CollisionDispatcher(collisions, heroes, enemies, bullets, pickups);
        outcome = new BattleOutcomeRules(state.mode, heroes, events, this::finish);
        events.subscribe(BattleEndedEvent.class, event -> collisions.clear());
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
        collisions.clear();
        outcome.close(); enemies.close(); bullets.close(); pickups.close(); effects.close(); heroes.close();
    }
}
