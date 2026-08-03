package com.laixia.maidintelligence.feature.behavior.application.learning;

import com.laixia.maidintelligence.feature.behavior.api.MaidLearningApi;
import com.laixia.maidintelligence.feature.behavior.domain.learning.AffordanceReliability;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.domain.learning.HabitForecast;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningSignal;
import com.laixia.maidintelligence.feature.behavior.domain.learning.OwnerPreference;
import com.laixia.maidintelligence.feature.behavior.port.LearningProfilePort;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;
import com.laixia.maidintelligence.feature.orchestration.port.OperationOutcomePort;
import com.laixia.maidintelligence.feature.orchestration.port.UtilityModifierPort;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class DefaultCompanionLearningService<M>
        implements MaidLearningApi<M>,
        OperationOutcomePort<M>,
        UtilityModifierPort<M> {
    private static final double MAX_MODIFIER = 20.0D;
    private static final double UNIFORM_HABIT_PROBABILITY = 1.0D / 24.0D;

    private final LearningProfilePort<M> profiles;
    private final Supplier<LearningMode> mode;

    public DefaultCompanionLearningService(
            LearningProfilePort<M> profiles,
            Supplier<LearningMode> mode
    ) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public LearningMode mode() {
        LearningMode current = mode.get();
        return current == null ? LearningMode.SHADOW : current;
    }

    @Override
    public CompanionLearningProfile profile(M subject) {
        CompanionLearningProfile loaded = profiles.load(subject);
        return loaded == null
                ? CompanionLearningProfile.empty()
                : loaded;
    }

    @Override
    public void record(M subject, OperationOutcome outcome) {
        CompanionLearningProfile current = profile(subject);
        CompanionLearningProfile updated =
                current.observe(LearningSignal.from(outcome));
        if (updated != current) {
            profiles.save(subject, updated);
        }
    }

    @Override
    public double modifier(
            M subject,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    ) {
        double projected = projectedModifier(
                profile(subject),
                intent,
                gameTime
        );
        return mode() == LearningMode.ACTIVE ? projected : 0.0D;
    }

    @Override
    public boolean freeze(M subject, boolean frozen) {
        CompanionLearningProfile current = profile(subject);
        CompanionLearningProfile updated = current.withFrozen(frozen);
        if (updated == current) {
            return false;
        }
        profiles.save(subject, updated);
        return true;
    }

    @Override
    public void reset(M subject) {
        profiles.save(subject, CompanionLearningProfile.empty());
    }

    @Override
    public String export(M subject) {
        CompanionLearningProfile profile = profile(subject);
        StringBuilder output = new StringBuilder(512);
        output.append('{')
                .append("\"revision\":").append(profile.revision())
                .append(",\"mode\":\"").append(mode().name())
                .append("\",\"frozen\":").append(profile.frozen())
                .append(",\"reliability\":{");
        appendReliability(output, profile.reliability());
        output.append("},\"preferences\":{");
        appendPreferences(output, profile.preferences());
        output.append("},\"habits\":{");
        appendHabits(output, profile.habits());
        return output.append("}}").toString();
    }

    private static double projectedModifier(
            CompanionLearningProfile profile,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    ) {
        OrchestrationId action = intent.plan().states()
                .get(intent.plan().initialState())
                .action();
        AffordanceReliability reliability =
                profile.reliability().get(action);
        OwnerPreference preference =
                profile.preferences().get(intent.id());
        HabitForecast habit = profile.habits().get(action);
        double reliabilityModifier = reliability == null
                ? 0.0D
                : (reliability.betaMean() - 0.5D) * 12.0D;
        double preferenceModifier = preference == null
                ? 0.0D
                : preference.value() * 8.0D;
        int bucket = (int) (Math.floorMod(gameTime, 24_000L) / 1_000L);
        double habitModifier = habit == null || habit.samples() < 4
                ? 0.0D
                : (habit.probability(bucket)
                - UNIFORM_HABIT_PROBABILITY) * 60.0D;
        double combined = reliabilityModifier
                + preferenceModifier
                + habitModifier;
        return Math.max(-MAX_MODIFIER, Math.min(MAX_MODIFIER, combined));
    }

    private static void appendReliability(
            StringBuilder output,
            Map<OrchestrationId, AffordanceReliability> values
    ) {
        boolean first = true;
        for (var entry : values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            first = separator(output, first);
            output.append('"').append(entry.getKey()).append("\":[")
                    .append(entry.getValue().successes()).append(',')
                    .append(entry.getValue().failures()).append(']');
        }
    }

    private static void appendPreferences(
            StringBuilder output,
            Map<OrchestrationId, OwnerPreference> values
    ) {
        boolean first = true;
        for (var entry : values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            first = separator(output, first);
            output.append('"').append(entry.getKey()).append("\":[")
                    .append(entry.getValue().value()).append(',')
                    .append(entry.getValue().samples()).append(']');
        }
    }

    private static void appendHabits(
            StringBuilder output,
            Map<OrchestrationId, HabitForecast> values
    ) {
        boolean first = true;
        for (var entry : values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            first = separator(output, first);
            output.append('"').append(entry.getKey()).append("\":")
                    .append(entry.getValue().dayBuckets());
        }
    }

    private static boolean separator(
            StringBuilder output,
            boolean first
    ) {
        if (!first) {
            output.append(',');
        }
        return false;
    }
}
