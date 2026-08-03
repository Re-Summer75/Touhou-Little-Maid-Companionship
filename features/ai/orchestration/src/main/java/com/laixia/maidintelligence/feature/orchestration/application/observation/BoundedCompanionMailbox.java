package com.laixia.maidintelligence.feature.orchestration.application.observation;

import com.laixia.maidintelligence.feature.orchestration.api.CompanionObservationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-subject in-memory mailbox with strict cardinality and TTL bounds.
 */
public final class BoundedCompanionMailbox {
    public static final int MAX_EVENTS = 64;
    public static final int MAX_BELIEFS = 64;
    public static final int MAX_OUTCOMES = 64;
    private static final int MAX_SEEN_IDENTITIES = 128;

    private final Map<UUID, EpisodicEvent> events = new LinkedHashMap<>();
    private final Map<BeliefKey, Belief> beliefs = new LinkedHashMap<>();
    private final Map<UUID, OperationOutcome> outcomes =
            new LinkedHashMap<>();
    private final Set<UUID> seenEvents = new LinkedHashSet<>();
    private final Set<UUID> seenOperations = new LinkedHashSet<>();

    public void restore(
            CompanionPersistentMemory memory,
            long gameTime
    ) {
        for (Belief belief : memory.beliefs()) {
            if (belief.activeAt(gameTime)) {
                remember(belief);
            }
        }
        for (OperationOutcome outcome : memory.outcomes()) {
            record(outcome);
        }
    }

    public boolean publish(EpisodicEvent event, long gameTime) {
        prune(gameTime);
        UUID eventId = event.identity().eventId();
        if (!seenEvents.add(eventId)) {
            return false;
        }
        events.put(eventId, event);
        trimMap(events, MAX_EVENTS);
        trimSet(seenEvents, MAX_SEEN_IDENTITIES);
        return true;
    }

    public boolean remember(Belief belief) {
        BeliefKey key = new BeliefKey(belief.key(), belief.source());
        Belief previous = beliefs.get(key);
        if (previous != null
                && previous.observedAtTick() > belief.observedAtTick()) {
            return false;
        }
        beliefs.remove(key);
        beliefs.put(key, belief);
        trimMap(beliefs, MAX_BELIEFS);
        return true;
    }

    public boolean record(OperationOutcome outcome) {
        UUID operationId = outcome.identity().operationId();
        if (!seenOperations.add(operationId)) {
            return false;
        }
        outcomes.put(operationId, outcome);
        trimMap(outcomes, MAX_OUTCOMES);
        trimSet(seenOperations, MAX_SEEN_IDENTITIES);
        return true;
    }

    public CompanionObservationSnapshot snapshot(long gameTime) {
        prune(gameTime);
        return new CompanionObservationSnapshot(
                List.copyOf(events.values()),
                List.copyOf(beliefs.values()),
                List.copyOf(outcomes.values())
        );
    }

    public CompanionPersistentMemory persistentMemory(long gameTime) {
        prune(gameTime);
        List<Belief> persistentBeliefs = beliefs.values().stream()
                .filter(Belief::persistent)
                .toList();
        return new CompanionPersistentMemory(
                CompanionPersistentMemory.CURRENT_SCHEMA_VERSION,
                last(
                        persistentBeliefs,
                        CompanionPersistentMemory.MAX_BELIEFS
                ),
                last(
                        new ArrayList<>(outcomes.values()),
                        CompanionPersistentMemory.MAX_OUTCOMES
                )
        );
    }

    private void prune(long gameTime) {
        events.values().removeIf(event -> !event.activeAt(gameTime));
        beliefs.values().removeIf(belief -> !belief.activeAt(gameTime));
    }

    private static <K, V> void trimMap(Map<K, V> values, int maximum) {
        Iterator<K> iterator = values.keySet().iterator();
        while (values.size() > maximum && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static <T> void trimSet(Set<T> values, int maximum) {
        Iterator<T> iterator = values.iterator();
        while (values.size() > maximum && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static <T> List<T> last(List<T> values, int maximum) {
        int start = Math.max(0, values.size() - maximum);
        return List.copyOf(values.subList(start, values.size()));
    }

    private record BeliefKey(
            OrchestrationId key,
            OrchestrationId source
    ) {
    }
}
