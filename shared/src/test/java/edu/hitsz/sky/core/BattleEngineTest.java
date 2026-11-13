package edu.hitsz.sky.core;

import java.util.HashSet;
import java.util.Set;

/** Run with javac/java; intentionally has no Android or test-framework dependency. */
public final class BattleEngineTest {
    private static final float STEP = 1f / 60f;
    private static int assertions;

    public static void main(String[] args) {
        catalogIsOneCampaign();
        snapshotsAreDetached();
        seededSimulationIsRepeatable();
        inputsAndSkillsHaveRealEffects();
        allSupplyEffectsCanBeCollected();
        campaignEndsAfterItsBoss();
        endlessFlightKeepsItsOriginalEnemyRoster();
        normalPilotsCanLose();
        cooperativePilotsCanRescueEachOther();
        nightShiftUsesResourcesAndHasBothEndings();
        System.out.println("BattleEngineTest: " + assertions + " assertions passed");
    }

    private static void catalogIsOneCampaign() {
        check(StageCatalog.all().size() == 5, "five campaign stages");
        for (int stage = 1; stage <= 5; stage++) {
            check(StageCatalog.get(stage).number == stage, "stable stage number");
            check(StageCatalog.get(stage).duration == 60, "one-minute campaign target");
            for (Difficulty difficulty : Difficulty.values()) {
                GameState s = new BattleEngine(stage, difficulty, Mode.SOLO, false, 1).snapshot();
                check(s.stage == stage && s.difficulty == difficulty, "same chapter in each difficulty");
            }
        }
        check(StageCatalog.get(6).number == 6, "night shift exists");
    }

    private static void snapshotsAreDetached() {
        BattleEngine engine = new BattleEngine(1, Difficulty.NORMAL, Mode.COOP, false, 2);
        GameState first = engine.snapshot();
        first.pilots.get(0).hp = -10;
        first.pilots.get(0).cooldowns[0] = 90;
        first.pilots.clear();
        GameState second = engine.snapshot();
        check(second.pilots.size() == 2 && second.pilots.get(0).hp > 0, "snapshot cannot mutate engine");
        check(second.pilots.get(0).cooldowns[0] == 0, "cooldowns copied");
        BattleEngine night = new BattleEngine(6, Difficulty.NORMAL, Mode.SOLO, true, 3);
        GameState frame = night.snapshot();
        frame.night.intruders[0] = 2;
        frame.night.routes[0] = 9;
        check(night.snapshot().night.intruders[0] < 1, "night arrays copied");
        check(night.snapshot().night.routes[0] < 2, "night routes copied");
    }

    private static void seededSimulationIsRepeatable() {
        BattleEngine first = new BattleEngine(3, Difficulty.HARD, Mode.SOLO, true, 827);
        BattleEngine second = new BattleEngine(3, Difficulty.HARD, Mode.SOLO, true, 827);
        for (int tick = 0; tick < 2400; tick++) {
            float x = 240 + (float) Math.sin(tick * .015) * 160;
            first.accept(InputCommand.move(0, x, 620));
            second.accept(InputCommand.move(0, x, 620));
            if (tick % 150 == 0) {
                first.accept(InputCommand.skill(0, tick / 150 % 4));
                second.accept(InputCommand.skill(0, tick / 150 % 4));
            }
            first.step(STEP); second.step(STEP);
        }
        GameState a = first.snapshot(), b = second.snapshot();
        check(a.tick == b.tick && a.score == b.score, "seeded score and time");
        check(a.enemies.size() == b.enemies.size() && a.shots.size() == b.shots.size(), "seeded body counts");
        for (int i = 0; i < a.enemies.size(); i++) {
            check(a.enemies.get(i).x == b.enemies.get(i).x, "seeded enemy positions");
        }
    }

