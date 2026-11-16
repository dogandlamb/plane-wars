package edu.hitsz.sky.core;

import java.util.ArrayList;

/** Regression contracts shared by entity and non-entity lifecycle updates. */
public final class GameObjectLifecycleTest {
    private static int assertions;
    public static void main(String[] args) {
        visualObjectsUseTheCommonTemplate();
        effectsRetainTheirLifetimeEdges();
        pickupsIgnoreWindAndFreeze();
        enemyFreezePoliciesRemainDistinct();
        enemyAttacksPrecedeBoundaryRetirement();
        newlySpawnedEnemiesWaitForNextUpdate();
        heroesOwnMovementAndDash();
        System.out.println("GameObjectLifecycleTest: " + assertions + " assertions passed");
    }
    private static void visualObjectsUseTheCommonTemplate() {
        try (VisualManager manager = new VisualManager()) {
            Visual visual = new Visual(); visual.entityId = 1; visual.x = 10; visual.y = 20; visual.vx = 6; visual.vy = 8;
            manager.register(visual);
            manager.update(new GameObjectUpdateContext(.25f, true, 4));
            near(visual.x, 12, "non-entity inherits movement and specializes wind");
            near(visual.y, 22, "non-entity inherits vertical movement");
            near(visual.age, .25f, "non-entity inherits age progression");
            ArrayList<CollisionBody> colliders = new ArrayList<>(); manager.collectColliders(colliders);
            check(colliders.isEmpty(), "visual lifecycle never registers collision geometry");
            manager.update(new GameObjectUpdateContext(.25f, false, 0));
            check(manager.count() == 1, "default expiry uses strict greater-than");
            manager.update(new GameObjectUpdateContext(.01f, false, 0));
            check(manager.count() == 0 && !visual.active && manager.releases == 1, "base requests owner retirement once");
            float age = visual.age; visual.update(new GameObjectUpdateContext(1, false, 100));
            visual.retire(); manager.clearAll();
            check(visual.age == age && manager.releases == 1, "retired object cannot update or release twice");
        }
    }
    private static void effectsRetainTheirLifetimeEdges() {
        try (Fixture f = new Fixture()) {
            int[] kinds = {0, 1, 5, 4, 4}, owners = {0, 0, 0, -1, 0};
            float[] lifetimes = {.65f, .22f, .32f, 1.15f, .48f};
            for (int i = 0; i < kinds.length; i++) {
                f.battle.effects.spawn(kinds[i], owners[i], 100, 100, 20);
                GameObject effect = f.state.effects.get(0);
                check(effect instanceof NonEntity, "actual effects inherit the non-entity branch");
                f.battle.effects.update(new GameObjectUpdateContext(lifetimes[i], true, 100));
                check(f.battle.effects.count() == 0 && !effect.active, "effect expires exactly at its original duration even during freeze");
            }
            f.battle.effects.spawn(0, 0, 100, 100, 20);
            GameObject effect = f.state.effects.get(0); effect.vx = 10; effect.vy = 20;
            f.battle.effects.update(new GameObjectUpdateContext(.1f, true, 100));
            near(effect.x, 101, "effect moves but ignores battle wind");
            near(effect.y, 102, "effect ignores battle freeze");
        }
    }
    private static void pickupsIgnoreWindAndFreeze() {
        try (Fixture f = new Fixture()) {
            Pickup pickup = f.battle.pickups.spawn(0, 200, 300);
            f.battle.pickups.update(new GameObjectUpdateContext(.5f, true, 100));
            near(pickup.x, 200, "pickup ignores wind"); near(pickup.y, 329, "pickup ignores freeze");
            pickup.y = 840; pickup.vy = 0;
            f.battle.pickups.update(new GameObjectUpdateContext(.1f, false, 0));
            check(pickup.active, "pickup boundary remains inclusive");
            pickup.y = 840.01f;
            f.battle.pickups.update(new GameObjectUpdateContext(.1f, false, 0));
            check(!pickup.active && f.battle.pickups.count() == 0 && f.battle.scores.getTotalScore() == 0,
                    "out-of-bounds retirement neither collects nor scores pickup");
        }
    }
    private static void enemyFreezePoliciesRemainDistinct() {
        try (Fixture f = new Fixture()) {
            Enemy normal = f.enemy(0, 200, 100), ace = f.enemy(3, 200, 100), boss = f.enemy(4, 200, 100);
            normal.vy = ace.vy = 100;
            f.battle.enemies.update(new GameObjectUpdateContext(1, true, 100));
            near(normal.y, 100, "normal enemy freezes"); near(normal.age, 0, "normal enemy action clock freezes");
            near(ace.y, 135, "ace slows to original fraction"); near(ace.age, .35f, "ace action clock slows");
            near(boss.y, 115, "boss movement ignores freeze"); near(boss.age, 1, "boss clock ignores freeze");
            near(normal.x, 200, "enemy ignores wind"); near(ace.x, 200, "ace ignores wind");
            f.battle.enemies.update(new GameObjectUpdateContext(1, false, 0));
            near(normal.y, 200, "normal enemy resumes with original velocity");
        }
    }
    private static void enemyAttacksPrecedeBoundaryRetirement() {
        try (Fixture f = new Fixture()) {
            int[] attacks = {0};
            f.battle.enemies.bindAttackBehavior((enemy, seconds) -> {
                check(enemy.active && enemy.y > 850, "final attack callback sees moved and still-owned enemy"); attacks[0]++;
            });
            Enemy enemy = f.enemy(0, 200, 849); enemy.vy = 10;
            f.battle.enemies.update(new GameObjectUpdateContext(.2f, false, 0));
            check(attacks[0] == 1 && !enemy.active && f.battle.enemies.count() == 0, "boundary retirement follows existing attack timing");
            check(f.battle.scores.getTotalScore() == 0 && f.battle.effects.count() == 0, "boundary removal is not enemy death");
        }
    }
    private static void newlySpawnedEnemiesWaitForNextUpdate() {
        try (Fixture f = new Fixture()) {
            Enemy[] child = {null};
            f.battle.enemies.bindAttackBehavior((enemy, seconds) -> {
                if (enemy.kind == 6 && child[0] == null) child[0] = f.enemy(0, 200, 100);
            });
            Enemy carrier = f.enemy(6, 200, 100);
            f.battle.enemies.update(new GameObjectUpdateContext(.1f, false, 0));
            check(child[0] != null && child[0].age == 0, "new child is not updated in its parent's current batch");
            f.battle.enemies.update(new GameObjectUpdateContext(.1f, false, 0));
            near(child[0].age, .1f, "child updates next batch"); near(carrier.age, .2f, "parent updates exactly once per batch");
        }
    }
    private static void heroesOwnMovementAndDash() {
        try (Fixture f = new Fixture()) {
            Hero hero = (Hero) f.state.pilots.get(0); hero.setMovementTarget(340, 680);
            f.battle.heroes.updateObject(hero.entityId, new GameObjectUpdateContext(.1f, true, 20));
            near(hero.x, 289, "hero owns target movement plus wind and ignores freeze");
            near(hero.y, 680, "hero retains target height"); near(hero.vx, 490, "hero owns reported velocity");
            near(hero.dash(), 680, "dash returns its path origin"); near(hero.y, 500, "dash owns position change");
            near(hero.vy, -1800, "dash refreshes this step's velocity");
            hero.setMovementTarget(-10000, 10000);
            f.battle.heroes.updateObject(hero.entityId, new GameObjectUpdateContext(10, false, 100));
            check(hero.x == 458 && hero.y == 770, "target and post-wind position remain bounded");
            hero.downed = true;
            f.battle.heroes.updateObject(hero.entityId, new GameObjectUpdateContext(1, false, 100));
            check(hero.x == 458 && hero.y == 770 && hero.vx == 0 && hero.vy == 0 && f.battle.heroes.count() == 1,
                    "downed hero stays owned for rescue and does not move");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .0001f, message); }
    private static final class Visual extends NonEntity {
        @Override protected float windScale() { return .5f; }
        @Override protected float lifetime() { return .5f; }
        void retire() { recycle(); }
    }
    private static final class VisualManager extends AbstractEntityManager<GameObject> {
        int releases;
        VisualManager() { super(new ArrayList<>(), null); }
        @Override protected void release(GameObject object) { releases++; }
    }
    private static final class Fixture implements AutoCloseable {
        final GameState state = new GameState(); final EventManager events = new EventManager(); final FlightBattle battle;
        Fixture() {
            state.stage = 1; state.mode = Mode.SOLO; state.difficulty = Difficulty.NORMAL; state.phase = Phase.RUNNING;
            HeroManager heroes = new HeroManager(state.pilots, events, 1); heroes.create(0, "Hero", 240, 680, false);
            battle = new FlightBattle(state, events, heroes);
        }
        Enemy enemy(int kind, float x, float y) { Enemy result = battle.enemies.spawn(kind, x, y, 20); result.hp = result.maxHp = 100; return result; }
        @Override public void close() { battle.close(); events.close(); }
    }
}
