package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HeroManager extends AbstractEntityManager<GameState.Pilot> {
    private final int maximum;

    HeroManager(List<GameState.Pilot> heroes, EventManager events, int maximum) {
        super(heroes, events); this.maximum = maximum;
    }

    public GameState.Pilot create(int id, String name, float x, float y, boolean developer) {
        if (!isAcceptingSpawns() || count() >= maximum) return null;
        for (GameState.Pilot existing : entities) {
            if (existing.id == id) throw new IllegalArgumentException("Duplicate hero ID: " + id);
        }
        GameState.Pilot hero = new GameState.Pilot();
        hero.id = id; hero.name = name; hero.x = x; hero.y = y;
        hero.hp = 100; hero.maxHp = 100; hero.bombs = developer ? 99 : 2; hero.charge = 1;
        register(hero);
        return hero;
    }

    public int getMaximum() { return maximum; }
    public int livingCount() {
        int count = 0;
        for (GameState.Pilot hero : entities) if (!hero.downed && hero.hp > 0) count++;
        return count;
    }

    public List<Identity> identities() {
        List<Identity> values = new ArrayList<>();
        for (GameState.Pilot hero : entities) values.add(new Identity(hero.id, hero.name));
        return Collections.unmodifiableList(values);
    }

    public static final class Identity {
        public final int id;
        public final String name;
        Identity(int id, String name) { this.id = id; this.name = name; }
    }
}
