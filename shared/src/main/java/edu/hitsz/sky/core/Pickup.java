package edu.hitsz.sky.core;

public final class Pickup extends Entity {
    private final PickupManager manager;
    Pickup(PickupManager manager) { super(CollisionType.PICKUP); this.manager = manager; }
    @Override public void onCollision(CollisionInfo other) {
        if (other.otherType == CollisionType.HERO) manager.collect(this, other.creditedHeroId);
    }
}