    private static void inputsAndSkillsHaveRealEffects() {
        BattleEngine engine = new BattleEngine(2, Difficulty.NORMAL, Mode.SOLO, false, 8);
        engine.accept(InputCommand.move(0, Float.NaN, 500));
        engine.accept(InputCommand.move(0, -5000, 9000));
        for (int i = 0; i < 120; i++) engine.step(STEP);
        GameState s = engine.snapshot();
        check(s.pilots.get(0).x >= 20 && s.pilots.get(0).y <= 780, "movement bounded");
        engine.accept(InputCommand.skill(0, 1)); engine.step(STEP);
        s = engine.snapshot();
        check(s.pilots.get(0).invulnerable > 3, "shield protects");
        check(s.pilots.get(0).cooldowns[1] > 8, "shield cooldown");
        float beforeDash = s.pilots.get(0).y;
        engine.accept(InputCommand.skill(0, 2)); engine.step(STEP);
        check(engine.snapshot().pilots.get(0).y < beforeDash - 100, "dash moves the pilot");
        engine.accept(InputCommand.skill(0, 0)); engine.step(STEP);
        check(hasEffect(engine.snapshot(), 3), "shockwave creates its effect");
        engine.accept(InputCommand.skill(0, 3)); engine.step(STEP);
        check(hasEffect(engine.snapshot(), 4), "light wave creates its beam");
        BattleEngine developer = new BattleEngine(1, Difficulty.HARD, Mode.SOLO, true, 8);
        for (int i = 0; i < 600; i++) {
            developer.accept(InputCommand.skill(0, i % 4));
            developer.accept(InputCommand.action(0, InputCommand.Type.BOMB, 0));
            developer.step(STEP);
        }
        GameState.Pilot pilot = developer.snapshot().pilots.get(0);
        check(pilot.hp == pilot.maxHp && pilot.bombs > 0, "developer life and bombs unlimited");
        for (float cooldown : pilot.cooldowns) check(cooldown == 0, "developer cooldown unlimited");
    }

    private static void allSupplyEffectsCanBeCollected() {
        BattleEngine engine = new BattleEngine(4, Difficulty.EASY, Mode.ENDLESS, true, 17);
        Set<Integer> collected = new HashSet<>();
        for (int i = 0; i < 9000 && collected.size() < 5; i++) {
            GameState s = engine.snapshot();
            GameState.Pilot pilot = s.pilots.get(0);
            if (!s.pickups.isEmpty()) {
                GameState.Body pickup = s.pickups.get(0);
                engine.accept(InputCommand.move(0, pickup.x, pickup.y));
                if (distance(pilot.x, pilot.y, pickup.x, pickup.y) < 38) {
                    int id = pickup.id, kind = pickup.kind;
                    engine.step(STEP);
                    GameState after = engine.snapshot();
                    if (after.pickups.stream().noneMatch(p -> p.id == id)) {
                        collected.add(kind);
                        if (kind == 1 || kind == 2) check(after.pilots.get(0).weapon == kind, "fire supply changes shooting pattern");
                        if (kind == 4) check(after.freezeSeconds > 4, "freeze supply freezes the world");
                        if (kind == 3) check(after.shots.stream().noneMatch(p -> p.owner < 0), "bomb clears hostile projectiles");
                    }
                    continue;
                }
            } else {
                engine.accept(InputCommand.move(0, 240, 370));
            }
            if (i % 120 == 0) engine.accept(InputCommand.skill(0, 0));
            if (i % 90 == 0) engine.accept(InputCommand.skill(0, 3));
            engine.step(STEP);
        }
        check(collected.size() == 5, "all five supply kinds are collectible: " + collected);
    }

    private static void campaignEndsAfterItsBoss() {
        for (int stage = 1; stage <= 5; stage++) {
            BattleEngine engine = new BattleEngine(stage, Difficulty.NORMAL, Mode.SOLO, true, 99);
            boolean sawBoss = false;
            GameState s = engine.snapshot();
            for (int i = 0; i < 21000 && s.phase == Phase.RUNNING; i++) {
                s = engine.snapshot();
                for (GameState.Body body : s.enemies) {
                    if (body.kind == 4) {
                        sawBoss = true;
                        engine.accept(InputCommand.move(0, body.x, 620));
                    }
                }
                if (i % 30 == 0) engine.accept(InputCommand.skill(0, 3));
                engine.step(STEP);
            }
            s = engine.snapshot();
            check(sawBoss && s.phase == Phase.VICTORY, "stage " + stage + " boss gives victory");
            check(s.elapsed >= StageCatalog.get(stage).duration - 15, "boss follows the forty-five-second waves");
            long endedTick = s.tick;
            for (int i = 0; i < 120; i++) engine.step(STEP);
            check(engine.snapshot().tick == endedTick, "victory freezes simulation");
        }
    }

    private static void normalPilotsCanLose() {
        BattleEngine engine = new BattleEngine(5, Difficulty.HARD, Mode.SOLO, false, 31);
        for (int i = 0; i < 24000 && engine.snapshot().phase == Phase.RUNNING; i++) engine.step(STEP);
        check(engine.snapshot().phase == Phase.DEFEAT, "normal game can be lost");
    }

