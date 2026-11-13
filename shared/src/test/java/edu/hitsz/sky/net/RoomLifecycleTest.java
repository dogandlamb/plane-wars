package edu.hitsz.sky.net;

import edu.hitsz.sky.core.Difficulty;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;

public final class RoomLifecycleTest {
    public static void main(String[] arguments) {
        RoomLifecycle room = new RoomLifecycle(new RoomOptions(1, Difficulty.HARD, Mode.COOP, true, 91));
        check(room.beginStory(), "initial story");
        check(room.ready(0) == RoomLifecycle.ReadyAction.NONE, "one member cannot skip story barrier");
        room.suspend(0, true);
        check(room.ready(1) == RoomLifecycle.ReadyAction.BEGIN_BATTLE, "both story confirmations start battle");
        check(room.paused(), "background state before ready survives start");
        room.pause(false);
        check(room.paused(), "another member cannot clear background pause");
        room.suspend(0, false);
        check(!room.paused(), "own return clears background pause");
        room.pause(true); room.pause(false);
        check(!room.paused(), "manual room pause remains resumable");
        check(room.finish(Phase.VICTORY, Phase.VICTORY), "first terminal transition");
        check(!room.finish(Phase.VICTORY, Phase.VICTORY), "repeat terminal frames do not reset confirmations");
        check(room.ready(0) == RoomLifecycle.ReadyAction.NONE && room.options.stage == 1, "one result confirmation waits");
        check(room.ready(1) == RoomLifecycle.ReadyAction.SHOW_STORY && room.options.stage == 2, "both continue to next stage story");
        check(room.options.difficulty == Difficulty.HARD && room.options.mode == Mode.COOP && room.options.developer, "room settings preserved");
        check(!room.running && !room.finished, "new story cannot run battle early");
        room.ready(0); room.ready(1);
        room.finish(Phase.DEFEAT, Phase.DEFEAT);
        room.ready(0); room.ready(1);
        check(room.options.stage == 2, "defeat retries current stage");
        room.suspend(1, true); room.retry();
        room.ready(0); room.ready(1);
        check(room.options.stage == 2 && room.paused(), "retry preserves background ownership");
        for (int stage : new int[]{5, 6}) {
            RoomLifecycle finalRoom = new RoomLifecycle(new RoomOptions(stage, Difficulty.NORMAL, Mode.COOP, true, 10));
            finalRoom.beginStory(); finalRoom.ready(0); finalRoom.ready(1);
            finalRoom.finish(Phase.VICTORY, Phase.VICTORY); finalRoom.ready(0); finalRoom.ready(1);
            check(finalRoom.options.stage == stage, "final stage confirmation retries the same stage");
        }
        RoomLifecycle versus = new RoomLifecycle(new RoomOptions(3, Difficulty.EASY, Mode.VERSUS, true, 1));
        versus.beginStory(); versus.ready(0); versus.ready(1);
        check(!versus.finish(Phase.VICTORY, Phase.RUNNING), "versus waits for both outcomes");
        versus.finish(Phase.VICTORY, Phase.DEFEAT); versus.ready(0); versus.ready(1);
        check(versus.options.stage == 3, "mixed versus outcome retries current stage");
        versus.ready(0); versus.ready(1);
        versus.finish(Phase.VICTORY, Phase.VICTORY); versus.ready(0); versus.ready(1);
        check(versus.options.stage == 4, "both versus victories advance the shared route");
        System.out.println("RoomLifecycleTest: background ownership, next stage, defeat/retry and final-stage flow passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
