package edu.hitsz.sky.android;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;

/** Executable JVM checks through the same coordinate boundary as WorldPainter. */
public final class SnapshotMotionTest {
    private static final long SECOND = 1_000_000_000L;
    private static final long PACKET = 50_000_000L;
    private static int checks;

    public static void main(String[] args) {
        networkIntermediateFrames();
        stableIdsAndEntityLifetime();
        immediateAuthorityAndNoMutation();
        pauseAndSceneResets();
        boundedLossAndJitter();
        directLocalAndNightFrames();
        System.out.println("Snapshot motion checks passed: " + checks);
    }

    private static void networkIntermediateFrames() {
        SnapshotMotion motion = new SnapshotMotion();
        GameState before = state(60, 100, 500);
        GameState after = state(63, 130, 470);
        motion.accept(before, SECOND);
        SnapshotMotion.Frame frame = motion.accept(after, SECOND + PACKET);
        near(110, frame.pilotX(0, frame.fraction(SECOND + PACKET + 16_666_667)),
                "20 Hz snapshots must produce a distinct first 60 Hz intermediate position");
        near(490, frame.pilotY(0, frame.fraction(SECOND + PACKET + 16_666_667)), "intermediate y");
        near(100, frame.pilotX(0, frame.fraction(SECOND + PACKET)), "start at previous position");
        near(120, frame.pilotX(0, frame.fraction(SECOND + PACKET + 33_333_333)), "second intermediate frame");
        near(130, frame.pilotX(0, frame.fraction(SECOND + 2 * PACKET)), "reach current snapshot");
        near(130, frame.pilotX(0, frame.fraction(SECOND + 30 * PACKET)), "never extrapolate");
        near(1.025f, frame.elapsed(frame.fraction(SECOND + PACKET + PACKET / 2)), "backdrop time is smooth");
        motion.accept(state(66, 160, 450), SECOND + PACKET * 2);
        near(115, frame.pilotX(0, .5f), "a later packet cannot mutate a published visual frame");
        motion.clear();
        before.mode = after.mode = Mode.VERSUS;
        motion.accept(before, SECOND);
        frame = motion.accept(after, SECOND + PACKET);
        near(115, frame.pilotX(0, frame.fraction(SECOND + PACKET + PACKET / 2)), "versus uses the same motion boundary");
    }

    private static void stableIdsAndEntityLifetime() {
        SnapshotMotion motion = new SnapshotMotion();
        GameState before = state(60, 100, 500);
        before.pilots.add(pilot(1, 300, 500));
        before.enemies.add(body(10, 0, -1, 40, 100));
        before.enemies.add(body(20, 1, -1, 200, 100));
        before.shots.add(body(10, 0, -1, 20, 400));
        before.shots.add(body(30, 0, -1, 60, 400));
        before.pickups.add(body(10, 1, 0, 400, 200));
        GameState after = state(63, 130, 500);
        after.pilots.add(0, pilot(1, 330, 500));
        after.enemies.add(body(20, 1, -1, 220, 100));
        after.enemies.add(body(10, 0, -1, 60, 100));
        after.enemies.add(body(40, 0, -1, 90, 100));
        after.shots.add(body(10, 0, -1, 30, 370));
        after.pickups.add(body(10, 1, 0, 400, 220));
        motion.accept(before, SECOND);
        SnapshotMotion.Frame frame = motion.accept(after, SECOND + PACKET);
        float half = frame.fraction(SECOND + PACKET + PACKET / 2);
        near(315, frame.pilotX(0, half), "pilot identity survives reorder");
        near(115, frame.pilotX(1, half), "other pilot identity survives reorder");
        near(210, frame.enemyX(0, half), "enemy identity survives reorder");
        near(50, frame.enemyX(1, half), "other enemy identity survives reorder");
        near(90, frame.enemyX(2, half), "new entity appears at current position");
        near(25, frame.shotX(0, half), "shot id is independent from enemy id");
        near(385, frame.shotY(0, half), "shot y interpolates");
        near(210, frame.pickupY(0, half), "pickup y interpolates");
        yes(frame.state.shots.size() == 1, "removed projectile disappears immediately");
        GameState reused = state(66, 140, 500);
        reused.pilots.get(0).downed = true;
        reused.enemies.add(body(20, 2, -1, 250, 100));
        reused.shots.add(body(10, 0, 0, 50, 330));
        frame = motion.accept(reused, SECOND + PACKET * 2);
        near(250, frame.enemyX(0, 0), "changed kind starts a new visual entity");
        near(50, frame.shotX(0, 0), "changed owner starts a new projectile");
        near(140, frame.pilotX(0, 0), "downed transition is immediate");
    }

