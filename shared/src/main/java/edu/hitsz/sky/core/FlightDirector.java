package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.List;

final class FlightDirector {
    private final FlightSimulation world;
    private final GameState state;
    private final boolean[] introduced = new boolean[8];
    private final List<Thunder> warnings = new ArrayList<>();
    private float spawnClock = .8f, nextBoss;
    private boolean bossPresent;
    private int bossSpawns;

    FlightDirector(FlightSimulation world) {
        this.world = world; state = world.state;
        world.battle.events.subscribe(BossDiedEvent.class, event -> bossDefeated());
        world.battle.events.subscribe(BattleEndedEvent.class, event -> warnings.clear());
        nextBoss = state.mode == Mode.ENDLESS ? state.difficulty == Difficulty.EASY ? Float.POSITIVE_INFINITY : 65f : world.stage.duration - 15f;
    }

    boolean bossPresent() { return bossPresent; }
    float wind() { return (float) Math.sin(state.elapsed * .19f) * 38; }

    void step(float seconds) {
        if (!world.battle.enemies.isAcceptingSpawns()) return;
        updateWarnings(seconds);
        if (!bossPresent && state.elapsed >= nextBoss) spawnBoss();
        if (!bossPresent) {
            spawnClock -= seconds;
            if (spawnClock <= 0) {
                spawnClock += Math.max(.48f, (1.36f - state.stage * .105f) / state.difficulty.paceScale / endlessSpeed());
                int limit = Math.min(FlightSimulation.MAX_ENEMIES - 1, 8 + state.stage * 2);
                if (state.enemies.size() < limit) spawnEnemy(pickKind(), 35 + world.random.nextFloat() * 410, -28);
            }
        }
        int count = state.enemies.size();
        for (int i = count - 1; i >= 0; i--) {
            if (i >= state.enemies.size()) continue;
            GameState.Body enemy = state.enemies.get(i);
            float factor = state.freezeSeconds > 0 && enemy.kind != 4 ? enemy.kind == 3 ? .35f : 0 : 1;
            float step = seconds * factor;
            enemy.age += step;
            if (enemy.kind == 4) updateBoss(enemy, step);
            else updateEnemy(enemy, step);
            if (enemy.y > 850 || enemy.x < -95 || enemy.x > 575) world.battle.enemies.remove(enemy);
        }
        if (state.stage == 2 && WorldMath.crossed(state.elapsed, seconds, 14)) world.notice(wind() < 0 ? "侧风向左，稳住机翼。" : "侧风向右，稳住机翼。");
        if (state.stage == 3 && !bossPresent && WorldMath.crossed(state.elapsed, seconds, 18)) {
            warn(65 + world.random.nextFloat() * 350, "hostile.23");
        }
    }

    private int pickKind() {
        float chance = world.random.nextFloat();
        if (state.mode == Mode.ENDLESS) {
            if (state.difficulty == Difficulty.HARD) return chance < .4f ? 0 : chance < .68f ? 1 : chance < .86f ? 2 : 3;
            return chance < .55f ? 0 : chance < .8f ? 1 : chance < .92f ? 2 : 3;
        }
        switch (state.stage) {
            case 1: return chance < .62f ? 0 : 1;
            case 2: return chance < .2f ? 0 : chance < .48f ? 1 : 2;
            case 3: return chance < .15f ? 0 : chance < .35f ? 1 : chance < .55f ? 2 : chance < .76f ? 3 : 7;
            case 4: return chance < .2f ? 1 : chance < .4f ? 2 : chance < .62f ? 3 : chance < .86f ? 5 : 7;
            default: return chance < .12f ? 2 : chance < .34f ? 3 : chance < .55f ? 5 : chance < .82f ? 6 : 7;
        }
    }

