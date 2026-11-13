package edu.hitsz.sky.core;

import java.util.Random;

final class FlightSimulation implements BattleEngine.Simulation {
    static final int MAX_SHOTS = 512, MAX_ENEMIES = 24;
    final GameState state;
    final StageCatalog.Stage stage;
    final Random random;
    final FlightWeapons weapons;
    final FlightDirector director;
    private final float[] targetX = new float[2], targetY = new float[2], shotClock = new float[2];
    private final int[] pendingSkills = new int[2];
    private final boolean[] pendingBombs = new boolean[2];
    private int nextId = 1, supplySequence, specialKills;
    private float noticeSeconds = 5, bossPhaseUntil;

    FlightSimulation(GameState state, long seed) {
        this.state = state; stage = StageCatalog.get(state.stage); random = new Random(seed);
        for (GameState.Pilot pilot : state.pilots) {
            targetX[pilot.id] = pilot.x; targetY[pilot.id] = pilot.y;
        }
        weapons = new FlightWeapons(this); director = new FlightDirector(this);
    }

    @Override public void accept(InputCommand command) {
        GameState.Pilot pilot = state.pilots.get(command.playerId);
        if (pilot.downed) return;
        switch (command.type) {
            case MOVE:
                if (Float.isFinite(command.x) && Float.isFinite(command.y)) {
                    targetX[pilot.id] = WorldMath.clamp(command.x, 22, 458);
                    targetY[pilot.id] = WorldMath.clamp(command.y, 100, 770);
                }
                break;
            case SKILL:
                if (command.value >= 0 && command.value < 4) pendingSkills[pilot.id] |= 1 << command.value;
                break;
            case BOMB: pendingBombs[pilot.id] = true; break;
            default: break;
        }
    }

    @Override public void step(float seconds) {
        state.freezeSeconds = Math.max(0, state.freezeSeconds - seconds);
        noticeSeconds -= seconds;
        if (noticeSeconds <= 0) state.notice = director.bossPresent() ? "找到空隙，再靠近一点。" : stage.subtitle;
        updateEffects(seconds);
        updatePilots(seconds);
        if (state.phase != Phase.RUNNING) return;
        director.step(seconds);
        for (GameState.Pilot pilot : state.pilots) {
            if (pilot.downed) continue;
            shotClock[pilot.id] -= seconds;
            if (shotClock[pilot.id] <= 0) {
                weapons.shootHero(pilot);
                shotClock[pilot.id] += .17f;
            }
        }
        moveProjectiles(seconds);
        resolveCollisions();
        if (state.phase == Phase.RUNNING) rescuePilots(seconds);
    }

    private void updatePilots(float seconds) {
        for (GameState.Pilot pilot : state.pilots) {
            if (state.developer) {
                pilot.hp = pilot.maxHp; pilot.downed = false;
                pilot.bombs = 99; pilot.invulnerable = 1;
            } else {
                pilot.invulnerable = Math.max(0, pilot.invulnerable - seconds);
            }
            for (int slot = 0; slot < 4; slot++) {
                pilot.cooldowns[slot] = state.developer ? 0 : Math.max(0, pilot.cooldowns[slot] - seconds);
            }
            pilot.weaponSeconds = Math.max(0, pilot.weaponSeconds - seconds);
            if (pilot.weaponSeconds == 0) pilot.weapon = 0;
            if (pilot.downed) { pendingSkills[pilot.id] = 0; pendingBombs[pilot.id] = false; continue; }
            float dx = targetX[pilot.id] - pilot.x, dy = targetY[pilot.id] - pilot.y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float speed = Math.min(1, 470 * seconds / Math.max(.001f, length));
            pilot.x += dx * speed; pilot.y += dy * speed;
            if (state.stage == 2) pilot.x += director.wind() * seconds;
            pilot.x = WorldMath.clamp(pilot.x, 22, 458);
            pilot.y = WorldMath.clamp(pilot.y, 100, 770);
            int skills = pendingSkills[pilot.id]; pendingSkills[pilot.id] = 0;
            for (int slot = 0; slot < 4; slot++) if ((skills & 1 << slot) != 0) weapons.skill(pilot, slot);
            if (pendingBombs[pilot.id]) weapons.bomb(pilot);
            pendingBombs[pilot.id] = false;
            if (state.developer) for (int slot = 0; slot < 4; slot++) pilot.cooldowns[slot] = 0;
            float shortest = pilot.cooldowns[0];
            for (int slot = 1; slot < 4; slot++) shortest = Math.min(shortest, pilot.cooldowns[slot]);
            pilot.charge = 1 - Math.min(1, shortest / 6f);
        }
    }

