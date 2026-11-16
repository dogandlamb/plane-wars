package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.List;

/** Observable movement and recycling contracts, including the original freeze and lifetime edges. */
public final class BulletMotionTest {
    private static int assertions;

    public static void main(String[] arguments) {
        entityOverrideMovesOnlyItsOwnBullet();
        freezeStopsHostileDisplacementButNotAge();
        windAffectsBothSidesAndRestartsAfterFreeze();
        expiryRemovesIdentityWithoutSkippingNeighbors();
        boundaryIsInclusiveAndRetirementStopsUpdates();
        frameworkSamplesTheCurrentEnvironment();
        closedManagerDoesNotUpdateRetiredObjects();
        invalidUpdateConditionsAreRejected();
        System.out.println("BulletMotionTest: " + assertions + " assertions passed");
    }

    private static void entityOverrideMovesOnlyItsOwnBullet() {
        try (Fixture f = new Fixture(1)) {
            GameState.Body shot = f.spawn(0, -10, -500);
            GameState.Body other = f.spawn(0, 0, -100);
            Entity entity = (Entity) shot;
            entity.update(new GameObjectUpdateContext(.2f, false, 0));
            near(shot.x, 198, "entity override advances horizontal position");
            near(shot.y, 200, "entity override advances vertical position");
            near(shot.age, .2f, "entity owns its age");
            near(other.y, 300, "one receiver does not move another bullet");
            check(shot.vx == -10 && shot.vy == -500 && shot.owner == 0, "movement preserves velocity and score identity");
        }
    }

    private static void freezeStopsHostileDisplacementButNotAge() {
        try (Fixture f = new Fixture(1)) {
            GameState.Body hostile = f.spawn(-1, 10, 20), friendly = f.spawn(0, 10, 20);
            f.battle.bullets.update(new GameObjectUpdateContext(.5f, true, 100));
            near(hostile.x, 200, "frozen hostile does not drift in wind");
            near(hostile.y, 300, "frozen hostile does not move vertically");
            near(hostile.age, .5f, "freeze does not pause hostile lifetime");
            near(friendly.x, 240, "friendly retains velocity plus scaled wind during freeze");
            near(friendly.y, 310, "friendly is not frozen");
            check(hostile.vx == 10 && hostile.vy == 20, "freeze does not destroy stored velocity");
        }
    }

    private static void windAffectsBothSidesAndRestartsAfterFreeze() {
        try (Fixture f = new Fixture(1)) {
            GameState.Body hostile = f.spawn(-1, 0, 100), friendly = f.spawn(0, 0, -100);
            f.battle.bullets.update(new GameObjectUpdateContext(.5f, true, -20));
            f.battle.bullets.update(new GameObjectUpdateContext(.5f, false, -20));
            near(hostile.x, 193, "hostile resumes wind displacement after freeze");
            near(hostile.y, 350, "hostile resumes its original velocity");
            near(friendly.x, 186, "friendly receives wind in both updates");
            near(friendly.y, 200, "friendly moves throughout freeze");
            near(hostile.age, 1, "lifetime covers frozen and unfrozen time");
        }
    }

    private static void expiryRemovesIdentityWithoutSkippingNeighbors() {
        try (Fixture f = new Fixture(1)) {
            List<GameState.Body> shots = new ArrayList<>();
            for (int i = 0; i < 3; i++) { GameState.Body shot = f.spawn(-1, 0, 0); shot.age = 13; shots.add(shot); }
            f.battle.bullets.update(new GameObjectUpdateContext(1, true, 100));
            check(f.battle.bullets.count() == 3, "exactly fourteen seconds is still alive, preserving strict expiry");
            f.battle.bullets.update(new GameObjectUpdateContext(.01f, true, 100));
            check(f.battle.bullets.count() == 0, "adjacent expired bullets all recycle in the same batch");
            for (GameState.Body shot : shots)
                check(!shot.active && !f.battle.bullets.collisionActive(shot.entityId), "expiry removes live and collision identity");
            List<CollisionBody> colliders = new ArrayList<>(); f.battle.bullets.collectColliders(colliders);
            check(colliders.isEmpty(), "expired bullets cannot enter subsequent collision detection");
            check(f.battle.scores.getTotalScore() == 0, "expiry is not a scored collision");
            int retired = shots.get(2).entityId;
            check(f.spawn(0, 0, 0).entityId > retired, "expiry does not recycle entity identity");
        }
    }

