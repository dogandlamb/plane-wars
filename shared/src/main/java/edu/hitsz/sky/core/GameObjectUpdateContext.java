package edu.hitsz.sky.core;

/** Read-only battle conditions sampled once per simulation step for entities and visual objects. */
public final class GameObjectUpdateContext {
    public final float seconds, windVelocityX;
    public final boolean frozen;

    public GameObjectUpdateContext(float seconds, boolean frozen, float windVelocityX) {
        if (!Float.isFinite(seconds) || seconds < 0 || !Float.isFinite(windVelocityX))
            throw new IllegalArgumentException("Invalid game object update conditions");
        this.seconds = seconds; this.frozen = frozen; this.windVelocityX = windVelocityX;
    }
}
