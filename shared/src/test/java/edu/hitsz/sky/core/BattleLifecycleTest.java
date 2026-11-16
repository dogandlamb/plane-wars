package edu.hitsz.sky.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Regression coverage for ownership, death settlement, cleanup and final result boundaries. */
public final class BattleLifecycleTest {
    private static int assertions;

    public static void main(String[] arguments) {
        normalDeathsAndPickupsSettleOnce();
        bossPublishesOnceAndRewardsPrecedeEnding();
        defeatClearsEveryManagerWithoutRewards();
        endlessBossDeathDoesNotEndBattle();
        resultAndLegacyFrameSurviveCleanup();
        eventsAreScopedAndSubscriptionsCanClose();
        listenerFailureDoesNotSkipOtherCleanup();
        System.out.println("BattleLifecycleTest: " + assertions + " assertions passed");
    }

    private static void normalDeathsAndPickupsSettleOnce() {
        try (Fixture f = new Fixture(Mode.COOP, 7, 42)) {
            AtomicInteger bossEvents = new AtomicInteger();
            f.events.subscribe(BossDiedEvent.class, event -> bossEvents.incrementAndGet());
            Enemy first = f.battle.enemies.spawn(1, 100, 200, 21);
            f.battle.enemies.defeat(first, 42);
            f.battle.enemies.defeat(first, 42);
            Enemy second = f.battle.enemies.spawn(1, 200, 200, 21);
            f.battle.enemies.defeat(second, 7);
            check(f.battle.scores.getTotalScore() == 40, "each normal kill is credited once");
            check(f.battle.scores.getHeroScore(42) == 20 && f.battle.scores.getHeroScore(7) == 20,
                    "stable hero IDs are not list positions");
            check(bossEvents.get() == 0, "normal enemies never broadcast a boss event");
            check(f.battle.effects.count() == 2 && f.battle.pickups.count() == 1, "death effects and original drop cadence");
            GameState.Body pickup = f.state.pickups.get(0);
            check(f.battle.pickups.collect(pickup, 42), "first pickup succeeds");
            check(!f.battle.pickups.collect(pickup, 42), "same pickup cannot be collected twice");
            check(f.battle.scores.getTotalScore() == 50 && f.battle.scores.getHeroScore(42) == 30, "pickup score has one writer");
            f.battle.enemies.spawn(1, 100, 200, 21);
            f.battle.enemies.clearAll();
            check(f.battle.scores.getTotalScore() == 50 && f.battle.pickups.count() == 0, "clearing is not a kill");
        }
    }

    private static void bossPublishesOnceAndRewardsPrecedeEnding() {
        try (Fixture f = new Fixture(Mode.SOLO, 0)) {
            AtomicInteger deaths = new AtomicInteger(), ends = new AtomicInteger();
            AtomicReference<BattleResult> observed = new AtomicReference<>();
            f.events.subscribe(BossDiedEvent.class, event -> deaths.incrementAndGet());
            f.events.subscribe(BattleEndedEvent.class, event -> {
                ends.incrementAndGet(); observed.set(event.result);
                check(event.result == f.battle.result(), "result is stored before notification");
            });
            Enemy boss = f.battle.enemies.spawn(4, 240, 115, 53);
            boss.die(0); boss.die(0);
            check(deaths.get() == 1 && f.battle.scores.getTotalScore() == 450, "boss emits and rewards exactly once");
            check(f.state.phase == Phase.RUNNING, "death callback does not end mid-iteration");
            f.battle.outcome.evaluate(); f.battle.outcome.evaluate();
            check(ends.get() == 1 && observed.get().score == 450, "ending includes last boss reward, once");
            check(observed.get().reason == BattleResult.Reason.BOSS_DEFEATED, "structured victory reason");
        }
    }

    private static void defeatClearsEveryManagerWithoutRewards() {
        try (Fixture f = new Fixture(Mode.COOP, 0, 1)) {
            AtomicInteger deaths = new AtomicInteger(), ends = new AtomicInteger();
            f.events.subscribe(BossDiedEvent.class, event -> deaths.incrementAndGet());
            f.events.subscribe(BattleEndedEvent.class, event -> ends.incrementAndGet());
            f.battle.enemies.defeat(f.battle.enemies.spawn(1, 80, 200, 21), 1);
            f.battle.enemies.defeat(f.battle.enemies.spawn(1, 90, 200, 21), 1);
            f.battle.enemies.spawn(4, 240, 115, 53);
            f.battle.bullets.spawn(-1, 1, 200, 300, 0, 100, 10, 6);
            f.battle.bullets.spawn(1, 0, 200, 500, 0, -100, 10, 5);
            for (GameState.Pilot hero : f.state.pilots) { hero.hp = 0; hero.downed = true; }
            f.battle.outcome.evaluate(); f.battle.outcome.evaluate();
            check(f.state.phase == Phase.DEFEAT && ends.get() == 1, "all down ends once");
            check(f.battle.result().reason == BattleResult.Reason.ALL_HEROES_DOWN, "structured defeat reason");
            check(f.state.pilots.isEmpty() && f.state.shots.isEmpty() && f.state.enemies.isEmpty()
                    && f.state.pickups.isEmpty() && f.state.effects.isEmpty(), "defeat clears all live collections");
            check(f.battle.result().score == 40 && deaths.get() == 0, "clear neither kills boss nor awards extra points");
            check(f.battle.bullets.spawn(0, 0, 0, 0, 0, -10, 10, 5) == null, "late bullet spawn rejected");
            check(f.battle.enemies.spawn(0, 100, 0, 21) == null, "late enemy spawn rejected");
            check(f.battle.heroes.create(2, "late", 240, 680, false) == null, "late hero creation rejected");
            f.battle.effects.spawn(0, 0, 100, 100, 30);
            check(f.battle.effects.count() == 0, "late battle effects rejected");
            f.battle.heroes.clearAll(); f.battle.bullets.clearAll();
            check(f.battle.scores.getHeroScore(1) == 40, "personal score survives hero collection cleanup");
        }
    }