    private static void boundaryIsInclusiveAndRetirementStopsUpdates() {
        try (Fixture f = new Fixture(1)) {
            float[][] edges = {{-40, 300}, {520, 300}, {200, -70}, {200, 860}};
            List<GameState.Body> shots = new ArrayList<>();
            for (float[] edge : edges) {
                GameState.Body shot = f.spawn(0, 0, 0); shot.x = edge[0]; shot.y = edge[1]; shots.add(shot);
            }
            f.battle.bullets.update(new GameObjectUpdateContext(.1f, false, 0));
            check(f.battle.bullets.count() == 4, "all original boundary edges remain inclusive");
            shots.get(0).x -= .01f; shots.get(1).x += .01f;
            shots.get(2).y -= .01f; shots.get(3).y += .01f;
            f.battle.bullets.update(new GameObjectUpdateContext(.1f, false, 0));
            check(f.battle.bullets.count() == 0, "all four out-of-bounds directions recycle");
            for (GameState.Body shot : shots) {
                float x = shot.x, age = shot.age;
                ((Entity) shot).update(new GameObjectUpdateContext(1, false, 100));
                check(shot.x == x && shot.age == age && !f.battle.bullets.collisionActive(shot.entityId), "retired references cannot move or regain identity");
            }
        }
    }

    private static void frameworkSamplesTheCurrentEnvironment() {
        try (Fixture f = new Fixture(2)) {
            FlightSimulation simulation = new FlightSimulation(f.state, 10, f.battle);
            f.state.elapsed = 2; f.state.freezeSeconds = 1;
            GameState.Body hostile = f.spawn(-1, 0, 100), friendly = f.spawn(0, 0, -100);
            simulation.step(.1f);
            near(hostile.x, 200, "simulation supplies current freeze to bullet");
            near(hostile.y, 300, "simulation no longer moves a frozen bullet externally");
            check(friendly.x > 200 && friendly.y == 290, "wind stage supplies a nonzero current wind sample");
            near(friendly.age, .1f, "simulation updates each bullet exactly once");
            f.state.freezeSeconds = .05f;
            simulation.step(.1f);
            check(hostile.x > 200 && hostile.y == 310, "freeze expiry follows existing simulation-step timing");
        }
        try (Fixture f = new Fixture(1)) {
            FlightSimulation simulation = new FlightSimulation(f.state, 10, f.battle);
            f.state.elapsed = 2;
            GameState.Body shot = f.spawn(0, 0, -100); simulation.step(.1f);
            near(shot.x, 200, "other stages do not acquire wind accidentally");
        }
    }

    private static void closedManagerDoesNotUpdateRetiredObjects() {
        try (Fixture f = new Fixture(1)) {
            GameState.Body shot = f.spawn(0, 100, -100);
            f.battle.bullets.close();
            f.battle.bullets.update(new GameObjectUpdateContext(.5f, false, 100));
            check(shot.x == 200 && shot.y == 300 && shot.age == 0 && !shot.active, "closed manager clears and stops update dispatch");
            check(f.battle.bullets.spawn(0, 0, 200, 300, 0, 0, "hero.basic", 5) == null, "closed manager still rejects new bullets");
        }
    }

    private static void invalidUpdateConditionsAreRejected() {
        for (float value : new float[]{-.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            boolean rejected = false;
            try { new GameObjectUpdateContext(value, false, 0); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid delta rejected");
        }
        boolean rejected = false;
        try { new GameObjectUpdateContext(.1f, false, Float.NaN); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nonfinite wind rejected");
        try (Fixture f = new Fixture(1)) {
            GameState.Body shot = f.spawn(0, 100, 100);
            f.battle.bullets.update(new GameObjectUpdateContext(0, false, 100));
            check(shot.x == 200 && shot.y == 300 && shot.age == 0, "zero delta changes nothing");
        }
    }

    private static void near(float actual, float expected, String description) {
        check(Math.abs(actual - expected) < .0001f, description + ": " + actual + " != " + expected);
    }
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    private static final class Fixture implements AutoCloseable {
        final GameState state = new GameState();
        final EventManager events = new EventManager();
        final FlightBattle battle;
        Fixture(int stage) {
            state.stage = stage; state.mode = Mode.SOLO; state.difficulty = Difficulty.NORMAL; state.phase = Phase.RUNNING;
            HeroManager heroes = new HeroManager(state.pilots, events, 1);
            heroes.create(0, "Hero", 240, 680, false);
            battle = new FlightBattle(state, events, heroes);
        }
        GameState.Body spawn(int owner, float vx, float vy) {
            return battle.bullets.spawn(owner, owner < 0 ? 1 : 0, 200, 300, vx, vy, owner < 0 ? "hostile.10" : "hero.basic", 5);
        }
        @Override public void close() { battle.close(); events.close(); }
    }
}