    private static void endlessFlightKeepsItsOriginalEnemyRoster() {
        BattleEngine engine = new BattleEngine(1, Difficulty.NORMAL, Mode.ENDLESS, true, 58);
        Set<Integer> roster = new HashSet<>();
        for (int i = 0; i < 15000; i++) {
            GameState s = engine.snapshot();
            for (GameState.Body enemy : s.enemies) roster.add(enemy.kind);
            if (i % 240 == 0) engine.accept(InputCommand.skill(0, 0));
            if (i % 30 == 0) engine.accept(InputCommand.skill(0, 3));
            engine.step(STEP);
        }
        check(roster.contains(0) && roster.contains(1) && roster.contains(2) && roster.contains(3), "endless has scout, elite, dart and ace: " + roster);
        check(roster.contains(4), "endless has recurring headliners");
        check(engine.snapshot().phase == Phase.RUNNING, "endless does not stop after a boss");
    }

    private static void cooperativePilotsCanRescueEachOther() {
        BattleEngine engine = new BattleEngine(5, Difficulty.HARD, Mode.COOP, false, 117);
        boolean downed = false, rescued = false;
        for (int i = 0; i < 18000; i++) {
            GameState s = engine.snapshot();
            if (s.phase != Phase.RUNNING) break;
            GameState.Pilot first = s.pilots.get(0), second = s.pilots.get(1);
            if (first.downed) {
                downed = true;
                engine.accept(InputCommand.move(1, first.x, first.y));
                engine.accept(InputCommand.skill(1, 1));
            } else if (downed) {
                rescued = true; break;
            } else if (!s.enemies.isEmpty()) {
                GameState.Body enemy = s.enemies.get(0);
                engine.accept(InputCommand.move(0, enemy.x, enemy.y + 15));
                engine.accept(InputCommand.move(1, 440, 740));
                engine.accept(InputCommand.skill(1, 1));
            }
            if (second.downed) break;
            engine.step(STEP);
        }
        check(downed && rescued, "cooperative pilot can be revived through proximity");
    }

    private static void nightShiftUsesResourcesAndHasBothEndings() {
        BattleEngine normal = new BattleEngine(6, Difficulty.NORMAL, Mode.SOLO, false, 18);
        normal.accept(InputCommand.action(0, InputCommand.Type.LEFT_DOOR, 1));
        normal.accept(InputCommand.action(0, InputCommand.Type.RIGHT_DOOR, 1));
        normal.accept(InputCommand.action(0, InputCommand.Type.LEFT_LIGHT, 1));
        normal.accept(InputCommand.action(0, InputCommand.Type.RIGHT_LIGHT, 1));
        normal.accept(InputCommand.action(0, InputCommand.Type.CAMERA, 2));
        for (int i = 0; i < 1800; i++) normal.step(STEP);
        GameState s = normal.snapshot();
        check(s.night.energy < 40 && s.night.energy >= 0, "doors and lights spend power");
        check(s.night.camera == 2, "camera selection works");
        for (int i = 0; i < 15000 && normal.snapshot().phase == Phase.RUNNING; i++) normal.step(STEP);
        check(normal.snapshot().phase == Phase.DEFEAT, "unguarded night can end in a friendly failure");
        BattleEngine developer = new BattleEngine(6, Difficulty.HARD, Mode.COOP, true, 18);
        developer.accept(InputCommand.action(0, InputCommand.Type.LEFT_DOOR, 1));
        developer.accept(InputCommand.action(1, InputCommand.Type.RIGHT_DOOR, 1));
        for (int i = 0; i < 15000; i++) developer.step(STEP);
        s = developer.snapshot();
        check(s.phase == Phase.VICTORY && s.night.hour == 6, "night reaches dawn");
        check(s.night.energy == 100, "developer power unlimited");
        BattleEngine guarded = new BattleEngine(6, Difficulty.NORMAL, Mode.SOLO, false, 18);
        for (int i = 0; i < 15000 && guarded.snapshot().phase == Phase.RUNNING; i++) {
            GameState.Night n = guarded.snapshot().night;
            boolean left = false, right = false;
            for (int visitor = 0; visitor < 3; visitor++) {
                if (n.intruders[visitor] > .93f) {
                    if (n.routes[visitor] == 0) left = true; else right = true;
                }
            }
            guarded.accept(InputCommand.action(0, InputCommand.Type.LEFT_DOOR, left ? 1 : 0));
            guarded.accept(InputCommand.action(0, InputCommand.Type.RIGHT_DOOR, right ? 1 : 0));
            guarded.step(STEP);
        }
        check(guarded.snapshot().phase == Phase.VICTORY, "ordinary night is winnable with resource management");
        check(guarded.snapshot().night.energy > 0, "careful door use leaves battery for dawn");
    }

    private static boolean hasEffect(GameState state, int kind) {
        return state.effects.stream().anyMatch(body -> body.kind == kind);
    }
    private static float distance(float x, float y, float a, float b) {
        return (float) Math.hypot(x - a, y - b);
    }
    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
