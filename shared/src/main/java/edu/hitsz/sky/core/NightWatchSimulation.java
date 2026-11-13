package edu.hitsz.sky.core;

import java.util.Random;

final class NightWatchSimulation implements BattleEngine.Simulation {
    private final GameState state;
    private final Random random;
    private final float[] speed = {.006f, .0055f, .0048f};
    private final float[] wait = {5f, 19f, 33f};
    private final GameState.Night night = new GameState.Night();

    NightWatchSimulation(GameState state, long seed) {
        this.state = state; random = new Random(seed);
        state.night = night;
        night.routes[0] = 0; night.routes[1] = 1; night.routes[2] = random.nextInt(2);
        state.notice = "拿起平板看监控，朋友到门口时再关门。";
    }

    @Override public void accept(InputCommand command) {
        boolean on = command.value != 0;
        if (night.energy <= 0 && !state.developer) return;
        switch (command.type) {
            case CAMERA: night.camera = Math.max(0, Math.min(2, command.value)); break;
            case TABLET: night.tabletOpen = on; break;
            case LEFT_DOOR: night.leftDoor = on; break;
            case RIGHT_DOOR: night.rightDoor = on; break;
            case LEFT_LIGHT: night.leftLight = on; break;
            case RIGHT_LIGHT: night.rightLight = on; break;
            default: break;
        }
    }

    @Override public void step(float seconds) {
        night.elapsed = state.elapsed;
        night.hour = Math.min(6, (int) (state.elapsed / 40f));
        float use = .2f + (night.leftDoor ? .65f : 0) + (night.rightDoor ? .65f : 0)
                + (night.leftLight ? .45f : 0) + (night.rightLight ? .45f : 0) + (night.tabletOpen ? .1f : 0);
        night.energy = state.developer ? 100 : Math.max(0, night.energy - use * seconds);
        if (night.energy <= 0) {
            night.leftDoor = false; night.rightDoor = false;
            night.leftLight = false; night.rightLight = false;
            night.tabletOpen = false;
            state.notice = "平板没电了，留意两边的门口。";
        }
        for (int i = 0; i < 3; i++) updateVisitor(i, seconds);
        if (state.phase == Phase.RUNNING && state.elapsed >= StageCatalog.get(6).duration) {
            night.hour = 6; state.phase = Phase.VICTORY;
            state.score = 2000 + Math.round(night.energy * 10);
            for (GameState.Pilot pilot : state.pilots) pilot.score = state.score;
            state.notice = "天亮了！豆丁：早餐我请。";
        }
    }

    private void updateVisitor(int visitor, float seconds) {
        if (state.phase != Phase.RUNNING) return;
        if (wait[visitor] > 0) { wait[visitor] -= seconds; return; }
        boolean left = night.routes[visitor] == 0;
        boolean light = left ? night.leftLight : night.rightLight;
        boolean door = left ? night.leftDoor : night.rightDoor;
        float progress = night.intruders[visitor];
        float pace = speed[visitor] * state.difficulty.paceScale * (1 + night.hour * .1f);
        if (night.tabletOpen && night.camera == visitor && night.energy > 0) pace *= .55f;
        if (light && progress > .66f) pace = -.065f;
        progress = Math.max(0, progress + pace * seconds);
        if (progress > .78f && !light) state.notice = left ? "左边有朋友来啦。" : "右边有朋友来啦。";
        if (progress >= 1f) {
            if (door || state.developer) {
                progress = .02f; wait[visitor] = 7 + random.nextFloat() * 8;
                if (visitor == 2) night.routes[visitor] = random.nextInt(2);
                state.notice = "门外安静下来了，开门省点电。";
            } else {
                state.phase = Phase.DEFEAT;
                state.notice = "机器人把值班室开成了派对。明晚再来！";
            }
        }
        night.intruders[visitor] = Math.min(1, progress);
    }
}
