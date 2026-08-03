package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.application.observation.BoundedCompanionMailbox;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.Belief;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EpisodicEvent;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.EventIdentity;
import com.laixia.maidintelligence.feature.orchestration.domain.observation.OperationStatus;
import com.laixia.maidintelligence.feature.orchestration.port.CompanionMemoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

@SuppressWarnings("null")
public final class CompanionObservabilityVerification {
    private CompanionObservabilityVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        mailboxIsBoundedAndDeduplicated();
        terminalActionsRecordExactlyOnePersistentOutcome();
        persistentBeliefsRestoreIntoNewRuntime();
    }

    private static void mailboxIsBoundedAndDeduplicated() {
        BoundedCompanionMailbox mailbox = new BoundedCompanionMailbox();
        EpisodicEvent first = event(1L);
        require(mailbox.publish(first, 0L),
                "Mailbox rejected a new event");
        require(!mailbox.publish(first, 0L),
                "Mailbox accepted a duplicate event identity");
        for (long index = 2L; index <= 80L; index++) {
            mailbox.publish(event(index), 0L);
        }
        require(mailbox.snapshot(0L).events().size()
                        == BoundedCompanionMailbox.MAX_EVENTS,
                "Mailbox exceeded its event bound");
        require(mailbox.snapshot(100L).events().isEmpty(),
                "Expired episodic events were retained");
    }

    private static void terminalActionsRecordExactlyOnePersistentOutcome() {
        RecordingMemoryPort memory = new RecordingMemoryPort();
        Runtime runtime = runtime(memory, SUCCEED);
        require(runtime.intents.tick("maid", 0L),
                "Observable intent did not execute");

        DecisionTrace trace = runtime.intents.inspectDecision("maid");
        require(trace.decisionId().getLeastSignificantBits() != 0L
                        || trace.decisionId().getMostSignificantBits() != 0L,
                "Decision trace did not receive an identity");
        require(trace.observations().outcomes().size() == 1,
                "Terminal action did not record exactly one outcome");
        require(trace.observations().outcomes().get(0).status()
                        == OperationStatus.SUCCEEDED,
                "Terminal outcome status is wrong");
        require(memory.value.outcomes().size() == 1,
                "Terminal outcome was not persisted");

        BoundedCompanionMailbox mailbox = new BoundedCompanionMailbox();
        require(mailbox.record(memory.value.outcomes().get(0)),
                "Mailbox rejected the first operation outcome");
        require(!mailbox.record(memory.value.outcomes().get(0)),
                "Mailbox accepted a duplicate operation outcome");
    }

    private static void persistentBeliefsRestoreIntoNewRuntime() {
        RecordingMemoryPort memory = new RecordingMemoryPort();
        Runtime first = runtime(memory, WAIT);
        Belief persistent = new Belief(
                FACT_A,
                0.75D,
                FACT_B,
                0.9D,
                0L,
                1_000L,
                true
        );
        require(first.intents.rememberBelief(
                        "maid",
                        persistent,
                        0L
                ),
                "Persistent belief was rejected");
        require(memory.value.beliefs().size() == 1,
                "Persistent belief was not stored");

        Runtime restored = runtime(memory, WAIT);
        restored.intents.rememberBelief(
                "maid",
                new Belief(
                        FACT_B,
                        1.0D,
                        FACT_A,
                        1.0D,
                        1L,
                        20L,
                        false
                ),
                1L
        );
        require(restored.intents.observations("maid", 1L)
                        .beliefs().contains(persistent),
                "Persistent belief was not restored");
    }

    private static Runtime runtime(
            RecordingMemoryPort memory,
            OrchestrationId action
    ) {
        MutableIntentCatalog catalog = new MutableIntentCatalog();
        PlanDefinition plan = plan(id("plan/observable"), action, 20);
        IntentDefinition intent = definition(
                id("intent/observable"),
                plan.id(),
                List.of(),
                1.0D,
                0,
                0,
                0.0D
        );
        catalog.publish(IntentCatalog.compile(
                1L,
                List.of(intent),
                List.of(plan),
                VOCABULARY
        ));
        Map<OrchestrationId, Double> facts = new HashMap<>();
        MaidIntentApi<String> intents = new DefaultMaidIntentOrchestrator<>(
                catalog,
                (subject, gameTime, requested, output) -> {
                    for (int index = 0; index < requested.size(); index++) {
                        output[index] = facts.getOrDefault(
                                requested.get(index),
                                Double.NaN
                        );
                    }
                },
                new IntentVerificationFixture.FakeActions(),
                String::hashCode,
                () -> true,
                () -> 1,
                () -> 128,
                () -> true,
                memory
        );
        return new Runtime(intents);
    }

    private static EpisodicEvent event(long value) {
        return new EpisodicEvent(
                EventIdentity.root(new UUID(0L, value)),
                SIGNAL,
                0L,
                100L,
                Map.of("value", Long.toString(value))
        );
    }

    private record Runtime(MaidIntentApi<String> intents) {
    }

    private static final class RecordingMemoryPort
            implements CompanionMemoryPort<String> {
        private CompanionPersistentMemory value =
                CompanionPersistentMemory.initial();

        @Override
        public CompanionPersistentMemory load(String subject) {
            return value;
        }

        @Override
        public void save(
                String subject,
                CompanionPersistentMemory memory
        ) {
            value = memory;
        }
    }
}
