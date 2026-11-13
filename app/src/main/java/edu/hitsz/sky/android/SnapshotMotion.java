package edu.hitsz.sky.android;

import edu.hitsz.sky.core.GameState;
import edu.hitsz.sky.core.Mode;
import edu.hitsz.sky.core.Phase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded visual blends; authority and entity lifetime always come from the latest snapshot. */
final class SnapshotMotion {
    private static final long TICK_NANOS = 1_000_000_000L / 60;
    private static final long MAX_BLEND_NANOS = 100_000_000L;
    private static final long MAX_RECEIVE_GAP_NANOS = 250_000_000L;
    private static final long MAX_TICK_GAP = 9;
    private static final float MAX_MOVE_SQUARED = 96 * 96;
    private final Map<Integer, Integer> pilotIndices = new HashMap<>();
    private final Map<Integer, Integer> enemyIndices = new HashMap<>();
    private final Map<Integer, Integer> shotIndices = new HashMap<>();
    private final Map<Integer, Integer> pickupIndices = new HashMap<>();
    private Frame previous;

    Frame accept(GameState state, long receivedNanos) {
        long duration = 0;
        float elapsedOrigin = 0;
        float[] origins = null;
        if (canBlend(state, receivedNanos)) {
            int enemyOffset = state.pilots.size() * 2;
            int shotOffset = enemyOffset + state.enemies.size() * 2;
            int pickupOffset = shotOffset + state.shots.size() * 2;
            duration = Math.min(MAX_BLEND_NANOS, (state.tick - previous.state.tick) * TICK_NANOS);
            origins = new float[pickupOffset + state.pickups.size() * 2];
            float fraction = previous.fraction(receivedNanos);
            elapsedOrigin = previous.elapsed(fraction);
            indexPilots(previous.state.pilots);
            for (int i = 0; i < state.pilots.size(); i++) {
                GameState.Pilot pilot = state.pilots.get(i);
                Integer oldIndex = pilotIndices.get(pilot.id);
                GameState.Pilot old = oldIndex == null ? null : previous.state.pilots.get(oldIndex);
                float x = pilot.x, y = pilot.y;
                if (old != null && pilot.downed == old.downed && nearby(old.x, old.y, x, y)) {
                    float fromX = previous.pilotX(oldIndex, fraction);
                    float fromY = previous.pilotY(oldIndex, fraction);
                    if (nearby(fromX, fromY, x, y)) { x = fromX; y = fromY; }
                }
                origins[i * 2] = x; origins[i * 2 + 1] = y;
            }
            fillBodies(origins, state.enemies, previous.state.enemies, enemyIndices,
                    enemyOffset, previous.enemyOffset, fraction);
            fillBodies(origins, state.shots, previous.state.shots, shotIndices,
                    shotOffset, previous.shotOffset, fraction);
            fillBodies(origins, state.pickups, previous.state.pickups, pickupIndices,
                    pickupOffset, previous.pickupOffset, fraction);
        }
        Frame next = new Frame(state, receivedNanos, duration, elapsedOrigin, origins);
        previous = state == null ? null : next;
        return next;
    }

    void clear() {
        previous = null;
        pilotIndices.clear(); enemyIndices.clear(); shotIndices.clear(); pickupIndices.clear();
    }

    private boolean canBlend(GameState state, long receivedNanos) {
        if (state == null || previous == null || state.night != null || previous.state.night != null ||
                (state.mode != Mode.COOP && state.mode != Mode.VERSUS)) return false;
        GameState old = previous.state;
        long ticks = state.tick - old.tick, gap = receivedNanos - previous.receivedNanos;
        return state.mode == old.mode && state.stage == old.stage && state.difficulty == old.difficulty &&
                state.developer == old.developer && state.phase == Phase.RUNNING && old.phase == Phase.RUNNING &&
                state.elapsed >= old.elapsed && ticks > 0 && ticks <= MAX_TICK_GAP &&
                gap > 0 && gap <= MAX_RECEIVE_GAP_NANOS;
    }

