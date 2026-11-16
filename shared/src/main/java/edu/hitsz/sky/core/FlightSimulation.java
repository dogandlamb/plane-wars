package edu.hitsz.sky.core;

import java.util.Random;
import java.util.Map;
import java.util.Set;

final class FlightSimulation implements BattleEngine.Simulation {
    static final int MAX_SHOTS = BulletManager.MAX_BULLETS, MAX_ENEMIES = EnemyManager.MAX_ENEMIES;
    final GameState state;
    final StageCatalog.Stage stage;
    final Random random;
    final FlightWeapons weapons;
    final FlightDirector director;
    final FlightBattle battle;
    private final float[] targetX = new float[2], targetY = new float[2], shotClock = new float[2];
    private final int[] pendingSkills = new int[2];
    private final boolean[] pendingBombs = new boolean[2];
    private float noticeSeconds = 5;

    FlightSimulation(GameState state, long seed, FlightBattle battle) {
        this.battle = battle;
        this.state = state; stage = StageCatalog.get(state.stage); random = new Random(seed);
        for (GameState.Pilot pilot : state.pilots) {
            targetX[pilot.id] = pilot.x; targetY[pilot.id] = pilot.y;
        }
        weapons = new FlightWeapons(this); director = new FlightDirector(this);
        battle.combat.bind(this::notice, weapons::detonate);
        battle.events.subscribe(BattleEndedEvent.class, event -> {
            java.util.Arrays.fill(pendingSkills, 0);
            java.util.Arrays.fill(pendingBombs, false);
        });
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
        battle.effects.update(seconds);
        updatePilots(seconds);
        if (battle.outcome.campaignObjectiveReached()) { battle.outcome.evaluate(); return; }
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
        battle.outcome.evaluate();
    }

    private void updatePilots(float seconds) {
        for (GameState.Pilot pilot : state.pilots) {
            float previousX = pilot.x, previousY = pilot.y;
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
            if (pilot.downed) { pilot.vx = pilot.vy = 0; pendingSkills[pilot.id] = 0; pendingBombs[pilot.id] = false; continue; }
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
            pilot.vx = (pilot.x - previousX) / seconds; pilot.vy = (pilot.y - previousY) / seconds;
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
            if (shot.x < -40 || shot.x > 520 || shot.y < -70 || shot.y > 860 || shot.age > 14) battle.bullets.remove(shot);
        }
        battle.pickups.update(seconds);
    }

    private void resolveCollisions() {
        Map<Integer, Set<CollisionInfo>> contacts = battle.collisions.detect(
                battle.heroes, battle.enemies, battle.bullets, battle.pickups);
        battle.collisionDispatcher.dispatch(contacts, battle.outcome::campaignObjectiveReached);
        CollisionDebugFrameEvent debug = battle.collisions.debugFrame(state.tick, contacts);
        if (debug != null) {
            // Diagnostics cannot interrupt battle settlement or make observers part of the rules.
            try { battle.events.publish(debug); } catch (RuntimeException ignored) { }
        }
    }

    private void rescuePilots(float seconds) {
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
        if (index >= 0 && index < state.enemies.size())
            ((Enemy) state.enemies.get(index)).receiveDamage(amount, owner);
    }

    void destroyEnemy(int index, int owner) {
        battle.enemies.defeat(state.enemies.get(index), owner);
    }

    void effect(int kind, int owner, float x, float y, float radius) {
        battle.effects.spawn(kind, owner, x, y, radius);
    }

    void notice(String text) { state.notice = text; noticeSeconds = 4; }
}
