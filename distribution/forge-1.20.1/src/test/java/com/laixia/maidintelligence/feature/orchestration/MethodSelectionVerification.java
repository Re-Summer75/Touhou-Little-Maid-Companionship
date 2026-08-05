package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.laixia.maidintelligence.feature.orchestration.domain.task.MethodSelection;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskExpansion;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskIntentCompiler;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskMethod;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_A;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_B;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.SUCCEED;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.WAIT;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.expectFailure;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.id;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;

/**
 * Per-method overrides: what a method may change about how eagerly it is
 * attempted, and where it may not.
 *
 * <p>Split from {@code TaskDecompositionVerification} when that file reached
 * the size limit. The two halves ask different questions — that one is about
 * what a task flattens into, this one about what the flattened intents inherit.
 */
public final class MethodSelectionVerification {
    private static final OrchestrationId TEMPLATE = id("intent/obtain_food");

    private MethodSelectionVerification() {
    }

    public static void main(String[] args) {
        methodsOverrideOnlyWhatTheyDeclare();
        methodUtilityReplacesRatherThanAppends();
        nestedMethodsMayNotOverrideSelection();
        outOfRangeOverridesAreRejected();
    }

    /**
     * Methods that differ in urgency rather than in preference need to say so.
     * Absent fields must inherit, or every method would have to restate the
     * whole selection block and the duplication would move rather than go.
     */
    private static void methodsOverrideOnlyWhatTheyDeclare() {
        OrchestrationId task = id("task/urgency");
        TaskDefinition definition = new TaskDefinition(task, List.of(
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(SUCCEED, Map.of(), 20)),
                        new MethodSelection(
                                1.0D, null, 1.0D, 20,
                                null, null, 60, 40
                        ),
                        List.of()
                ),
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(WAIT, Map.of(), 20))
                )
        ));
        List<IntentDefinition> intents = TaskIntentCompiler.compile(
                templateFor(task),
                Map.of(task, definition)
        ).intents();

        IntentDefinition urgent = intents.get(0);
        require(urgent.baseScore() == 1.0D
                        && urgent.activationChance() == 1.0D
                        && urgent.evaluationIntervalTicks() == 20
                        && urgent.interruptPriority() == 60
                        && urgent.cooldownTicks() == 40,
                "Declared overrides were not applied");
        require(urgent.minimumCommitTicks() == 7
                        && urgent.switchMargin() == 0.25D,
                "Undeclared fields did not inherit from the template");

        IntentDefinition inherited = intents.get(1);
        require(inherited.baseScore() == 0.5D
                        && inherited.interruptPriority() == 30
                        && inherited.evaluationIntervalTicks() == 5,
                "A method declaring no selection did not inherit all of it");
    }

    private static void methodUtilityReplacesRatherThanAppends() {
        OrchestrationId task = id("task/utility");
        UtilityConsideration own = new UtilityConsideration(
                FACT_B,
                0.0D,
                20.0D,
                0.1D,
                UtilityCurve.INVERSE_LINEAR
        );
        TaskDefinition definition = new TaskDefinition(task, List.of(
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(SUCCEED, Map.of(), 20)),
                        MethodSelection.inherit(),
                        List.of(own)
                ),
                new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(WAIT, Map.of(), 20))
                )
        ));
        List<IntentDefinition> intents = TaskIntentCompiler.compile(
                templateFor(task),
                Map.of(task, definition)
        ).intents();

        require(intents.get(0).considerations().equals(List.of(own)),
                "A method's own utility did not replace the template's");
        require(intents.get(1).considerations().size() == 1
                        && intents.get(1).considerations().get(0).fact()
                        .equals(FACT_A),
                "A method declaring no utility did not inherit the "
                        + "template's");
    }

    /**
     * A nested method expands into the middle of another branch, so there is no
     * single selection for it to override. Rejecting says that; picking one
     * silently would not.
     */
    private static void nestedMethodsMayNotOverrideSelection() {
        OrchestrationId inner = id("task/inner");
        OrchestrationId outer = id("task/outer");
        Map<OrchestrationId, TaskDefinition> tasks = new LinkedHashMap<>();
        tasks.put(inner, new TaskDefinition(inner, List.of(new TaskMethod(
                List.of(),
                List.of(TaskStep.action(SUCCEED, Map.of(), 20)),
                new MethodSelection(
                        null, null, null, null, null, null, 90, null
                ),
                List.of()
        ))));
        tasks.put(outer, new TaskDefinition(outer, List.of(new TaskMethod(
                List.of(),
                List.of(TaskStep.task(inner, 20))
        ))));
        expectFailure(() -> TaskExpansion.expand(outer, tasks),
                "a nested method overriding selection");
        // The same definition is fine when it is the one the intent names.
        TaskExpansion.expand(inner, tasks);
    }

    /**
     * An override is validated by the same constructor that validates an intent
     * file, so a bad number in a task fails the same way it would anywhere
     * else.
     */
    private static void outOfRangeOverridesAreRejected() {
        OrchestrationId task = id("task/bad");
        Map<OrchestrationId, TaskDefinition> tasks = Map.of(
                task,
                new TaskDefinition(task, List.of(new TaskMethod(
                        List.of(),
                        List.of(TaskStep.action(SUCCEED, Map.of(), 20)),
                        new MethodSelection(
                                null, null, 2.0D, null, null, null, null, null
                        ),
                        List.of()
                )))
        );
        expectFailure(
                () -> TaskIntentCompiler.compile(templateFor(task), tasks),
                "an activation chance above one"
        );
    }

    private static IntentDefinition templateFor(OrchestrationId task) {
        return new IntentDefinition(
                TEMPLATE,
                task,
                List.of(),
                List.of(new UtilityConsideration(
                        FACT_A,
                        0.0D,
                        1.0D,
                        1.0D,
                        UtilityCurve.LINEAR
                )),
                0.5D,
                0.0D,
                1.0D,
                5,
                7,
                0.25D,
                30,
                0
        );
    }
}