    private void indexPilots(List<GameState.Pilot> pilots) {
        pilotIndices.clear();
        for (int i = 0; i < pilots.size(); i++) pilotIndices.put(pilots.get(i).id, i);
    }

    private void fillBodies(float[] origins, List<GameState.Body> bodies, List<GameState.Body> oldBodies,
                            Map<Integer, Integer> indices, int offset, int oldOffset, float fraction) {
        indices.clear();
        for (int i = 0; i < oldBodies.size(); i++) indices.put(oldBodies.get(i).id, i);
        for (int i = 0; i < bodies.size(); i++) {
            GameState.Body body = bodies.get(i);
            Integer oldIndex = indices.get(body.id);
            GameState.Body old = oldIndex == null ? null : oldBodies.get(oldIndex);
            float x = body.x, y = body.y;
            if (old != null && body.kind == old.kind && body.owner == old.owner && nearby(old.x, old.y, x, y)) {
                float fromX = previous.coordinate(oldOffset, oldIndex, 0, old.x, fraction);
                float fromY = previous.coordinate(oldOffset, oldIndex, 1, old.y, fraction);
                if (nearby(fromX, fromY, x, y)) { x = fromX; y = fromY; }
            }
            origins[offset + i * 2] = x; origins[offset + i * 2 + 1] = y;
        }
    }

    private static boolean nearby(float x, float y, float targetX, float targetY) {
        float dx = targetX - x, dy = targetY - y;
        return dx * dx + dy * dy <= MAX_MOVE_SQUARED;
    }

    static final class Frame {
        final GameState state;
        private final long receivedNanos;
        private final int enemyOffset, shotOffset, pickupOffset;
        private final long durationNanos;
        private final float elapsedOrigin;
        private final float[] origins;

        private Frame(GameState state, long receivedNanos, long durationNanos, float elapsedOrigin,
                      float[] origins) {
            this.state = state; this.receivedNanos = receivedNanos;
            this.durationNanos = durationNanos; this.elapsedOrigin = elapsedOrigin; this.origins = origins;
            enemyOffset = state == null ? 0 : state.pilots.size() * 2;
            shotOffset = enemyOffset + (state == null ? 0 : state.enemies.size() * 2);
            pickupOffset = shotOffset + (state == null ? 0 : state.shots.size() * 2);
        }

        float fraction(long nowNanos) {
            if (durationNanos == 0) return 1;
            return Math.max(0, Math.min(1, (nowNanos - receivedNanos) / (float) durationNanos));
        }

        float elapsed(float fraction) {
            return origins == null ? state.elapsed : elapsedOrigin + (state.elapsed - elapsedOrigin) * fraction;
        }

        float pilotX(int index, float fraction) { return coordinate(0, index, 0, state.pilots.get(index).x, fraction); }
        float pilotY(int index, float fraction) { return coordinate(0, index, 1, state.pilots.get(index).y, fraction); }
        float enemyX(int index, float fraction) { return coordinate(enemyOffset, index, 0, state.enemies.get(index).x, fraction); }
        float enemyY(int index, float fraction) { return coordinate(enemyOffset, index, 1, state.enemies.get(index).y, fraction); }
        float shotX(int index, float fraction) { return coordinate(shotOffset, index, 0, state.shots.get(index).x, fraction); }
        float shotY(int index, float fraction) { return coordinate(shotOffset, index, 1, state.shots.get(index).y, fraction); }
        float pickupX(int index, float fraction) { return coordinate(pickupOffset, index, 0, state.pickups.get(index).x, fraction); }
        float pickupY(int index, float fraction) { return coordinate(pickupOffset, index, 1, state.pickups.get(index).y, fraction); }

        private float coordinate(int offset, int index, int axis, float target, float fraction) {
            if (origins == null) return target;
            float origin = origins[offset + index * 2 + axis];
            return origin + (target - origin) * fraction;
        }
    }
}
