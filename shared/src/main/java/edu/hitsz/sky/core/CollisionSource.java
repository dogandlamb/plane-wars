package edu.hitsz.sky.core;

import java.util.List;

/** Managers append only this simulation step's enabled, read-only samples. */
public interface CollisionSource {
    void collectColliders(List<CollisionBody> destination);
}
