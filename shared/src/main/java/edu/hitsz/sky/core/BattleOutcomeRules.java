package edu.hitsz.sky.core;

import java.util.function.BiFunction;

/** Evaluated at a settlement boundary, after death rewards have been processed. */
public final class BattleOutcomeRules implements AutoCloseable {
    private final Mode mode;
    private final HeroManager heroes;
    private final EventManager events;
    private final BiFunction<Phase, BattleResult.Reason, BattleResult> finish;
    private final EventManager.Subscription bossSubscription;
    private boolean bossDefeated, ended;

    BattleOutcomeRules(Mode mode, HeroManager heroes, EventManager events,
                       BiFunction<Phase, BattleResult.Reason, BattleResult> finish) {
        this.mode = mode; this.heroes = heroes; this.events = events; this.finish = finish;
        bossSubscription = events.subscribe(BossDiedEvent.class, event -> bossDefeated = true);
    }

    void evaluate() {
        if (ended) return;
        // Retain campaign boss victory precedence; no new conflict policy is introduced here.
        if (mode != Mode.ENDLESS && bossDefeated) end(Phase.VICTORY, BattleResult.Reason.BOSS_DEFEATED);
        else if (heroes.livingCount() == 0) end(Phase.DEFEAT, BattleResult.Reason.ALL_HEROES_DOWN);
    }

    boolean campaignObjectiveReached() { return mode != Mode.ENDLESS && bossDefeated; }

    private void end(Phase outcome, BattleResult.Reason reason) {
        ended = true;
        BattleResult result = finish.apply(outcome, reason);
        events.publish(new BattleEndedEvent(result));
    }

    @Override public void close() { bossSubscription.close(); }
}