    private void spawnEnemy(int kind, float x, float y) {
        if (!world.battle.enemies.isAcceptingSpawns() || state.enemies.size() >= FlightSimulation.MAX_ENEMIES) return;
        int[] health = {27, 48, 56, 110, 1, 98, 140, 85};
        float[] speeds = {83, 67, 114, 59, 0, 56, 45, 42};
        GameState.Body enemy = world.battle.enemies.spawn(kind, x, y, kind == 6 ? 29 : kind == 3 ? 24 : 21);
        if (enemy == null) return;
        enemy.hp = Math.max(1, Math.round((health[kind] + (state.stage - 1) * 4 + endlessHealthBonus()) * state.difficulty.healthScale));
        enemy.maxHp = enemy.hp;
        enemy.vy = speeds[kind] * (.9f + .1f * state.difficulty.paceScale) * endlessSpeed();
        enemy.vx = (world.random.nextBoolean() ? 1 : -1) * (kind == 2 ? 78 : kind == 3 ? 48 : 20);
        if (!introduced[kind]) {
            introduced[kind] = true;
            switch (kind) {
                case 2: world.notice("两翼来客！快递机喜欢走折线。"); break;
                case 3: world.notice("王牌不怕一发炸弹，冰冻也只能减速。"); break;
                case 5: world.notice("护盾有开合，等它露出空隙。"); break;
                case 6: world.notice("运输机还带着小伙伴，别让它们散开。"); break;
                case 7: world.notice("雷云先闪后落，离开亮起的航道。"); break;
                default: break;
            }
        }
    }

    private void updateEnemy(GameState.Body enemy, float seconds) {
        enemy.y += enemy.vy * seconds;
        if (enemy.kind == 2 || enemy.kind == 3) enemy.x += (float) Math.sin(enemy.age * 2.2f + enemy.id) * enemy.vx * seconds;
        else if (enemy.kind == 7) enemy.x += (float) Math.cos(enemy.age * 1.3f) * enemy.vx * seconds;
        enemy.x = WorldMath.clamp(enemy.x, 24, 456);
        float pace = state.difficulty.paceScale;
        switch (enemy.kind) {
            case 1:
                if (WorldMath.crossed(enemy.age, seconds, 1.8f / pace)) world.weapons.aimed(enemy, 1, 142 * pace, 0, "hostile.10");
                break;
            case 2:
                if (WorldMath.crossed(enemy.age, seconds, 2.1f / pace)) world.weapons.aimed(enemy, 2, 158 * pace, .22f, "hostile.9");
                break;
            case 3:
                if (WorldMath.crossed(enemy.age, seconds, 1.9f / pace)) world.weapons.aimed(enemy, 3, 148 * pace, .23f, "hostile.11");
                break;
            case 5:
                enemy.flags = (int) (enemy.age / 1.8f) % 2 == 0 ? 1 : 0;
                if (WorldMath.crossed(enemy.age, seconds, 2.4f / pace)) world.weapons.aimed(enemy, 3, 140 * pace, .28f, "hostile.10");
                break;
            case 6:
                if (enemy.y < 380 && WorldMath.crossed(enemy.age, seconds, 4.5f)) {
                    spawnEnemy(0, WorldMath.clamp(enemy.x - 36, 25, 455), enemy.y + 12);
                    spawnEnemy(0, WorldMath.clamp(enemy.x + 36, 25, 455), enemy.y + 12);
                }
                if (WorldMath.crossed(enemy.age, seconds, 2.2f / pace)) world.weapons.aimed(enemy, 3, 135 * pace, .35f, "hostile.11");
                break;
            case 7:
                if (WorldMath.crossed(enemy.age, seconds, 4.5f / pace)) warn(enemy.x, "hostile.20");
                break;
            default: break;
        }
    }

    private void spawnBoss() {
        GameState.Body boss = world.battle.enemies.spawn(4, 240, -65, 51 + state.stage * 2);
        if (boss == null) return;
        bossPresent = true;
        bossSpawns++;
        float team = state.mode == Mode.COOP ? 1.65f : 1;
        int repeatBonus = state.mode == Mode.ENDLESS && state.difficulty == Difficulty.HARD ? (bossSpawns - 1) * 80 : 0;
        int health = state.mode == Mode.ENDLESS ? 650 + state.stage * 210 + repeatBonus : 360 + state.stage * 65;
        boss.hp = Math.round(health * state.difficulty.healthScale * team);
        boss.maxHp = boss.hp;
        world.notice(bossName() + "来了！先看清它的航线。");
    }

    private String bossName() {
        switch (state.stage) {
            case 1: return "邮港巡逻艇";
            case 2: return "风车领航机";
            case 3: return "积雨云领队";
            case 4: return "工厂守门员";
            default: return "大块头运输舰";
        }
    }

