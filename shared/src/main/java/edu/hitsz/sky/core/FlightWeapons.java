package edu.hitsz.sky.core;

final class FlightWeapons {
    private static final float[] COOLDOWNS = {8, 10, 6, 13};
    private final FlightSimulation world;
    private final GameState state;

    FlightWeapons(FlightSimulation world) { this.world = world; state = world.state; }

    void shootHero(GameState.Pilot pilot) {
        if (pilot.weapon == 2) {
            for (int i = 0; i < 8; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 4;
                shot(pilot.id, 0, pilot.x, pilot.y - 18, (float) Math.cos(angle) * 440, (float) Math.sin(angle) * 440, 14, 5);
            }
        } else if (pilot.weapon == 1) {
            for (int i = -1; i <= 1; i++) shot(pilot.id, 0, pilot.x + i * 9, pilot.y - 20, i * 105, -500, 15, 5);
        } else {
            shot(pilot.id, 0, pilot.x, pilot.y - 22, 0, -520, 17, 5);
        }
    }

    void skill(GameState.Pilot pilot, int slot) {
        if (pilot.cooldowns[slot] > 0 && !state.developer) return;
        pilot.cooldowns[slot] = COOLDOWNS[slot];
        switch (slot) {
            case 0:
                world.effect(3, pilot.id, pilot.x, pilot.y, 245);
                for (int i = state.shots.size() - 1; i >= 0; i--) {
                    GameState.Body shot = state.shots.get(i);
                    if (shot.owner < 0 && WorldMath.distance(pilot.x, pilot.y, shot.x, shot.y) < 245) state.shots.remove(i);
                }
                for (int i = state.enemies.size() - 1; i >= 0; i--) {
                    GameState.Body enemy = state.enemies.get(i);
                    if (WorldMath.distance(pilot.x, pilot.y, enemy.x, enemy.y) < 245 + enemy.radius) {
                        world.damageEnemy(i, enemy.kind == 4 ? 45 : enemy.kind == 3 ? 55 : 110, pilot.id);
                    }
                }
                world.notice("冲击波！"); break;
            case 1:
                pilot.invulnerable = Math.max(pilot.invulnerable, 4);
                world.notice("护盾撑住了 · 4秒"); break;
            case 2: world.dash(pilot); world.notice("冲过去！"); break;
            case 3:
                world.effect(4, pilot.id, pilot.x, pilot.y, 65);
                for (int i = state.enemies.size() - 1; i >= 0; i--) {
                    GameState.Body enemy = state.enemies.get(i);
                    if (Math.abs(enemy.x - pilot.x) < enemy.radius + 60 && enemy.y <= pilot.y + 25) world.damageEnemy(i, 280, pilot.id);
                }
                for (int i = state.shots.size() - 1; i >= 0; i--) {
                    GameState.Body shot = state.shots.get(i);
                    if (shot.owner < 0 && Math.abs(shot.x - pilot.x) < 65 && shot.y < pilot.y) state.shots.remove(i);
                }
                world.notice("光波开路！"); break;
            default: break;
        }
    }

    void bomb(GameState.Pilot pilot) {
        if (pilot.bombs <= 0 && !state.developer) return;
        if (!state.developer) pilot.bombs--;
        detonate(pilot); world.notice("轰！弹幕清空了。");
    }

    void detonate(GameState.Pilot pilot) {
        state.shots.removeIf(shot -> shot.owner < 0);
        for (int i = state.enemies.size() - 1; i >= 0; i--) {
            GameState.Body enemy = state.enemies.get(i);
            if (enemy.kind == 4) continue;
            if (enemy.kind == 3) world.damageEnemy(i, 60, pilot.id);
            else world.destroyEnemy(i, pilot.id);
        }
        world.effect(3, pilot.id, 240, 400, 700);
    }

    void aimed(GameState.Body enemy, int count, float speed, float spread, int damage) {
        GameState.Pilot target = null;
        float best = Float.MAX_VALUE;
        for (GameState.Pilot pilot : state.pilots) {
            float distance = WorldMath.distance(enemy.x, enemy.y, pilot.x, pilot.y);
            if (!pilot.downed && distance < best) { best = distance; target = pilot; }
        }
        if (target == null) return;
        double center = Math.atan2(target.y - enemy.y, target.x - enemy.x);
        for (int i = 0; i < count; i++) {
            double angle = center + (i - (count - 1) / 2f) * spread;
            shot(-1, 1, enemy.x, enemy.y + enemy.radius, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, damage, 6);
        }
    }

    void ring(GameState.Body enemy, int count, float speed, float angle, int damage) {
        for (int i = 0; i < count; i++) {
            double direction = angle + i * Math.PI * 2 / count;
            shot(-1, 1, enemy.x, enemy.y + 14, (float) Math.cos(direction) * speed, (float) Math.sin(direction) * speed, damage, 6);
        }
    }

    void shot(int owner, int kind, float x, float y, float vx, float vy, int damage, float radius) {
        if (state.shots.size() >= FlightSimulation.MAX_SHOTS) return;
        GameState.Body shot = world.body(kind, owner, x, y, radius);
        shot.vx = vx; shot.vy = vy; shot.hp = damage; shot.maxHp = damage;
        shot.angle = (float) Math.atan2(vy, vx);
        state.shots.add(shot);
    }
}
