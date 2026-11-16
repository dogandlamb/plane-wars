package edu.hitsz.sky.core;

/** Immutable information about the OTHER participant; independent of its continued existence. */
public final class CollisionInfo implements Comparable<CollisionInfo> {
    public final int otherId, creditedHeroId;
    public final CollisionType otherType;
    public final String configurationTag;
    public final float vx, vy;

    CollisionInfo(CollisionBody other) {
        otherId = other.id; otherType = other.type; configurationTag = other.configurationTag;
        vx = other.vx; vy = other.vy; creditedHeroId = other.creditedHeroId;
    }
    @Override public int compareTo(CollisionInfo other) { return Integer.compare(otherId, other.otherId); }
    @Override public boolean equals(Object other) {
        return other instanceof CollisionInfo && otherId == ((CollisionInfo) other).otherId;
    }
    @Override public int hashCode() { return Integer.hashCode(otherId); }
}
