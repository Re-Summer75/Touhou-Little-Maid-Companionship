package com.laixia.maidintelligence.feature.orchestration.domain.task;

import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityConsideration;

import java.util.List;

/**
 * Per-method overrides of how eagerly a decomposition branch is attempted.
 *
 * <p>Classic task networks let methods differ only in applicability and steps,
 * and rank them by writing order. That is not enough for the intents this
 * replaces: reaching a snack cabinet is checked every twenty ticks and preempts
 * at priority sixty, while asking the owner is checked every hundred with a one
 * in ten chance at priority forty. Those are not two ways of saying "prefer the
 * cabinet" — they are genuinely different costs of attempting, and collapsing
 * them into an ordering would change behaviour rather than express it.
 *
 * <p>Every field is optional. An absent field inherits the intent that named
 * the task, so a method overrides only what it actually differs in.
 *
 * <p>Only a task named directly by an intent may carry these. A nested task
 * expands into the middle of someone else's branch, where there is no single
 * selection to override and no obvious answer to which of two competing
 * overrides should win, so {@link TaskExpansion} rejects them instead of
 * picking one.
 */
public record MethodSelection(
        Double baseScore,
        Double minimumScore,
        Double activationChance,
        Integer evaluationIntervalTicks,
        Integer minimumCommitTicks,
        Double switchMargin,
        Integer interruptPriority,
        Integer cooldownTicks
) {
    private static final MethodSelection INHERIT = new MethodSelection(
            null, null, null, null, null, null, null, null
    );

    public static MethodSelection inherit() {
        return INHERIT;
    }

    public boolean overridesNothing() {
        return equals(INHERIT);
    }

    /**
     * Builds the definition for one branch: the template, with this method's
     * overrides applied and the branch's own guards and considerations.
     *
     * <p>Validation stays where it already lives — the resulting record's own
     * constructor rejects an out-of-range override, so a bad value in a task
     * file fails for the same reason and with the same message as a bad value
     * in an intent file.
     */
    IntentDefinition applyTo(
            IntentDefinition template,
            OrchestrationId id,
            OrchestrationId plan,
            List<FactCondition> conditions,
            List<UtilityConsideration> considerations
    ) {
        return new IntentDefinition(
                id,
                plan,
                conditions,
                considerations,
                or(baseScore, template.baseScore()),
                or(minimumScore, template.minimumScore()),
                or(activationChance, template.activationChance()),
                or(evaluationIntervalTicks, template.evaluationIntervalTicks()),
                or(minimumCommitTicks, template.minimumCommitTicks()),
                or(switchMargin, template.switchMargin()),
                or(interruptPriority, template.interruptPriority()),
                or(cooldownTicks, template.cooldownTicks()),
                template.aggregation()
        );
    }

    private static double or(Double override, double inherited) {
        return override == null ? inherited : override;
    }

    private static int or(Integer override, int inherited) {
        return override == null ? inherited : override;
    }
}
