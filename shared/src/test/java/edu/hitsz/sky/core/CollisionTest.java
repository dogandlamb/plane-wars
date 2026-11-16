package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Geometry contracts plus real-manager regressions for independent two-sided processing. */
public final class CollisionTest {
    private static int assertions;

    public static void main(String[] arguments) {
        circlesAndPurposeSpecificRadii();
        classificationOrderingAndBatchLifetime();
        invalidInputFailsExplicitly();
        uniqueIdsAndDetachedValues();
        bothSidesFinishAfterSourceRemoval();
        shieldSpeedAndHeroImmunity();
        provisionalConflictPolicyConsumesOnce();
        pickupDropWaitsForNextBatch();
        pickupEffectsAndSingleClaim();
        bombClearsManagerIndexesBeforeLaterContacts();
        bodyDamageAndBossProtection();
        debugSamplingDoesNotChangeResults();
        System.out.println("CollisionTest: " + assertions + " assertions passed");
    }

    private static void circlesAndPurposeSpecificRadii() {
        CollisionDetector detector = new CollisionDetector();
        CollisionBody hero = new CollisionBody(1, CollisionType.HERO, "hero", 42, 100, 100, 0, 0, 0, 12, 15, 18);
        for (CollisionType type : new CollisionType[]{CollisionType.BULLET, CollisionType.ENEMY, CollisionType.PICKUP}) {
            float reach = type == CollisionType.BULLET ? 13 : type == CollisionType.ENEMY ? 16 : 19;
            CollisionBody tangent = body(2, type, -1, 100 + reach, 100, 1);
            Map<Integer, Set<CollisionInfo>> hits = detector.detect(source(hero, tangent));
            check(hits.size() == 2 && hits.get(1).size() == 1 && hits.get(2).size() == 1, "tangent contact uses correct hero circle: " + type);
            CollisionBody outside = body(2, type, -1, 100 + reach + .01f, 100, 1);
            check(detector.detect(source(hero, outside)).isEmpty(), "outside correct hero circle: " + type);
        }
        CollisionBody shot = body(3, CollisionType.BULLET, 42, 120, 100, 5);
        CollisionBody enemy = body(2, CollisionType.ENEMY, -1, 100, 100, 15);
        check(detector.detect(source(shot, enemy)).size() == 2, "player shot tangency includes both radii");
        check(detector.detect(source(hero, shot)).isEmpty(), "friendly projectile is not paired with hero");
        check(detector.detect(source(enemy, body(4, CollisionType.ENEMY, -1, 100, 100, 15))).isEmpty(), "enemy-enemy excluded");
        check(detector.detect(source(hero, body(5, CollisionType.HERO, 7, 100, 100, 15))).isEmpty(), "hero-hero excluded");
    }

    private static void classificationOrderingAndBatchLifetime() {
        CollisionDetector detector = new CollisionDetector();
        CollisionBody enemy = body(9, CollisionType.ENEMY, -1, 100, 100, 20);
        CollisionBody shot = new CollisionBody(3, CollisionType.BULLET, "hero.spread", 42, 100, 100, 105, -500, 5);
        CollisionBody second = body(7, CollisionType.BULLET, 7, 100, 100, 5);
        Map<Integer, Set<CollisionInfo>> result = detector.detect(source(enemy, second, shot));
        check(new ArrayList<>(result.keySet()).equals(Arrays.asList(3, 7, 9)), "keys sorted regardless of source order");
        List<Integer> neighbors = new ArrayList<>();
        for (CollisionInfo info : result.get(9)) neighbors.add(info.otherId);
        check(neighbors.equals(Arrays.asList(3, 7)), "neighbors sorted and unique");
        CollisionInfo saved = result.get(9).iterator().next();
        check(saved.creditedHeroId == 42 && saved.configurationTag.equals("hero.spread") && saved.vx == 105 && saved.vy == -500,
                "configuration, speed and score identity copied with contact");
        boolean immutable = false;
        try { result.get(9).clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "result values read-only");
        immutable = false;
        try { result.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "result map read-only");
        check(detector.ids(CollisionType.BULLET).equals(new HashSet<>(Arrays.asList(3, 7))), "per-type identity pool");
        check(detector.detect(source()).isEmpty() && detector.ids(CollisionType.ENEMY).isEmpty(), "next batch discards old identities");
        check(result.size() == 3 && saved.creditedHeroId == 42, "previous immutable result survives index refresh");
    }

