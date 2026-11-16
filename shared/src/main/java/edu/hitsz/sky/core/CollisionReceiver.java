package edu.hitsz.sky.core;

/** Each receiver changes its own state, not the other participant's state. */
public interface CollisionReceiver {
    void onCollision(CollisionInfo collision);
}
