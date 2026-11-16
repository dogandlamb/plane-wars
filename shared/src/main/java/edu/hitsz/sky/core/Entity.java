package edu.hitsz.sky.core;

public abstract class Entity extends GameState.Body implements CollisionReceiver {
    public final CollisionType collisionType;
    public String configurationTag = "";

    protected Entity(CollisionType type) { collisionType = type; }
    public boolean isCollidable() { return active; }
    CollisionBody collisionBody() {
        return new CollisionBody(entityId, collisionType, configurationTag, owner, x, y, vx, vy, radius);
    }
}
