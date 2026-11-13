package edu.hitsz.sky.core;

final class StateSnapshots {
    private StateSnapshots() { }
    static GameState copy(GameState source) {
        GameState result = new GameState();
        result.tick = source.tick; result.stage = source.stage; result.score = source.score;
        result.elapsed = source.elapsed; result.freezeSeconds = source.freezeSeconds;
        result.difficulty = source.difficulty; result.mode = source.mode;
        result.developer = source.developer; result.phase = source.phase; result.notice = source.notice;
        for (GameState.Pilot original : source.pilots) {
            GameState.Pilot pilot = new GameState.Pilot();
            pilot.id = original.id; pilot.hp = original.hp; pilot.maxHp = original.maxHp;
            pilot.score = original.score; pilot.weapon = original.weapon; pilot.bombs = original.bombs;
            pilot.name = original.name; pilot.x = original.x; pilot.y = original.y;
            pilot.invulnerable = original.invulnerable; pilot.weaponSeconds = original.weaponSeconds;
            pilot.reviveProgress = original.reviveProgress; pilot.charge = original.charge;
            pilot.downed = original.downed;
            System.arraycopy(original.cooldowns, 0, pilot.cooldowns, 0, 4);
            result.pilots.add(pilot);
        }
        for (GameState.Body body : source.enemies) result.enemies.add(copyBody(body));
        for (GameState.Body body : source.shots) result.shots.add(copyBody(body));
        for (GameState.Body body : source.pickups) result.pickups.add(copyBody(body));
        for (GameState.Body body : source.effects) result.effects.add(copyBody(body));
        if (source.night != null) {
            GameState.Night night = new GameState.Night();
            night.energy = source.night.energy; night.elapsed = source.night.elapsed;
            night.camera = source.night.camera; night.hour = source.night.hour;
            night.leftDoor = source.night.leftDoor; night.rightDoor = source.night.rightDoor;
            night.leftLight = source.night.leftLight; night.rightLight = source.night.rightLight;
            night.tabletOpen = source.night.tabletOpen;
            System.arraycopy(source.night.intruders, 0, night.intruders, 0, 3);
            System.arraycopy(source.night.routes, 0, night.routes, 0, 3);
            result.night = night;
        }
        return result;
    }

    private static GameState.Body copyBody(GameState.Body original) {
        GameState.Body body = new GameState.Body();
        body.id = original.id; body.kind = original.kind; body.owner = original.owner;
        body.hp = original.hp; body.maxHp = original.maxHp; body.flags = original.flags;
        body.x = original.x; body.y = original.y; body.vx = original.vx; body.vy = original.vy;
        body.radius = original.radius; body.age = original.age; body.angle = original.angle;
        return body;
    }
}
