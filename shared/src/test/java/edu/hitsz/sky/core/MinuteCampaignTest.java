package edu.hitsz.sky.core;

/** Public pacing contract for the short campaign. */
public final class MinuteCampaignTest {
    public static void main(String[] args) {
        for (int stage = 1; stage <= 5; stage++) {
            check(StageCatalog.get(stage).duration == 60f, "stage " + stage + " targets one minute");
        }
        check(StageCatalog.get(6).duration == 240f, "night shift retains four minutes");
        check(java.util.Arrays.equals(StageCatalog.selectable(1, false), new int[]{1, 6}), "normal newcomer can select extra night without unlocking stage two");
        check(!StageCatalog.available(2, 1, false) && StageCatalog.available(6, 1, false), "night access preserves campaign locks");
        check(StageCatalog.selectable(1, true).length == 6 && !StageCatalog.available(7, 5, true), "developer selection stays within catalog");
        tabletUsesPowerAndOnlyWatchesWhenRaised();
        burstCannotSkipBossSecondPattern();
        for (Difficulty difficulty : Difficulty.values()) for (int stage = 1; stage <= 5; stage++) {
            BattleEngine engine = new BattleEngine(stage, difficulty, Mode.SOLO, true, 827);
            float secondPhaseAt = -1;
            for (int tick = 0; tick < 18000 && engine.snapshot().phase == Phase.RUNNING; tick++) {
                GameState s = engine.snapshot();
                for (GameState.Body enemy : s.enemies) if (enemy.kind == 4) {
                    engine.accept(InputCommand.move(0, enemy.x, 680));
                    if ((enemy.flags & 2) != 0 && secondPhaseAt < 0) secondPhaseAt = s.elapsed;
                }
                engine.step(1f / 60);
            }
            GameState s = engine.snapshot();
            check(s.phase == Phase.VICTORY, "short campaign still ends by defeating boss");
            check(secondPhaseAt > 0 && s.elapsed - secondPhaseAt >= 2.95f, "second boss pattern remains playable");
            check(s.elapsed >= 45 && s.elapsed < 100, "short campaign pacing remains bounded");
            System.out.printf(java.util.Locale.ROOT, "%s stage %d: %.2fs (developer survival, automatic fire only)%n", difficulty, stage, s.elapsed);
        }
        System.out.println("MinuteCampaignTest: short campaign, normal night access and authoritative tablet passed");
    }

    private static void tabletUsesPowerAndOnlyWatchesWhenRaised() {
        BattleEngine closed = new BattleEngine(6, Difficulty.NORMAL, Mode.COOP, false, 18);
        BattleEngine raised = new BattleEngine(6, Difficulty.NORMAL, Mode.COOP, false, 18);
        check(!closed.snapshot().night.tabletOpen, "tablet begins on desk");
        raised.accept(InputCommand.action(1, InputCommand.Type.TABLET, 1));
        for (int i = 0; i < 3600; i++) { closed.step(1f/60); raised.step(1f/60); }
        GameState s = raised.snapshot();
        check(s.phase == Phase.RUNNING && s.night.hour == 1, "night does not end at sixty seconds");
        check(s.night.energy < closed.snapshot().night.energy - 5, "raised tablet consumes additional power");
        check(s.night.intruders[0] < closed.snapshot().night.intruders[0] * .6f, "only raised selected camera slows visitor");
        s.night.tabletOpen = false;
        check(raised.snapshot().night.tabletOpen, "snapshot cannot close authoritative tablet");
        raised.accept(InputCommand.action(0, InputCommand.Type.LEFT_DOOR, 1));
        raised.accept(InputCommand.action(1, InputCommand.Type.RIGHT_DOOR, 1));
        raised.accept(InputCommand.action(0, InputCommand.Type.LEFT_LIGHT, 1));
        raised.accept(InputCommand.action(1, InputCommand.Type.RIGHT_LIGHT, 1));
        for (int i=0;i<2400;i++) raised.step(1f/60);
        check(raised.snapshot().night.energy == 0 && !raised.snapshot().night.tabletOpen, "flat battery lowers tablet");
    }

    private static void burstCannotSkipBossSecondPattern() {
        BattleEngine engine = new BattleEngine(1, Difficulty.EASY, Mode.SOLO, true, 827);
        for (int i=0;i<2701;i++) engine.step(1f/60);
        boolean second = false;
        for (int i=0;i<150;i++) {
            GameState s = engine.snapshot();
            for (GameState.Body body : s.enemies) if (body.kind == 4) {
                engine.accept(InputCommand.move(0,body.x,680));
                second |= (body.flags & 2) != 0;
            }
            engine.accept(InputCommand.skill(0,3)); engine.step(1f/60);
            check(engine.snapshot().phase == Phase.RUNNING, "repeated bursts cannot skip second pattern");
        }
        check(second, "burst actually reached second phase");
        for (int i=0;i<150 && engine.snapshot().phase==Phase.RUNNING;i++) {
            engine.accept(InputCommand.skill(0,3)); engine.step(1f/60);
        }
        check(engine.snapshot().phase==Phase.VICTORY, "boss can be finished after its phase window");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
