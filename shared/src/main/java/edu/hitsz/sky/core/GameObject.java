package edu.hitsz.sky.core;

import java.util.Objects;

/** Identity and world-space lifecycle shared by live objects and detached frame values. */
public abstract class GameObject {
    public int entityId;
    public float x, y, vx, vy, radius, age, angle;
    public boolean active = true;
    private Runnable recycleRequest;

    /** One template for entities and visual objects; subclasses specialize the individual rules. */
    public final void update(GameObjectUpdateContext context) {
        Objects.requireNonNull(context, "context");
        if (!active || context.seconds == 0) return;
        float movementSeconds = context.seconds * timeScale(context);
        age += ageSeconds(context, movementSeconds);
        move(context, movementSeconds);
        if (!active) return;
        afterMove(context, movementSeconds);
        if (active && (isLifetimeExpired() || isOutOfBounds())) recycle();
    }

    protected float timeScale(GameObjectUpdateContext context) { return 1; }
    protected float ageSeconds(GameObjectUpdateContext context, float movementSeconds) { return movementSeconds; }
    protected float windScale() { return 0; }
    protected void move(GameObjectUpdateContext context, float seconds) {
        x += vx * seconds; y += vy * seconds;
        applyWind(context, seconds);
    }
    protected final void applyWind(GameObjectUpdateContext context, float seconds) {
        float scale = windScale();
        if (scale != 0 && seconds != 0) x += context.windVelocityX * seconds * scale;
    }
    protected void afterMove(GameObjectUpdateContext context, float movementSeconds) { }
    protected float lifetime() { return Float.POSITIVE_INFINITY; }
    protected boolean isLifetimeExpired() { return age > lifetime(); }
    protected boolean isOutOfBounds() { return false; }

    /** Retirement asks the owning manager to update its list and identity index together. */
    protected final void recycle() {
        if (!active) return;
        if (recycleRequest == null) throw new IllegalStateException("Object has no owning manager");
        recycleRequest.run();
    }
    final void bindRecycler(Runnable request) {
        if (!active || recycleRequest != null) throw new IllegalStateException("Object is inactive or already owned");
        recycleRequest = Objects.requireNonNull(request);
    }
    final void deactivate() { active = false; recycleRequest = null; }
}
