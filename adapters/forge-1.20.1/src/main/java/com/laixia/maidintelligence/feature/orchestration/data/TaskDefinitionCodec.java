package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;
import com.laixia.maidintelligence.feature.orchestration.domain.task.MethodSelection;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskMethod;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskStep;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * Reads {@code maid_ai/tasks/*.json}.
 *
 * <p>A step names either an {@code action} or a {@code task} and never both.
 * That is checked here rather than left to the expander so the error names the
 * file that is wrong, instead of a generated plan the author never wrote.
 */
final class TaskDefinitionCodec {
    private static final Codec<Step> STEP =
            RecordCodecBuilder.create(instance -> instance.group(
                    OrchestrationCodecSupport.ID
                            .optionalFieldOf("action")
                            .forGetter(Step::action),
                    OrchestrationCodecSupport.ID
                            .optionalFieldOf("task")
                            .forGetter(Step::task),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING)
                            .optionalFieldOf("parameters", Map.of())
                            .forGetter(Step::parameters),
                    Codec.intRange(1, 12_000)
                            .optionalFieldOf("timeout_ticks", 200)
                            .forGetter(Step::timeoutTicks)
            ).apply(instance, Step::new));

    /**
     * Every field optional, and absent means "inherit from the intent that
     * named this task" rather than "use a default". A method that differs only
     * in how often it is worth checking should say only that.
     */
    private static final Codec<MethodSelection> SELECTION =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.DOUBLE.optionalFieldOf("base_score")
                            .forGetter(s -> box(s.baseScore())),
                    Codec.DOUBLE.optionalFieldOf("minimum_score")
                            .forGetter(s -> box(s.minimumScore())),
                    Codec.DOUBLE.optionalFieldOf("activation_chance")
                            .forGetter(s -> box(s.activationChance())),
                    Codec.INT.optionalFieldOf("evaluation_interval_ticks")
                            .forGetter(s -> box(s.evaluationIntervalTicks())),
                    Codec.INT.optionalFieldOf("minimum_commit_ticks")
                            .forGetter(s -> box(s.minimumCommitTicks())),
                    Codec.DOUBLE.optionalFieldOf("switch_margin")
                            .forGetter(s -> box(s.switchMargin())),
                    Codec.INT.optionalFieldOf("interrupt_priority")
                            .forGetter(s -> box(s.interruptPriority())),
                    Codec.INT.optionalFieldOf("cooldown_ticks")
                            .forGetter(s -> box(s.cooldownTicks()))
            ).apply(instance, TaskDefinitionCodec::selection));

    private static final Codec<Method> METHOD =
            RecordCodecBuilder.create(instance -> instance.group(
                    IntentDefinitionCodec.CONDITION.listOf()
                            .optionalFieldOf("conditions", List.of())
                            .forGetter(Method::conditions),
                    STEP.listOf()
                            .fieldOf("subtasks")
                            .forGetter(Method::subtasks),
                    SELECTION.optionalFieldOf(
                            "selection",
                            MethodSelection.inherit()
                    ).forGetter(Method::selection),
                    IntentDefinitionCodec.CONSIDERATION.listOf()
                            .optionalFieldOf("utility", List.of())
                            .forGetter(Method::utility)
            ).apply(instance, Method::new));

    private static final Codec<Payload> PAYLOAD =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(1, 1)
                            .fieldOf("format_version")
                            .forGetter(Payload::formatVersion),
                    METHOD.listOf()
                            .fieldOf("methods")
                            .forGetter(Payload::methods)
            ).apply(instance, Payload::new));

    private TaskDefinitionCodec() {
    }

    static DataResult<TaskDefinition> parse(
            OrchestrationId id,
            JsonElement json
    ) {
        return PAYLOAD.parse(JsonOps.INSTANCE, json)
                .flatMap(payload -> create(id, payload));
    }

    private static DataResult<TaskDefinition> create(
            OrchestrationId id,
            Payload payload
    ) {
        try {
            List<TaskMethod> methods = payload.methods().stream()
                    .map(TaskDefinitionCodec::method)
                    .toList();
            return DataResult.success(new TaskDefinition(id, methods));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(
                    () -> "Task " + id + ": " + exception.getMessage()
            );
        }
    }

    private static TaskMethod method(Method method) {
        return new TaskMethod(
                method.conditions(),
                method.subtasks().stream()
                        .map(TaskDefinitionCodec::step)
                        .toList(),
                method.selection(),
                method.utility()
        );
    }

    private static MethodSelection selection(
            java.util.Optional<Double> baseScore,
            java.util.Optional<Double> minimumScore,
            java.util.Optional<Double> activationChance,
            java.util.Optional<Integer> evaluationIntervalTicks,
            java.util.Optional<Integer> minimumCommitTicks,
            java.util.Optional<Double> switchMargin,
            java.util.Optional<Integer> interruptPriority,
            java.util.Optional<Integer> cooldownTicks
    ) {
        return new MethodSelection(
                baseScore.orElse(null),
                minimumScore.orElse(null),
                activationChance.orElse(null),
                evaluationIntervalTicks.orElse(null),
                minimumCommitTicks.orElse(null),
                switchMargin.orElse(null),
                interruptPriority.orElse(null),
                cooldownTicks.orElse(null)
        );
    }

    private static <T> java.util.Optional<T> box(T value) {
        return java.util.Optional.ofNullable(value);
    }

    private static TaskStep step(Step step) {
        boolean hasAction = step.action().isPresent();
        boolean hasTask = step.task().isPresent();
        if (hasAction == hasTask) {
            throw new IllegalArgumentException(
                    hasAction
                            ? "a subtask names both an action and a task"
                            : "a subtask names neither an action nor a task"
            );
        }
        if (hasTask) {
            return TaskStep.task(step.task().orElseThrow(), step.timeoutTicks());
        }
        return TaskStep.action(
                step.action().orElseThrow(),
                step.parameters(),
                step.timeoutTicks()
        );
    }

    private record Step(
            java.util.Optional<OrchestrationId> action,
            java.util.Optional<OrchestrationId> task,
            Map<String, String> parameters,
            int timeoutTicks
    ) {
    }

    private record Method(
            List<FactCondition> conditions,
            List<Step> subtasks,
            MethodSelection selection,
            List<UtilityConsideration> utility
    ) {
    }

    private record Payload(
            int formatVersion,
            List<Method> methods
    ) {
    }
}
