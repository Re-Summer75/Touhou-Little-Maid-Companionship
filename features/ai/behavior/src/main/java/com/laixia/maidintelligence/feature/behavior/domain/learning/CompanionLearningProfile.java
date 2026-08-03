package com.laixia.maidintelligence.feature.behavior.domain.learning;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompanionLearningProfile(
        long revision,
        boolean frozen,
        Map<OrchestrationId, AffordanceReliability> reliability,
        Map<OrchestrationId, OwnerPreference> preferences,
        Map<OrchestrationId, HabitForecast> habits,
        List<UUID> recentSignals
) {
    public static final int MAX_PROJECTIONS = 64;
    public static final int MAX_RECENT_SIGNALS = 128;

    public CompanionLearningProfile {
        reliability = Map.copyOf(new LinkedHashMap<>(reliability));
        preferences = Map.copyOf(new LinkedHashMap<>(preferences));
        habits = Map.copyOf(new LinkedHashMap<>(habits));
        recentSignals = List.copyOf(recentSignals);
        if (revision < 0L
                || reliability.size() > MAX_PROJECTIONS
                || preferences.size() > MAX_PROJECTIONS
                || habits.size() > MAX_PROJECTIONS
                || recentSignals.size() > MAX_RECENT_SIGNALS
                || recentSignals.stream().distinct().count()
                != recentSignals.size()) {
            throw new IllegalArgumentException(
                    "Invalid companion learning profile"
            );
        }
    }

    public static CompanionLearningProfile empty() {
        return new CompanionLearningProfile(
                0L,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        );
    }

    public CompanionLearningProfile observe(LearningSignal signal) {
        if (frozen || recentSignals.contains(signal.signalId())) {
            return this;
        }
        Map<OrchestrationId, AffordanceReliability> nextReliability =
                new LinkedHashMap<>(reliability);
        Map<OrchestrationId, OwnerPreference> nextPreferences =
                new LinkedHashMap<>(preferences);
        Map<OrchestrationId, HabitForecast> nextHabits =
                new LinkedHashMap<>(habits);
        if (signal.status() != OperationStatus.CANCELLED) {
            ensureReliabilityCapacity(nextReliability, signal.action());
            nextReliability.put(
                    signal.action(),
                    nextReliability.getOrDefault(
                            signal.action(),
                            AffordanceReliability.initial()
                    ).observe(
                            signal.status() == OperationStatus.SUCCEEDED,
                            signal.completedAtTick()
                    )
            );
            ensurePreferenceCapacity(nextPreferences, signal.intent());
            nextPreferences.put(
                    signal.intent(),
                    nextPreferences.getOrDefault(
                            signal.intent(),
                            OwnerPreference.initial()
                    ).observe(
                            signal.status() == OperationStatus.SUCCEEDED
                                    ? 1.0D
                                    : -1.0D,
                            signal.completedAtTick()
                    )
            );
        }
        ensureHabitCapacity(nextHabits, signal.action());
        nextHabits.put(
                signal.action(),
                nextHabits.getOrDefault(
                        signal.action(),
                        HabitForecast.initial()
                ).observe(
                        signal.dayBucket(),
                        signal.completedAtTick()
                )
        );
        List<UUID> nextSignals = new ArrayList<>(recentSignals);
        nextSignals.add(signal.signalId());
        while (nextSignals.size() > MAX_RECENT_SIGNALS) {
            nextSignals.remove(0);
        }
        return new CompanionLearningProfile(
                nextRevision(),
                false,
                nextReliability,
                nextPreferences,
                nextHabits,
                nextSignals
        );
    }

    public CompanionLearningProfile withFrozen(boolean frozen) {
        if (this.frozen == frozen) {
            return this;
        }
        return new CompanionLearningProfile(
                nextRevision(),
                frozen,
                reliability,
                preferences,
                habits,
                recentSignals
        );
    }

    private long nextRevision() {
        return revision == Long.MAX_VALUE ? 1L : revision + 1L;
    }

    private static void ensureReliabilityCapacity(
            Map<OrchestrationId, AffordanceReliability> values,
            OrchestrationId incoming
    ) {
        if (!values.containsKey(incoming)
                && values.size() >= MAX_PROJECTIONS) {
            values.entrySet().stream().min(Comparator.<Map.Entry<
                    OrchestrationId, AffordanceReliability>>
                    comparingLong(entry ->
                            entry.getValue().lastUpdatedTick())
                    .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .ifPresent(values::remove);
        }
    }

    private static void ensurePreferenceCapacity(
            Map<OrchestrationId, OwnerPreference> values,
            OrchestrationId incoming
    ) {
        if (!values.containsKey(incoming)
                && values.size() >= MAX_PROJECTIONS) {
            values.entrySet().stream().min(Comparator.<Map.Entry<
                    OrchestrationId, OwnerPreference>>
                    comparingLong(entry ->
                            entry.getValue().lastUpdatedTick())
                    .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .ifPresent(values::remove);
        }
    }

    private static void ensureHabitCapacity(
            Map<OrchestrationId, HabitForecast> values,
            OrchestrationId incoming
    ) {
        if (!values.containsKey(incoming)
                && values.size() >= MAX_PROJECTIONS) {
            values.entrySet().stream().min(Comparator.<Map.Entry<
                    OrchestrationId, HabitForecast>>
                    comparingLong(entry ->
                            entry.getValue().lastUpdatedTick())
                    .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .ifPresent(values::remove);
        }
    }
}
