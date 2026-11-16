package edu.hitsz.sky.core;

import java.util.function.Consumer;

/** Narrow battle services used by entity callbacks; owns no entity collection. */
final class CombatContext {
    final BattleConfig config;
    private final GameState state;
    private final EffectManager effects;
    private Consumer<String> notices;
    private Consumer<GameState.Pilot> detonation = hero -> { };

    CombatContext(GameState state, BattleConfig config, EffectManager effects) {
        this.state = state; this.config = config; this.effects = effects;
        notices = message -> state.notice = message;
    }
    void bind(Consumer<String> notices, Consumer<GameState.Pilot> detonation) {
        this.notices = notices; this.detonation = detonation;
    }
    boolean developer() { return state.developer; }
    int stage() { return state.stage; }
    float damageScale() { return state.difficulty.damageScale; }
    float elapsed() { return state.elapsed; }
    boolean endless() { return state.mode == Mode.ENDLESS; }
    boolean cooperative() { return state.mode == Mode.COOP; }
    void notice(String message) { notices.accept(message); }
    void effect(int kind, int owner, float x, float y, float radius) { effects.spawn(kind, owner, x, y, radius); }
    void detonate(GameState.Pilot hero) { detonation.accept(hero); }
    void freeze(float seconds) { state.freezeSeconds = seconds; }
}
