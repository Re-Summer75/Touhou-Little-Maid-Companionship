package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.learning.DefaultCompanionLearningService;
import com.laixia.maidintelligence.feature.behavior.codec.CompanionLearningProfileCodec;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningSignal;
import com.laixia.maidintelligence.feature.behavior.port.LearningProfilePort;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EventIdentity;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationOutcome;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;
import com.mojang.serialization.JsonOps;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class BoundedLearningVerification {
    private static final OrchestrationId INTENT =
            new OrchestrationId("test", "intent");
    private static final OrchestrationId PLAN =
            new OrchestrationId("test", "plan");

    private BoundedLearningVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        signalsAreDeduplicatedAndProjectionCardinalityIsBounded();
        shadowModeRecordsWithoutChangingUtility();
        profileCodecRoundTrips();
    }

    private static void signalsAreDeduplicatedAndProjectionCardinalityIsBounded() {
        CompanionLearningProfile profile =
                CompanionLearningProfile.empty();
        LearningSignal first = signal(1, CompanionIntentIds.APPROACH_OWNER);
        profile = profile.observe(first);
        CompanionLearningProfile duplicate = profile.observe(first);
        require(duplicate == profile,
                "Duplicate LearningSignal changed projections");
        require(profile.reliability().size() == 1
                        && profile.preferences().size() == 1
                        && profile.habits().size() == 1,
                "Outcome did not update all three projections");

        for (int index = 2; index <= 200; index++) {
            profile = profile.observe(signal(
                    index,
                    new OrchestrationId("test", "action/" + index)
            ));
        }
        require(profile.reliability().size()
                        <= CompanionLearningProfile.MAX_PROJECTIONS
                        && profile.preferences().size()
                        <= CompanionLearningProfile.MAX_PROJECTIONS
                        && profile.habits().size()
                        <= CompanionLearningProfile.MAX_PROJECTIONS
                        && profile.recentSignals().size()
                        <= CompanionLearningProfile.MAX_RECENT_SIGNALS,
                "Learning projection exceeded a hard cardinality bound");

        CompanionLearningProfile frozen = profile.withFrozen(true);
        require(frozen.observe(signal(
                500,
                CompanionIntentIds.DEPLOY_BOAT
        )) == frozen, "Frozen profile accepted a learning update");
    }

    private static void shadowModeRecordsWithoutChangingUtility() {
        Object maid = new Object();
        MemoryProfiles profiles = new MemoryProfiles();
        AtomicReference<LearningMode> mode =
                new AtomicReference<>(LearningMode.SHADOW);
        DefaultCompanionLearningService<Object> learning =
                new DefaultCompanionLearningService<>(
                        profiles,
                        mode::get
                );
        for (int index = 0; index < 8; index++) {
            learning.record(maid, outcome(index + 1));
        }
        IntentCatalog.CompiledIntent intent = compiledIntent();
        require(learning.modifier(maid, intent, 6_000L) == 0.0D,
                "Shadow learning modified live Utility");
        require(learning.profile(maid).reliability().size() == 1,
                "Shadow mode failed to record projections");

        mode.set(LearningMode.ACTIVE);
        double active = learning.modifier(maid, intent, 6_000L);
        require(active > 0.0D && active <= 20.0D,
                "Active learning modifier was not positive and bounded");
        require(learning.export(maid).contains("\"reliability\""),
                "Learning diagnostics export omitted projections");
        learning.reset(maid);
        require(learning.profile(maid).revision() == 0L,
                "Learning reset retained old state");
    }

    private static void profileCodecRoundTrips() {
        CompanionLearningProfile expected =
                CompanionLearningProfile.empty().observe(
                        signal(900, CompanionIntentIds.DEPLOY_BOAT)
                );
        var encoded = CompanionLearningProfileCodec.CODEC.encodeStart(
                JsonOps.INSTANCE,
                expected
        ).result().orElseThrow();
        CompanionLearningProfile decoded =
                CompanionLearningProfileCodec.CODEC.parse(
                        JsonOps.INSTANCE,
                        encoded
                ).result().orElseThrow();
        require(decoded.equals(expected),
                "Persistent learning profile codec changed state");
    }

    private static IntentCatalog.CompiledIntent compiledIntent() {
        PlanDefinition plan = new PlanDefinition(
                PLAN,
                "act",
                Map.of("act", new PlanDefinition.State(
                        CompanionIntentIds.APPROACH_OWNER,
                        Map.of(
                                "speed", "0.5",
                                "close_distance", "2"
                        ),
                        20,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE
                ))
        );
        IntentDefinition intent = new IntentDefinition(
                INTENT,
                PLAN,
                List.of(),
                List.of(),
                1.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        return IntentCatalog.compile(
                1L,
                List.of(intent),
                List.of(plan),
                CompanionIntentIds.vocabulary()
        ).intent(INTENT);
    }

    private static LearningSignal signal(
            long id,
            OrchestrationId action
    ) {
        return LearningSignal.from(new OperationOutcome(
                EventIdentity.root(new UUID(0L, id)),
                INTENT,
                PLAN,
                "act",
                action,
                OperationStatus.SUCCEEDED,
                id,
                id,
                "verification"
        ));
    }

    private static OperationOutcome outcome(long id) {
        return new OperationOutcome(
                EventIdentity.root(new UUID(0L, id)),
                INTENT,
                PLAN,
                "act",
                CompanionIntentIds.APPROACH_OWNER,
                OperationStatus.SUCCEEDED,
                id,
                id,
                "verification"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryProfiles
            implements LearningProfilePort<Object> {
        private CompanionLearningProfile profile =
                CompanionLearningProfile.empty();

        @Override
        public CompanionLearningProfile load(Object subject) {
            return profile;
        }

        @Override
        public void save(
                Object subject,
                CompanionLearningProfile profile
        ) {
            this.profile = profile;
        }
    }
}
