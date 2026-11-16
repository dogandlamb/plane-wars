package edu.hitsz.sky.core;

public final class Boss extends Enemy {
    private final EventManager events;
    Boss(EnemyManager manager, EventManager events) { super(manager); this.events = events; }
    @Override protected void move(GameObjectUpdateContext context, float seconds) {
        int stage = combat().stage();
        y = WorldMath.approach(y, 115, seconds * 70);
        x = 240 + (float) Math.sin(age * (.4f + stage * .025f)) * (88 + stage * 8);
    }
    @Override protected void notifyDeath(int creditedHeroId) {
        events.publish(new BossDiedEvent(this, creditedHeroId));
    }
}
