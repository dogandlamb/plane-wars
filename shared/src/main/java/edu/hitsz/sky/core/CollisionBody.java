package edu.hitsz.sky.core;

import java.util.Objects;

/** One immutable entity sample, including the hero's three purpose-specific circles. */
public final class CollisionBody {
    public final int id, creditedHeroId;
    public final CollisionType type;
    public final String configurationTag;
    public final float x, y, vx, vy, radius, hurtRadius, bodyRadius, pickupRadius;

    public CollisionBody(int id, CollisionType type, String tag, int owner,
                         float x, float y, float vx, float vy, float radius) {
        this(id, type, tag, owner, x, y, vx, vy, radius, radius, radius, radius);
    }
    public CollisionBody(int id, CollisionType type, String tag, int owner,
                         float x, float y, float vx, float vy, float radius,
                         float hurtRadius, float bodyRadius, float pickupRadius) {
        if (id <= 0 || !finite(x, y, vx, vy, radius, hurtRadius, bodyRadius, pickupRadius)
                || radius < 0 || hurtRadius < 0 || bodyRadius < 0 || pickupRadius < 0)
            throw new IllegalArgumentException("Invalid collision body: " + id);
        this.id = id; this.type = Objects.requireNonNull(type); configurationTag = Objects.requireNonNull(tag);
        creditedHeroId = owner; this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.radius = radius; this.hurtRadius = hurtRadius; this.bodyRadius = bodyRadius; this.pickupRadius = pickupRadius;
    }
    private static boolean finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) return false;
        return true;
    }
}