    private void updateBoss(GameState.Body boss, float seconds) {
        boss.y = WorldMath.approach(boss.y, 115, seconds * 70);
        boss.x = 240 + (float) Math.sin(boss.age * (.4f + state.stage * .025f)) * (88 + state.stage * 8);
        if (boss.y < 70) return;
        boolean second = (boss.flags & 2) != 0;
        float pace = state.difficulty.paceScale * (second ? 1.42f : 1);
        float period = Math.max(.7f, 1.7f - state.stage * .09f) / pace;
        if (WorldMath.crossed(boss.age, seconds, period)) {
            switch (state.stage) {
                case 1: world.weapons.aimed(boss, second ? 5 : 3, 148 * pace, .22f, "hostile.12"); break;
                case 2:
                    world.weapons.aimed(boss, 3, 155 * pace, .28f, "hostile.12");
                    world.weapons.shot(-1, 1, boss.x - 44, boss.y + 25, -85, 195 * pace, "hostile.11", 7);
                    world.weapons.shot(-1, 1, boss.x + 44, boss.y + 25, 85, 195 * pace, "hostile.11", 7);
                    break;
                case 3: world.weapons.ring(boss, second ? 16 : 10, 137 * pace, boss.age * .32f, "hostile.12"); break;
                case 4: world.weapons.aimed(boss, second ? 7 : 5, 148 * pace, .19f, "hostile.13"); break;
                default:
                    if (second) world.weapons.ring(boss, 18, 148 * pace, boss.age * .42f, "hostile.13");
                    else world.weapons.aimed(boss, 5, 164 * pace, .25f, "hostile.13");
                    break;
            }
        }
        if (state.stage == 1 && second && WorldMath.crossed(boss.age, seconds, 4.1f)) world.weapons.ring(boss, 10, 120, boss.age, "hostile.10");
        if (state.stage == 3 && WorldMath.crossed(boss.age, seconds, second ? 2.8f : 4.3f)) warn(targetX(), "hostile.25");
        if (state.stage == 4) {
            boss.flags = second ? boss.flags & ~1 : (int) (boss.age / 2.3f) % 2 == 0 ? boss.flags | 1 : boss.flags & ~1;
            if (WorldMath.crossed(boss.age, seconds, second ? 2.3f : 4f)) {
                warn(targetX(), "hostile.26");
                if (second) warn(WorldMath.clamp(targetX() + 95, 28, 452), "hostile.22");
            }
        }
        if (state.stage == 5 && WorldMath.crossed(boss.age, seconds, second ? 3.8f : 5.5f)) {
            if (second) warn(targetX(), "hostile.24");
            else spawnEnemy(2, world.random.nextBoolean() ? 50 : 430, 100);
        }
    }

    private float targetX() {
        for (GameState.Pilot pilot : state.pilots) if (!pilot.downed) return pilot.x;
        return 240;
    }

    private void warn(float x, String tag) {
        if (warnings.size() >= 5) return;
        Thunder thunder = new Thunder(); thunder.x = x; thunder.tag = tag;
        warnings.add(thunder); world.effect(4, -1, x, 25, 25);
    }

    private void updateWarnings(float seconds) {
        for (int i = warnings.size() - 1; i >= 0; i--) {
            Thunder warning = warnings.get(i);
            warning.delay -= seconds;
            if (warning.delay <= 0) {
                world.weapons.shot(-1, 2, warning.x, -25, 0, 540, warning.tag, 23);
                warnings.remove(i);
            }
        }
    }

    void bossDefeated() {
        bossPresent = false;
        if (state.mode == Mode.ENDLESS) nextBoss = state.elapsed + 65;
    }

    private float endlessSpeed() {
        if (state.mode != Mode.ENDLESS || state.difficulty == Difficulty.EASY) return 1;
        float growth = state.difficulty == Difficulty.HARD ? .1f : .08f;
        float cap = state.difficulty == Difficulty.HARD ? 2.2f : 1.8f;
        return Math.min(cap, 1 + (int) (state.elapsed / 10) * growth);
    }

    private int endlessHealthBonus() {
        if (state.mode != Mode.ENDLESS || state.difficulty == Difficulty.EASY) return 0;
        int growth = state.difficulty == Difficulty.HARD ? 16 : 10;
        int cap = state.difficulty == Difficulty.HARD ? 220 : 120;
        return Math.min(cap, (int) (state.elapsed / 10) * growth);
    }

    private static final class Thunder {
        float x, delay = 1.05f;
        String tag;
    }
}
