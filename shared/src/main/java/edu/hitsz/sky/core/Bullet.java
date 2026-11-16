package edu.hitsz.sky.core;

public final class Bullet extends Entity {
    private static final float MIN_X = -40, MAX_X = 520, MIN_Y = -70, MAX_Y = 860;
    private final float lifetime, windScale;

    Bullet(BattleConfig config) {
        super(CollisionType.BULLET);
        lifetime = config.value("bullet.lifetime"); windScale = config.value("bullet.windScale");
    }
    @Override protected float timeScale(GameObjectUpdateContext context) {
        return owner < 0 && context.frozen ? 0 : 1;
    }
    @Override protected float ageSeconds(GameObjectUpdateContext context, float movementSeconds) { return context.seconds; }
    @Override protected float windScale() { return windScale; }
    @Override protected float lifetime() { return lifetime; }
    @Override protected boolean isOutOfBounds() { return x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y; }
    @Override public void onCollision(CollisionInfo other) { recycle(); }
}
