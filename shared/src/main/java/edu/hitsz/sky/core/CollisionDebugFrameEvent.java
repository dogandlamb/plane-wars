package edu.hitsz.sky.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional sampled diagnostics; truncation affects diagnostics only, never the formal result. */
public final class CollisionDebugFrameEvent {
    public final long tick, candidatePairs, detectionNanos;
    public final int contacts;
    public final boolean truncated;
    public final List<CollisionBody> bodies;
    public final List<Contact> samples;

    CollisionDebugFrameEvent(long tick, long candidates, int contacts, long nanos, Collection<CollisionBody> bodies,
                             Map<Integer, Set<CollisionInfo>> result, int limit) {
        this.tick = tick; candidatePairs = candidates; this.contacts = contacts; detectionNanos = nanos;
        List<CollisionBody> bodySamples = new ArrayList<>();
        for (CollisionBody body : bodies) if (bodySamples.size() < limit) bodySamples.add(body);
        this.bodies = Collections.unmodifiableList(bodySamples);
        List<Contact> samples = new ArrayList<>();
        for (Map.Entry<Integer, Set<CollisionInfo>> entry : result.entrySet()) for (CollisionInfo other : entry.getValue())
            if (entry.getKey() < other.otherId && samples.size() < limit) samples.add(new Contact(entry.getKey(), other.otherId));
        this.samples = Collections.unmodifiableList(samples);
        truncated = bodies.size() > limit || contacts > limit;
    }
    public static final class Contact {
        public final int firstId, secondId;
        Contact(int first, int second) { firstId = first; secondId = second; }
    }
}
