package edu.hitsz.sky.core;

public final class Bullet extends Entity {
    private final BulletManager manager;
    Bullet(BulletManager manager) { super(CollisionType.BULLET); this.manager = manager; }
    @Override public void onCollision(CollisionInfo other) { manager.remove(this); }
}