    void dash(GameState.Pilot pilot) {
        float oldY = pilot.y;
        pilot.y = Math.max(100, pilot.y - 180);
        targetY[pilot.id] = pilot.y;
        pilot.invulnerable = Math.max(pilot.invulnerable, 1.3f);
        for (int i = state.enemies.size() - 1; i >= 0; i--) {
            GameState.Body enemy = state.enemies.get(i);
            if (Math.abs(enemy.x - pilot.x) < enemy.radius + 28 && enemy.y > pilot.y - 30 && enemy.y < oldY + 30) {
                damageEnemy(i, 85, pilot.id);
            }
        }
        effect(5, pilot.id, pilot.x, oldY, 30);
    }

    private void moveProjectiles(float seconds) {
        float windStep = state.stage == 2 ? director.wind() * seconds * .7f : 0;
        for (int i = state.shots.size() - 1; i >= 0; i--) {
            GameState.Body shot = state.shots.get(i);
            shot.age += seconds;
            float factor = shot.owner < 0 && state.freezeSeconds > 0 ? 0 : 1;
            shot.x += shot.vx * seconds * factor;
            shot.y += shot.vy * seconds * factor;
            if (state.stage == 2) shot.x += windStep * factor;
            if (shot.x < -40 || shot.x > 520 || shot.y < -70 || shot.y > 860 || shot.age > 14) state.shots.remove(i);
        }
        for (int i = state.pickups.size() - 1; i >= 0; i--) {
            GameState.Body pickup = state.pickups.get(i);
            pickup.age += seconds; pickup.y += 58 * seconds;
            if (pickup.y > 840) state.pickups.remove(i);
        }
    }

    private void resolveCollisions() {
        for (int i = state.shots.size() - 1; i >= 0 && state.phase == Phase.RUNNING; i--) {
            GameState.Body shot = state.shots.get(i);
            if (shot.owner >= 0) {
                for (int j = state.enemies.size() - 1; j >= 0; j--) {
                    GameState.Body enemy = state.enemies.get(j);
                    if (!touch(shot, enemy.x, enemy.y, enemy.radius)) continue;
                    state.shots.remove(i);
                    if ((enemy.flags & 1) == 0 || Math.abs(shot.vx) > 65) damageEnemy(j, shot.hp, shot.owner);
                    else effect(1, -1, shot.x, shot.y, 12);
                    break;
                }
            } else {
                for (GameState.Pilot pilot : state.pilots) {
                    if (pilot.downed || !touch(shot, pilot.x, pilot.y, 12)) continue;
                    state.shots.remove(i); damagePilot(pilot, shot.hp); break;
                }
            }
        }
        for (int i = state.enemies.size() - 1; i >= 0 && state.phase == Phase.RUNNING; i--) {
            GameState.Body enemy = state.enemies.get(i);
            for (GameState.Pilot pilot : state.pilots) {
                if (pilot.downed || WorldMath.distance(enemy.x, enemy.y, pilot.x, pilot.y) > enemy.radius + 15) continue;
                damagePilot(pilot, enemy.kind == 4 ? 35 : 28);
                if (enemy.kind != 4) destroyEnemy(i, pilot.id);
                break;
            }
        }
        for (int i = state.pickups.size() - 1; i >= 0 && state.phase == Phase.RUNNING; i--) {
            GameState.Body pickup = state.pickups.get(i);
            for (GameState.Pilot pilot : state.pilots) {
                if (!pilot.downed && touch(pickup, pilot.x, pilot.y, 18)) {
                    state.pickups.remove(i); collect(pilot, pickup.kind); break;
                }
            }
        }
    }

    private static boolean touch(GameState.Body body, float x, float y, float radius) {
        float dx = body.x - x, dy = body.y - y, sum = body.radius + radius;
        return dx * dx + dy * dy <= sum * sum;
    }

    private void damagePilot(GameState.Pilot pilot, int amount) {
        if (pilot.invulnerable > 0 || state.developer) return;
        pilot.hp = Math.max(0, pilot.hp - Math.max(1, Math.round(amount * state.difficulty.damageScale)));
        pilot.invulnerable = 1.1f;
        effect(1, pilot.id, pilot.x, pilot.y, 18);
        if (pilot.hp == 0) {
            pilot.downed = true; pilot.invulnerable = 0;
            effect(0, pilot.id, pilot.x, pilot.y, 34);
            notice(state.mode == Mode.COOP ? "队友需要帮助，靠近停留就能救援。" : "飞机需要修一修。再来一次吧！");
        }
    }