    private static void immediateAuthorityAndNoMutation() {
        SnapshotMotion motion = new SnapshotMotion();
        GameState before = state(60, 100, 500);
        before.enemies.add(body(1, 0, -1, 30, 100));
        GameState after = state(63, 130, 470);
        after.score = 700; after.pilots.get(0).hp = 42; after.pilots.get(0).cooldowns[0] = 4;
        after.enemies.add(body(1, 0, -1, 60, 120));
        after.enemies.get(0).hp = 9;
        motion.accept(before, SECOND);
        SnapshotMotion.Frame frame = motion.accept(after, SECOND + PACKET);
        float half = frame.fraction(SECOND + PACKET + PACKET / 2);
        frame.pilotX(0, half); frame.enemyY(0, half); frame.elapsed(half);
        yes(frame.state == after, "authority object is retained without replacement");
        yes(frame.state.score == 700 && frame.state.pilots.get(0).hp == 42,
                "score and HP expose current authority immediately");
        near(4, frame.state.pilots.get(0).cooldowns[0], "cooldown is never interpolated");
        yes(frame.state.enemies.get(0).hp == 9, "enemy HP is never interpolated");
        near(100, before.pilots.get(0).x, "previous pilot is unchanged");
        near(130, after.pilots.get(0).x, "current pilot is unchanged");
        near(30, before.enemies.get(0).x, "previous body is unchanged");
        near(120, after.enemies.get(0).y, "current body is unchanged");
        near(1.05f, after.elapsed, "current elapsed is unchanged");
    }

    private static void pauseAndSceneResets() {
        SnapshotMotion motion = new SnapshotMotion();
        motion.accept(state(60, 100, 500), SECOND);
        motion.accept(state(63, 130, 500), SECOND + PACKET);
        SnapshotMotion.Frame frame = motion.accept(state(63, 130, 500), SECOND + PACKET + 10_000_000);
        near(130, frame.pilotX(0, frame.fraction(SECOND + PACKET + 20_000_000)), "same tick freezes at authority");
        GameState reset = state(3, 200, 500);
        frame = motion.accept(reset, SECOND + PACKET * 2);
        near(200, frame.pilotX(0, 0), "restarted round resets motion");
        assertReset((state) -> state.stage = 2, "stage change");
        assertReset((state) -> state.phase = Phase.VICTORY, "terminal phase");
        assertReset((state) -> state.phase = Phase.DEFEAT, "defeat phase");
        assertReset((state) -> state.mode = Mode.VERSUS, "mode change");
        assertReset((state) -> state.difficulty = Difficulty.HARD, "difficulty change");
        assertReset((state) -> state.developer = true, "developer change");
        assertReset((state) -> state.elapsed = .1f, "elapsed reset");
        motion.accept(null, SECOND + PACKET * 3);
        frame = motion.accept(state(66, 210, 500), SECOND + PACKET * 4);
        near(210, frame.pilotX(0, 0), "null frame clears previous scene");
        motion.clear();
        frame = motion.accept(state(69, 220, 500), SECOND + PACKET * 5);
        near(220, frame.pilotX(0, 0), "dispose clear forgets prior position");
    }

