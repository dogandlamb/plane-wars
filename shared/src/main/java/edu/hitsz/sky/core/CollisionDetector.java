package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Batch geometry only. No entity references, damage calculations or business callbacks. */
public final class CollisionDetector {
    private final Map<Integer, CollisionBody> bodies = new LinkedHashMap<>();
    private final Map<Integer, CollisionInfo> information = new LinkedHashMap<>();
    private final Map<CollisionType, Set<Integer>> idsByType = new EnumMap<>(CollisionType.class);
    private final Set<Integer> consumed = new HashSet<>(), bodyContacts = new HashSet<>();
    private long candidates, elapsedNanos;
    private int contacts;
    private boolean debug;
    private int debugInterval = 60, debugLimit = 128;

    public Map<Integer, Set<CollisionInfo>> detect(CollisionSource... sources) {
        clear();
        long started = debug ? System.nanoTime() : 0;
        List<CollisionBody> samples = new ArrayList<>();
        for (CollisionSource source : sources) source.collectColliders(samples);
        Map<CollisionType, List<CollisionBody>> groups = new EnumMap<>(CollisionType.class);
        for (CollisionType type : CollisionType.values()) {
            idsByType.put(type, new LinkedHashSet<>()); groups.put(type, new ArrayList<>());
        }
        for (CollisionBody sample : samples) {
            if (bodies.putIfAbsent(sample.id, sample) != null)
                throw new IllegalArgumentException("Duplicate collision entity ID: " + sample.id);
            idsByType.get(sample.type).add(sample.id); groups.get(sample.type).add(sample);
        }
        Map<Integer, Set<CollisionInfo>> result = new TreeMap<>();
        detectPairs(groups, result);
        Map<Integer, Set<CollisionInfo>> immutable = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<CollisionInfo>> entry : result.entrySet())
            immutable.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        if (debug) elapsedNanos = System.nanoTime() - started;
        return Collections.unmodifiableMap(immutable);
    }

    /** Enumerating only these category products excludes reverse duplicates and forbidden pairs. */
    private void detectPairs(Map<CollisionType, List<CollisionBody>> groups, Map<Integer, Set<CollisionInfo>> result) {
        List<CollisionBody> heroes = groups.get(CollisionType.HERO), enemies = groups.get(CollisionType.ENEMY);
        for (CollisionBody bullet : groups.get(CollisionType.BULLET)) {
            for (CollisionBody target : bullet.creditedHeroId >= 0 ? enemies : heroes)
                test(bullet, target, bullet.radius, target.type == CollisionType.HERO ? target.hurtRadius : target.radius, result);
        }
        for (CollisionBody enemy : enemies) for (CollisionBody hero : heroes)
            test(enemy, hero, enemy.radius, hero.bodyRadius, result);
        for (CollisionBody pickup : groups.get(CollisionType.PICKUP)) for (CollisionBody hero : heroes)
            test(pickup, hero, pickup.radius, hero.pickupRadius, result);
    }

    private void test(CollisionBody a, CollisionBody b, float ar, float br, Map<Integer, Set<CollisionInfo>> result) {
        if (a.id == b.id) return;
        candidates++;
        double dx = (double) a.x - b.x, dy = (double) a.y - b.y, sum = (double) ar + br;
        if (dx * dx + dy * dy > sum * sum) return;
        boolean added = result.computeIfAbsent(a.id, ignored -> new TreeSet<>()).add(informationOf(b.id));
        result.computeIfAbsent(b.id, ignored -> new TreeSet<>()).add(informationOf(a.id));
        if (added) contacts++;
    }

    /** Provisional conflict policy: first active pair in ID order consumes a bullet/pickup once.
     * Kept separate from geometry so the final simultaneous-contact policy can replace it. */
    boolean acceptContact(int firstId, int secondId) {
        if (consumed.contains(firstId) || consumed.contains(secondId)) return false;
        CollisionBody a = bodies.get(firstId), b = bodies.get(secondId);
        if (a == null || b == null) return false;
        CollisionBody enemy = a.type == CollisionType.ENEMY ? a : b.type == CollisionType.ENEMY ? b : null;
        boolean body = enemy != null && (a.type == CollisionType.HERO || b.type == CollisionType.HERO);
        if (body && !bodyContacts.add(enemy.id)) return false;
        if (a.type == CollisionType.BULLET || a.type == CollisionType.PICKUP) consumed.add(a.id);
        if (b.type == CollisionType.BULLET || b.type == CollisionType.PICKUP) consumed.add(b.id);
        return true;
    }

    CollisionType typeOf(int id) { return bodies.get(id).type; }
    CollisionInfo informationOf(int id) {
        return information.computeIfAbsent(id, key -> new CollisionInfo(bodies.get(key)));
    }
    public Set<Integer> ids(CollisionType type) {
        Set<Integer> values = idsByType.get(type);
        return values == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
    public void configureDebug(boolean enabled, int interval, int sampleLimit) {
        if (interval < 1 || sampleLimit < 0) throw new IllegalArgumentException("Invalid collision debug limits");
        debug = enabled; debugInterval = interval; debugLimit = sampleLimit;
    }
    CollisionDebugFrameEvent debugFrame(long tick, Map<Integer, Set<CollisionInfo>> result) {
        if (!debug || tick % debugInterval != 0) return null;
        return new CollisionDebugFrameEvent(tick, candidates, contacts, elapsedNanos, bodies.values(), result, debugLimit);
    }
    public void clear() {
        bodies.clear(); information.clear(); idsByType.clear(); consumed.clear(); bodyContacts.clear();
        candidates = 0; contacts = 0; elapsedNanos = 0;
    }
}
