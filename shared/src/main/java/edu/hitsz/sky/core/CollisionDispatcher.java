package edu.hitsz.sky.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Notification adapter: managers locate their own entities and call their receiver interface. */
final class CollisionDispatcher {
    private final CollisionDetector detector;
    private final Map<CollisionType, AbstractEntityManager<?>> managers = new EnumMap<>(CollisionType.class);

    CollisionDispatcher(CollisionDetector detector, HeroManager heroes, EnemyManager enemies,
                        BulletManager bullets, PickupManager pickups) {
        this.detector = detector;
        managers.put(CollisionType.HERO, heroes); managers.put(CollisionType.ENEMY, enemies);
        managers.put(CollisionType.BULLET, bullets); managers.put(CollisionType.PICKUP, pickups);
    }
    void dispatch(Map<Integer, Set<CollisionInfo>> result, BooleanSupplier stop) {
        for (Map.Entry<Integer, Set<CollisionInfo>> entry : result.entrySet()) {
            int firstId = entry.getKey();
            for (CollisionInfo other : entry.getValue()) {
                if (firstId >= other.otherId) continue; // A pair is notified twice, not executed twice.
                if (stop.getAsBoolean()) return;
                AbstractEntityManager<?> first = managers.get(detector.typeOf(firstId));
                AbstractEntityManager<?> second = managers.get(other.otherType);
                if (!first.collisionActive(firstId) || !second.collisionActive(other.otherId)) continue;
                if (!detector.acceptContact(firstId, other.otherId)) continue;
                CollisionInfo reverse = detector.informationOf(firstId);
                first.notifyCollision(firstId, other);
                second.notifyCollision(other.otherId, reverse);
            }
        }
    }
}