    private static void endlessBossDeathDoesNotEndBattle() {
        try (Fixture f = new Fixture(Mode.ENDLESS, 0)) {
            f.battle.enemies.defeat(f.battle.enemies.spawn(4, 240, 115, 53), 0);
            f.battle.outcome.evaluate();
            check(f.battle.result() == null && f.state.phase == Phase.RUNNING, "endless continues after a boss");
            check(f.battle.scores.getTotalScore() == 450, "endless still awards the boss kill");
            check(f.battle.enemies.isAcceptingSpawns() && f.battle.bullets.isAcceptingSpawns(), "generation stays enabled");
        }
    }

    private static void resultAndLegacyFrameSurviveCleanup() {
        try (BattleEngine engine = new BattleEngine(5, Difficulty.HARD, Mode.SOLO, false, 31)) {
            AtomicReference<BattleResult> notified = new AtomicReference<>();
            engine.onBattleEnded(event -> notified.set(event.result));
            for (int i = 0; i < 24000 && engine.result() == null; i++) engine.step(1f / 60f);
            BattleResult result = engine.result();
            check(result != null && result == notified.get(), "public framework result and notification agree");
            GameState frame = engine.snapshot();
            check(frame.pilots.size() == 1 && frame.pilots.get(0).id == result.heroes.get(0).id,
                    "legacy terminal snapshots retain detached hero values");
            check(frame.score == result.score && frame.pilots.get(0).score == result.heroes.get(0).score,
                    "legacy score projection agrees with result");
            check(frame.shots.isEmpty() && frame.enemies.isEmpty() && frame.pickups.isEmpty() && frame.effects.isEmpty(),
                    "terminal frame reflects unified cleanup");
            frame.pilots.get(0).name = "changed"; frame.pilots.clear();
            check(engine.snapshot().pilots.size() == 1 && !result.heroes.get(0).name.equals("changed"), "no mutable entity leaks into result");
            boolean immutable = false;
            try { result.heroes.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            check(immutable, "hero result list is read-only");
            long tick = engine.snapshot().tick;
            engine.accept(InputCommand.skill(0, 3)); engine.step(1);
            check(engine.result() == result && engine.snapshot().tick == tick, "ended battle cannot advance or change its result");
        }
    }

    private static void eventsAreScopedAndSubscriptionsCanClose() {
        EventManager first = new EventManager(), second = new EventManager();
        AtomicInteger calls = new AtomicInteger();
        EventManager.Subscription subscription = first.subscribe(String.class, event -> calls.incrementAndGet());
        second.publish("other battle"); first.publish("current battle");
        subscription.close(); first.publish("unsubscribed");
        check(calls.get() == 1, "subscriptions are scoped and removable");
        first.close(); second.close();
    }

    private static void listenerFailureDoesNotSkipOtherCleanup() {
        try (Fixture f = new Fixture(Mode.SOLO, 0)) {
            AtomicInteger calls = new AtomicInteger();
            f.events.subscribe(BattleEndedEvent.class, event -> { throw new IllegalStateException("test listener"); });
            f.events.subscribe(BattleEndedEvent.class, event -> calls.incrementAndGet());
            f.state.pilots.get(0).downed = true;
            boolean failed = false;
            try { f.battle.outcome.evaluate(); } catch (IllegalStateException expected) { failed = true; }
            check(failed && calls.get() == 1 && f.state.pilots.isEmpty(), "all handlers run even if a listener fails");
            check(f.battle.result() != null, "listener failure cannot lose stored result");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameState state = new GameState();
        final EventManager events = new EventManager();
        final FlightBattle battle;
        Fixture(Mode mode, int... heroIds) {
            state.stage = 1; state.mode = mode; state.difficulty = Difficulty.NORMAL; state.phase = Phase.RUNNING;
            state.elapsed = 12;
            HeroManager heroes = new HeroManager(state.pilots, events, heroIds.length);
            for (int id : heroIds) heroes.create(id, "Hero " + id, 240, 680, false);
            battle = new FlightBattle(state, events, heroes);
        }
        @Override public void close() { battle.close(); events.close(); }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