    private static void boundedLossAndJitter() {
        SnapshotMotion motion = new SnapshotMotion();
        motion.accept(state(60, 100, 500), SECOND);
        SnapshotMotion.Frame frame = motion.accept(state(69, 160, 500), SECOND + 150_000_000);
        near(130, frame.pilotX(0, frame.fraction(SECOND + 200_000_000)), "dropped snapshots cap blend at 100 ms");
        near(160, frame.pilotX(0, frame.fraction(SECOND + 250_000_000)), "blend reaches authority within 100 ms");
        frame = motion.accept(state(72, 190, 500), SECOND + 500_000_000);
        near(190, frame.pilotX(0, 0), "long receive gap resets immediately");
        frame = motion.accept(state(90, 200, 500), SECOND + 550_000_000);
        near(200, frame.pilotX(0, 0), "large simulation gap resets immediately");
        frame = motion.accept(state(93, 350, 500), SECOND + 600_000_000);
        near(350, frame.pilotX(0, 0), "teleport or dash is not swept across screen");
        motion.clear();
        motion.accept(state(60, 100, 500), SECOND);
        frame = motion.accept(state(63, 130, 500), SECOND + PACKET);
        float beforeArrival = frame.pilotX(0, frame.fraction(SECOND + 80_000_000));
        frame = motion.accept(state(66, 160, 500), SECOND + 80_000_000);
        near(beforeArrival, frame.pilotX(0, 0), "early packet continues from already displayed position");
        near(160, frame.pilotX(0, frame.fraction(SECOND + 130_000_000)), "jitter never creates a queued backlog");
        frame = motion.accept(state(69, 180, 500), SECOND + 80_000_000);
        near(180, frame.pilotX(0, 0), "nonincreasing receipt time resets motion");
    }

    private static void directLocalAndNightFrames() {
        for (Mode mode : new Mode[] {Mode.SOLO, Mode.ENDLESS}) {
            SnapshotMotion motion = new SnapshotMotion();
            GameState before = state(60, 100, 500); before.mode = mode;
            GameState after = state(61, 103, 500); after.mode = mode;
            motion.accept(before, SECOND);
            SnapshotMotion.Frame frame = motion.accept(after, SECOND + 16_666_667);
            near(103, frame.pilotX(0, 0), mode + " adds no presentation delay");
            near(after.elapsed, frame.elapsed(0), mode + " time is immediate");
        }
        SnapshotMotion motion = new SnapshotMotion();
        GameState before = state(60, 100, 500); before.stage = 6; before.night = new GameState.Night();
        GameState after = state(63, 130, 500); after.stage = 6; after.night = new GameState.Night();
        after.night.leftDoor = true; after.night.rightLight = true; after.night.camera = 2;
        motion.accept(before, SECOND);
        SnapshotMotion.Frame frame = motion.accept(after, SECOND + PACKET);
        near(130, frame.pilotX(0, 0), "nightwatch is immediate");
        yes(frame.state.night.leftDoor && frame.state.night.rightLight && frame.state.night.camera == 2,
                "nightwatch controls expose current discrete state");
    }

    private interface Change { void apply(GameState state); }

    private static void assertReset(Change change, String label) {
        SnapshotMotion motion = new SnapshotMotion();
        motion.accept(state(60, 100, 500), SECOND);
        GameState after = state(63, 130, 500); change.apply(after);
        SnapshotMotion.Frame frame = motion.accept(after, SECOND + PACKET);
        near(130, frame.pilotX(0, 0), label + " resets motion");
    }

    private static GameState state(long tick, float x, float y) {
        GameState state = new GameState();
        state.tick = tick; state.elapsed = tick / 60f; state.stage = 1;
        state.mode = Mode.COOP; state.difficulty = Difficulty.NORMAL; state.phase = Phase.RUNNING;
        state.pilots.add(pilot(0, x, y));
        return state;
    }

    private static GameState.Pilot pilot(int id, float x, float y) {
        GameState.Pilot pilot = new GameState.Pilot();
        pilot.id = id; pilot.x = x; pilot.y = y; pilot.hp = pilot.maxHp = 100;
        return pilot;
    }

    private static GameState.Body body(int id, int kind, int owner, float x, float y) {
        GameState.Body body = new GameState.Body();
        body.id = id; body.kind = kind; body.owner = owner; body.x = x; body.y = y;
        return body;
    }

    private static void near(float expected, float actual, String message) {
        checks++;
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > .002f) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void yes(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
