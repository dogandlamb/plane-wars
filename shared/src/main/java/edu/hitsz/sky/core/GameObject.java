package edu.hitsz.sky.core;

/** Identity and world-space lifecycle shared by live objects and detached frame values. */
public abstract class GameObject {
    public int entityId;
    public float x, y, vx, vy, radius, age, angle;
    public boolean active = true;
}