    private static void invalidInputFailsExplicitly() {
        boolean rejected = false;
        try { body(1, CollisionType.ENEMY, -1, Float.NaN, 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nonfinite position rejected");
        rejected = false;
        try { body(1, CollisionType.ENEMY, -1, 0, 0, -1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "negative radius rejected");
        rejected = false;
        try { new CollisionDetector().detect(source(body(1, CollisionType.ENEMY, -1, 0, 0, 1), body(1, CollisionType.PICKUP, -1, 0, 0, 1))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "duplicate identity across types rejected");
    }

    private static void uniqueIdsAndDetachedValues() {
        try (Fixture f = new Fixture(Mode.COOP, false, 7, 42)) {
            GameState.Body shot = f.shot(42, 300, 300, "hero.basic");
            Enemy enemy = f.enemy(1, 300, 300, 100);
            Pickup pickup = f.battle.pickups.spawn(0, 200, 200);
            f.battle.effects.spawn(0, 7, 200, 200, 20);
            Set<Integer> identities = new HashSet<>();
            for (List<? extends GameObject> list : Arrays.asList(f.state.pilots, f.state.shots, f.state.enemies, f.state.pickups, f.state.effects))
                for (GameObject object : list) check(object.entityId > 0 && identities.add(object.entityId), "all managers share unique identity allocation");
            Map<Integer, Set<CollisionInfo>> contacts = f.detect();
            check(!f.battle.collisions.ids(CollisionType.BULLET).contains(f.state.effects.get(0).entityId), "effects never enter detector");
            check(f.battle.heroes.count() == 2 && f.state.pilots.get(1).id == 42, "score identities independent from global entity identities");
            GameState frame = f.battle.snapshot();
            check(frame.shots.get(0).getClass() == GameState.Body.class, "snapshot does not expose live bullet receiver");
            frame.shots.get(0).x = 999;
            check(shot.x == 300 && contacts.containsKey(enemy.entityId), "geometry leaves entities unchanged and frames detached");
            int retired = pickup.entityId;
            f.battle.pickups.remove(pickup);
            check(f.battle.pickups.spawn(0, 200, 200).entityId > retired, "recycled entity identity not reused");
        }
    }

    private static void bothSidesFinishAfterSourceRemoval() {
        try (Fixture f = new Fixture(Mode.COOP, false, 7, 42)) {
            GameState.Body bullet = f.shot(42, 200, 200, "hero.basic"); // Lower ID: callback removes this source first.
            Enemy enemy = f.enemy(1, 200, 200, 17);
            Map<Integer, Set<CollisionInfo>> contacts = f.detect();
            f.dispatch(contacts);
            check(!bullet.active && enemy.isDead(), "source removal cannot prevent target callback");
            check(f.battle.scores.getHeroScore(42) == 20 && f.battle.scores.getHeroScore(7) == 0, "lethal source credits its hero, not source ID");
            f.dispatch(contacts);
            check(f.battle.scores.getTotalScore() == 20, "stale contacts cannot score again");
            check(f.battle.bullets.count() == 0 && f.battle.enemies.count() == 0, "both sides recycled in same step");
        }
    }

    private static void shieldSpeedAndHeroImmunity() {
        try (Fixture f = new Fixture(Mode.SOLO, false, 0)) {
            Enemy shield = f.enemy(5, 200, 200, 100); shield.flags = 1;
            f.shot(0, 200, 200, "hero.basic"); f.dispatch(f.detect());
            check(shield.hp == 100 && f.battle.bullets.count() == 0, "closed shield blocks but still consumes bullet");
            GameState.Body side = f.shot(0, 200, 200, "hero.spread"); side.vx = 105;
            f.dispatch(f.detect());
            check(shield.hp == 85, "side velocity preserves shield bypass");
            GameState.Pilot hero = f.state.pilots.get(0); hero.invulnerable = 2;
            f.shot(-1, hero.x, hero.y, "hostile.10"); f.dispatch(f.detect());
            check(hero.hp == 100 && f.battle.bullets.count() == 0, "invulnerable hero still consumes hostile bullet");
            hero.invulnerable = 0; f.state.difficulty = Difficulty.HARD;
            f.shot(-1, hero.x, hero.y, "hostile.10"); f.dispatch(f.detect());
            check(hero.hp == 87 && hero.invulnerable == 1.1f, "hero applies difficulty and hit protection itself");
            hero.downed = true;
            f.shot(-1, hero.x, hero.y, "hostile.10");
            check(!f.detect().containsKey(hero.entityId), "downed hero absent from input");
        }
        try (Fixture f = new Fixture(Mode.SOLO, true, 0)) {
            GameState.Pilot hero = f.state.pilots.get(0);
            f.shot(-1, hero.x, hero.y, "hostile.10"); f.dispatch(f.detect());
            check(hero.hp == 100 && f.battle.bullets.count() == 0, "developer immunity does not suppress geometry or bullet consumption");
        }
    }

    private static void provisionalConflictPolicyConsumesOnce() {
        try (Fixture f = new Fixture(Mode.SOLO, false, 0)) {
            Enemy first = f.enemy(0, 200, 200, 100), second = f.enemy(0, 200, 200, 100);
            f.shot(0, 200, 200, "hero.basic");
            Map<Integer, Set<CollisionInfo>> contacts = f.detect();
            check(contacts.get(f.state.shots.get(0).entityId).size() == 2, "geometry reports both candidates");
            f.dispatch(contacts);
            check(first.hp == 83 && second.hp == 100, "temporary policy uses first active target once");
            check(f.battle.bullets.count() == 0, "nonpiercing shot consumed once");
        }
    }

    private static void pickupDropWaitsForNextBatch() {
        try (Fixture f = new Fixture(Mode.SOLO, false, 0)) {
            f.battle.enemies.defeat(f.enemy(1, 100, 100, 1), 0);
            f.enemy(1, 200, 200, 17); f.shot(0, 200, 200, "hero.basic");
            Map<Integer, Set<CollisionInfo>> contacts = f.detect(); f.dispatch(contacts);
            Pickup drop = (Pickup) f.state.pickups.get(0);
            check(!contacts.containsKey(drop.entityId), "new death drop cannot enter completed detection batch");
            GameState.Pilot hero = f.state.pilots.get(0); hero.x = drop.x; hero.y = drop.y; hero.hp = 50;
            f.dispatch(f.detect());
            check(hero.hp == 85 && f.battle.pickups.count() == 0 && f.battle.scores.getTotalScore() == 50, "next batch can collect drop and score once");
        }
    }

    private static void pickupEffectsAndSingleClaim() {
        try (Fixture f = new Fixture(Mode.COOP, false, 7, 42)) {
            GameState.Pilot first = f.state.pilots.get(0), second = f.state.pilots.get(1);
            first.hp = second.hp = 50; second.x = first.x; second.y = first.y;
            f.battle.pickups.spawn(0, first.x, first.y); f.dispatch(f.detect());
            check(first.hp == 85 && second.hp == 50, "shared pickup effect belongs to one hero only");
            check(f.battle.scores.getHeroScore(7) == 10 && f.battle.scores.getHeroScore(42) == 0, "pickup manager credits its actual claimant");
            f.battle.pickups.spawn(1, first.x, first.y); f.dispatch(f.detect());
            check(first.weapon == 1 && first.weaponSeconds == 5, "spread effect in hero callback");
            f.battle.pickups.spawn(2, first.x, first.y); f.dispatch(f.detect());
            check(first.weapon == 2, "ring effect in hero callback");
            f.battle.pickups.spawn(4, first.x, first.y); f.dispatch(f.detect());
            check(f.state.freezeSeconds == 5 && f.battle.pickups.count() == 0, "freeze invokes shared battle service");
        }
    }

    private static void bodyDamageAndBossProtection() {
        try (Fixture f = new Fixture(Mode.SOLO, false, 0)) {
            GameState.Pilot hero = f.state.pilots.get(0);
            Enemy enemy = f.enemy(0, hero.x, hero.y, 100);
            f.dispatch(f.detect());
            check(hero.hp == 72 && enemy.isDead() && f.battle.scores.getTotalScore() == 10, "ram changes each participant's own state and retains score");
            Enemy boss = f.enemy(4, hero.x, hero.y, 100); hero.invulnerable = 0;
            f.dispatch(f.detect());
            check(hero.hp == 37 && !boss.isDead(), "boss body does not die on contact");
            boss.receiveDamage(1000, 0);
            check(boss.hp == 1 && (boss.flags & 2) != 0, "campaign boss enters protected second phase");
            f.state.elapsed += 3.1f;
            boss.receiveDamage(17, 0);
            check(boss.isDead() && f.battle.scores.getTotalScore() == 460, "boss event path rewards once after protection");
            f.battle.outcome.evaluate();
            check(f.battle.result().score == 460 && f.battle.collisions.ids(CollisionType.HERO).isEmpty(), "ending freezes score and clears detector indexes");
        }
    }

    private static void bombClearsManagerIndexesBeforeLaterContacts() {
        try (Fixture f = new Fixture(Mode.SOLO, false, 0)) {
            FlightSimulation simulation = new FlightSimulation(f.state, 1, f.battle);
            GameState.Pilot hero = f.state.pilots.get(0);
            Pickup bomb = f.battle.pickups.spawn(3, hero.x, hero.y);
            GameState.Body hostile = f.shot(-1, hero.x, hero.y, "hostile.10");
            f.dispatch(f.detect());
            check(hero.hp == 100 && !hostile.active && !bomb.active, "bomb pickup clears hostile before its later contact");
            check(!f.battle.bullets.collisionActive(hostile.entityId) && f.battle.bullets.count() == 0,
                    "special clearing cannot leave a stale manager identity");
            f.shot(-1, hero.x, hero.y - 100, "hostile.10");
            simulation.weapons.skill(hero, 0);
            check(f.battle.bullets.count() == 0, "shockwave still clears projectiles through their owner manager");
        }
    }

    private static void debugSamplingDoesNotChangeResults() {
        CollisionDetector detector = new CollisionDetector(); detector.configureDebug(true, 2, 1);
        Map<Integer, Set<CollisionInfo>> result = detector.detect(source(
                body(1, CollisionType.ENEMY, -1, 0, 0, 20), body(2, CollisionType.BULLET, 0, 0, 0, 5), body(3, CollisionType.BULLET, 0, 0, 0, 5)));
        check(detector.debugFrame(1, result) == null, "debug sampling interval");
        CollisionDebugFrameEvent debug = detector.debugFrame(2, result);
        check(debug.contacts == 2 && debug.candidatePairs == 2 && debug.truncated && debug.samples.size() == 1, "bounded geometry diagnostics");
        check(result.get(1).size() == 2, "debug truncation never truncates formal results");
        detector.configureDebug(false, 1, 10);
        check(detector.debugFrame(2, result) == null, "debug disabled by default path");
        try (BattleEngine engine = new BattleEngine(1, Difficulty.NORMAL, Mode.SOLO, false, 10)) {
            AtomicInteger observed = new AtomicInteger();
            engine.configureCollisionDebug(true, 1, 8);
            engine.onCollisionDebug(event -> { throw new IllegalStateException("diagnostic observer"); });
            engine.onCollisionDebug(event -> observed.incrementAndGet());
            engine.step(1f / 60f);
            check(engine.snapshot().tick == 1 && observed.get() == 1, "diagnostic listener failure cannot interrupt gameplay or other observers");
        }
    }

    private static CollisionSource source(CollisionBody... bodies) { return output -> Collections.addAll(output, bodies); }
    private static CollisionBody body(int id, CollisionType type, int owner, float x, float y, float radius) {
        return new CollisionBody(id, type, type == CollisionType.BULLET ? "hero.basic" : "test", owner, x, y, 0, 0, radius);
    }
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    private static final class Fixture implements AutoCloseable {
        final GameState state = new GameState();
        final EventManager events = new EventManager();
        final FlightBattle battle;
        Fixture(Mode mode, boolean developer, int... heroIds) {
            state.stage = 1; state.mode = mode; state.developer = developer;
            state.difficulty = Difficulty.NORMAL; state.phase = Phase.RUNNING; state.elapsed = 10;
            HeroManager heroes = new HeroManager(state.pilots, events, heroIds.length);
            for (int id : heroIds) heroes.create(id, "Hero " + id, 240 + state.pilots.size() * 100, 680, developer);
            battle = new FlightBattle(state, events, heroes);
        }
        Enemy enemy(int kind, float x, float y, int hp) {
            Enemy enemy = battle.enemies.spawn(kind, x, y, 20); enemy.hp = enemy.maxHp = hp; return enemy;
        }
        GameState.Body shot(int owner, float x, float y, String tag) { return battle.bullets.spawn(owner, owner < 0 ? 1 : 0, x, y, 0, 0, tag, 5); }
        Map<Integer, Set<CollisionInfo>> detect() { return battle.collisions.detect(battle.heroes, battle.enemies, battle.bullets, battle.pickups); }
        void dispatch(Map<Integer, Set<CollisionInfo>> result) { battle.collisionDispatcher.dispatch(result, battle.outcome::campaignObjectiveReached); }
        @Override public void close() { battle.close(); events.close(); }
    }
}
