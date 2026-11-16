package edu.hitsz.sky.core;

public final class Boss extends Enemy {
    private final EventManager events;
    Boss(EnemyManager manager, EventManager events) { super(manager); this.events = events; }
    @Override protected void notifyDeath(int creditedHeroId) {
        events.publish(new BossDiedEvent(this, creditedHeroId));
    }
}
