package edu.hitsz.sky.core;

/** Common entity branch for heroes and enemies; bullets do not use aircraft health rules. */
public abstract class Aircraft extends Entity {
    protected Aircraft(CollisionType type) { super(type); }
}