    private void rescuePilots(float seconds) {
        boolean everyoneDown = true;
        for (GameState.Pilot pilot : state.pilots) if (!pilot.downed) everyoneDown = false;
        if (everyoneDown) { state.phase = Phase.DEFEAT; return; }
        if (state.mode != Mode.COOP) return;
        for (GameState.Pilot pilot : state.pilots) {
            if (!pilot.downed) continue;
            boolean near = false;
            for (GameState.Pilot teammate : state.pilots) {
                if (!teammate.downed && WorldMath.distance(pilot.x, pilot.y, teammate.x, teammate.y) < 58) near = true;
            }
            pilot.reviveProgress = WorldMath.clamp(pilot.reviveProgress + seconds * (near ? 1f / 2.2f : -.18f), 0, 1);
            if (pilot.reviveProgress >= 1) {
                pilot.downed = false; pilot.hp = 55; pilot.invulnerable = 3.5f; pilot.reviveProgress = 0;
                notice("接住啦！继续一起飞。"); effect(2, pilot.id, pilot.x, pilot.y, 30);
            }
        }
    }

    void damageEnemy(int index, int amount, int owner) {
        if (index < 0 || index >= state.enemies.size()) return;
        GameState.Body enemy = state.enemies.get(index);
        enemy.hp -= amount;
        if (enemy.kind == 4 && enemy.hp <= enemy.maxHp / 2 && (enemy.flags & 2) == 0) {
            enemy.flags |= 2; notice("它换了个飞法！留心下一轮。");
            effect(3, -1, enemy.x, enemy.y, 90);
            if (state.mode != Mode.ENDLESS) bossPhaseUntil = state.elapsed + 3;
        }
        // A short campaign still gives the second attack pattern time to appear.
        if (enemy.kind == 4 && state.mode != Mode.ENDLESS && state.elapsed < bossPhaseUntil)
            enemy.hp = Math.max(1, enemy.hp);
        if (enemy.hp <= 0) destroyEnemy(index, owner);
    }

    void destroyEnemy(int index, int owner) {
        GameState.Body enemy = state.enemies.remove(index);
        int[] rewards = {10, 20, 30, 40, 350, 35, 50, 45};
        addScore(owner, enemy.kind == 4 ? rewards[4] + state.stage * 100 : rewards[enemy.kind]);
        effect(0, owner, enemy.x, enemy.y, enemy.radius * 1.6f);
        if (enemy.kind == 4) {
            director.bossDefeated();
            if (state.mode != Mode.ENDLESS) {
                state.phase = Phase.VICTORY;
                state.shots.clear();
                notice(state.stage == 5 ? "航线恢复！大块头，跟我们回家。" : "航标找到了，下一站见！");
            }
        } else if (enemy.kind > 0 && ++specialKills % 2 == 0 && state.pickups.size() < 20) {
            GameState.Body pickup = body(supplySequence++ % 5, 0, enemy.x, enemy.y, 16);
            state.pickups.add(pickup);
        }
    }

    private void collect(GameState.Pilot pilot, int kind) {
        addScore(pilot.id, 10); effect(2, pilot.id, pilot.x, pilot.y, 27);
        switch (kind) {
            case 0: pilot.hp = Math.min(pilot.maxHp, pilot.hp + 35); notice("修好了，继续飞！"); break;
            case 1: pilot.weapon = 1; pilot.weaponSeconds = 5; notice("三路火力 · 5秒"); break;
            case 2: pilot.weapon = 2; pilot.weaponSeconds = 5; notice("环形火力 · 5秒"); break;
            case 3:
                pilot.bombs = Math.min(5, pilot.bombs + 1);
                weapons.detonate(pilot); notice("炸弹清屏！头目和王牌更结实些。"); break;
            case 4: state.freezeSeconds = 5; notice("冻结 · 5秒（头目免疫，王牌减速）"); break;
            default: break;
        }
    }

    void addScore(int owner, int amount) {
        state.score += amount;
        int id = Math.max(0, Math.min(state.pilots.size() - 1, owner));
        state.pilots.get(id).score += amount;
    }

    GameState.Body body(int kind, int owner, float x, float y, float radius) {
        GameState.Body body = new GameState.Body();
        body.id = nextId++; body.kind = kind; body.owner = owner;
        body.x = x; body.y = y; body.radius = radius;
        return body;
    }

    void effect(int kind, int owner, float x, float y, float radius) {
        if (state.effects.size() >= 72) state.effects.remove(0);
        state.effects.add(body(kind, owner, x, y, radius));
    }

    private void updateEffects(float seconds) {
        for (int i = state.effects.size() - 1; i >= 0; i--) {
            GameState.Body effect = state.effects.get(i);
            effect.age += seconds;
            float lifetime = effect.kind == 1 ? .22f : effect.kind == 5 ? .32f : .65f;
            if (effect.kind == 4) lifetime = effect.owner < 0 ? 1.15f : .48f;
            if (effect.age >= lifetime) state.effects.remove(i);
        }
    }

    void notice(String text) { state.notice = text; noticeSeconds = 4; }
}
