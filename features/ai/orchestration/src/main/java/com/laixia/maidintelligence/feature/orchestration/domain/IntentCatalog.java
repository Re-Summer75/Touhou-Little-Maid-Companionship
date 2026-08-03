package com.laixia.maidintelligence.feature.orchestration.domain;

import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class IntentCatalog {
    public static final int TERMINAL_SUCCESS = -1;
    public static final int TERMINAL_FAILURE = -2;

    private static final int MAX_INTENTS = 128;
    private static final int MAX_PLANS = 128;
    private static final int MAX_STATES_PER_PLAN = 64;
    private static final int MAX_CONDITIONS_PER_INTENT = 32;
    private static final int MAX_CONSIDERATIONS_PER_INTENT = 32;

    private final long generation;
    private final List<OrchestrationId> facts;
    private final List<CompiledIntent> intents;
    private final Map<OrchestrationId, CompiledIntent> intentsById;
    private final Map<OrchestrationId, CompiledPlan> plansById;
    private final Set<Integer> signalFactIndexes;

    private IntentCatalog(
            long generation,
            List<OrchestrationId> facts,
            List<CompiledIntent> intents,
            Map<OrchestrationId, CompiledPlan> plansById,
            Set<Integer> signalFactIndexes
    ) {
        this.generation = generation;
        this.facts = List.copyOf(facts);
        this.intents = List.copyOf(intents);
        Map<OrchestrationId, CompiledIntent> indexed = new LinkedHashMap<>();
        intents.forEach(intent -> indexed.put(intent.id(), intent));
        this.intentsById = Map.copyOf(indexed);
        this.plansById = Map.copyOf(plansById);
        this.signalFactIndexes = Set.copyOf(signalFactIndexes);
    }

    public static IntentCatalog empty() {
        return new IntentCatalog(
                0L,
                List.of(),
                List.of(),
                Map.of(),
                Set.of()
        );
    }

    public static IntentCatalog compile(
            long generation,
            Collection<IntentDefinition> definitions,
            Collection<PlanDefinition> plans,
            IntentVocabulary vocabulary
    ) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(plans, "plans");
        Objects.requireNonNull(vocabulary, "vocabulary");
        requireMaximum(definitions.size(), MAX_INTENTS, "intents");
        requireMaximum(plans.size(), MAX_PLANS, "plans");

        Map<OrchestrationId, PlanDefinition> rawPlans =
                uniqueById(plans, PlanDefinition::id, "plan");
        Map<OrchestrationId, CompiledPlan> compiledPlans =
                compilePlans(rawPlans, vocabulary);
        Map<OrchestrationId, IntentDefinition> rawIntents =
                uniqueById(definitions, IntentDefinition::id, "intent");

        Set<OrchestrationId> usedFacts = new HashSet<>();
        rawIntents.values().forEach(intent -> {
            requireMaximum(
                    intent.conditions().size(),
                    MAX_CONDITIONS_PER_INTENT,
                    "conditions for " + intent.id()
            );
            requireMaximum(
                    intent.considerations().size(),
                    MAX_CONSIDERATIONS_PER_INTENT,
                    "considerations for " + intent.id()
            );
            intent.conditions().forEach(condition ->
                    validateCondition(condition, vocabulary, usedFacts));
            intent.considerations().forEach(consideration ->
                    validateConsideration(
                            consideration,
                            vocabulary,
                            usedFacts
                    ));
        });
        for (OrchestrationId fact : usedFacts) {
            if (!vocabulary.facts().contains(fact)) {
                throw new IllegalArgumentException(
                        "Unknown orchestration fact: " + fact
                );
            }
        }
        List<OrchestrationId> facts = usedFacts.stream().sorted().toList();
        Map<OrchestrationId, Integer> factIndexes = new HashMap<>();
        for (int index = 0; index < facts.size(); index++) {
            factIndexes.put(facts.get(index), index);
        }

        List<CompiledIntent> compiledIntents = rawIntents.values().stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .map(intent -> compileIntent(
                        intent,
                        compiledPlans,
                        factIndexes
                ))
                .toList();
        Set<Integer> signalIndexes = new HashSet<>();
        vocabulary.signals().forEach(signal -> {
            Integer index = factIndexes.get(signal);
            if (index != null) {
                signalIndexes.add(index);
            }
        });
        return new IntentCatalog(
                generation,
                facts,
                compiledIntents,
                compiledPlans,
                signalIndexes
        );
    }

    public long generation() {
        return generation;
    }

    public List<OrchestrationId> facts() {
        return facts;
    }

    public List<CompiledIntent> intents() {
        return intents;
    }

    public int planCount() {
        return plansById.size();
    }

    public CompiledIntent intent(OrchestrationId id) {
        return intentsById.get(id);
    }

    public CompiledPlan plan(OrchestrationId id) {
        return plansById.get(id);
    }

    public boolean signalFact(int factIndex) {
        return signalFactIndexes.contains(factIndex);
    }

    private static CompiledIntent compileIntent(
            IntentDefinition definition,
            Map<OrchestrationId, CompiledPlan> plans,
            Map<OrchestrationId, Integer> factIndexes
    ) {
        CompiledPlan plan = plans.get(definition.plan());
        if (plan == null) {
            throw new IllegalArgumentException(
                    "Intent " + definition.id()
                            + " references unknown plan " + definition.plan()
            );
        }
        List<CompiledCondition> conditions = definition.conditions().stream()
                .map(condition -> new CompiledCondition(
                        requiredFactIndex(factIndexes, condition.fact()),
                        condition
                ))
                .toList();
        List<CompiledConsideration> considerations =
                definition.considerations().stream()
                        .map(consideration -> new CompiledConsideration(
                                requiredFactIndex(
                                        factIndexes,
                                        consideration.fact()
                                ),
                                consideration
                        ))
                        .toList();
        return new CompiledIntent(
                definition,
                conditions,
                considerations,
                plan
        );
    }

    private static Map<OrchestrationId, CompiledPlan> compilePlans(
            Map<OrchestrationId, PlanDefinition> plans,
            IntentVocabulary vocabulary
    ) {
        Map<OrchestrationId, CompiledPlan> result = new LinkedHashMap<>();
        plans.values().stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(plan -> result.put(
                        plan.id(),
                        compilePlan(plan, vocabulary)
                ));
        return result;
    }

    private static CompiledPlan compilePlan(
            PlanDefinition definition,
            IntentVocabulary vocabulary
    ) {
        requireMaximum(
                definition.states().size(),
                MAX_STATES_PER_PLAN,
                "states for " + definition.id()
        );
        List<String> stateIds = definition.states().keySet().stream()
                .sorted()
                .toList();
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < stateIds.size(); index++) {
            indexes.put(stateIds.get(index), index);
        }
        List<CompiledState> states = new ArrayList<>(stateIds.size());
        for (String stateId : stateIds) {
            PlanDefinition.State state = definition.states().get(stateId);
            if (!vocabulary.actions().contains(state.action())) {
                throw new IllegalArgumentException(
                        "Unknown action " + state.action()
                                + " in plan " + definition.id()
                );
            }
            try {
                vocabulary.actionSchema(state.action())
                        .validate(state.parameters());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid parameters in plan " + definition.id()
                                + " state " + stateId + ": "
                                + exception.getMessage(),
                        exception
                );
            }
            states.add(new CompiledState(
                    stateId,
                    state.action(),
                    state.parameters(),
                    state.timeoutTicks(),
                    transitionIndex(
                            indexes,
                            state.onSuccess(),
                            definition.id()
                    ),
                    transitionIndex(
                            indexes,
                            state.onFailure(),
                            definition.id()
                    ),
                    transitionIndex(
                            indexes,
                            state.onCancelled(),
                            definition.id()
                    )
            ));
        }
        int initialState = indexes.get(definition.initialState());
        validatePlanGraph(definition.id(), states, initialState);
        Set<Integer> checkpoints = definition.checkpoints().stream()
                .map(indexes::get)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new CompiledPlan(
                definition.id(),
                initialState,
                List.copyOf(states),
                definition.resumePolicy(),
                checkpoints,
                definition.maximumSuspendTicks()
        );
    }

    private static void validatePlanGraph(
            OrchestrationId planId,
            List<CompiledState> states,
            int initialState
    ) {
        Set<Integer> reachable = new HashSet<>();
        collectReachable(states, initialState, reachable);
        if (reachable.size() != states.size()) {
            throw new IllegalArgumentException(
                    "Plan " + planId + " contains unreachable states"
            );
        }

        // Cancellation is an external escape hatch; it must not make an
        // otherwise non-terminating success/failure graph appear valid.
        Set<Integer> terminalReachable = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < states.size(); index++) {
                CompiledState state = states.get(index);
                if (!terminalReachable.contains(index)
                        && (terminal(state.successState())
                        || terminal(state.failureState())
                        || terminalReachable.contains(state.successState())
                        || terminalReachable.contains(state.failureState()))) {
                    terminalReachable.add(index);
                    changed = true;
                }
            }
        } while (changed);
        if (terminalReachable.size() != states.size()) {
            throw new IllegalArgumentException(
                    "Plan " + planId + " contains a state with no terminal path"
            );
        }
    }

    private static void collectReachable(
            List<CompiledState> states,
            int stateIndex,
            Set<Integer> reachable
    ) {
        if (terminal(stateIndex) || !reachable.add(stateIndex)) {
            return;
        }
        CompiledState state = states.get(stateIndex);
        collectReachable(states, state.successState(), reachable);
        collectReachable(states, state.failureState(), reachable);
        collectReachable(states, state.cancellationState(), reachable);
    }

    private static int transitionIndex(
            Map<String, Integer> indexes,
            String transition,
            OrchestrationId planId
    ) {
        if (PlanDefinition.SUCCESS.equals(transition)) {
            return TERMINAL_SUCCESS;
        }
        if (PlanDefinition.FAILURE.equals(transition)) {
            return TERMINAL_FAILURE;
        }
        Integer index = indexes.get(transition);
        if (index == null) {
            throw new IllegalArgumentException(
                    "Plan " + planId
                            + " references unknown state " + transition
            );
        }
        return index;
    }

    private static boolean terminal(int stateIndex) {
        return stateIndex == TERMINAL_SUCCESS
                || stateIndex == TERMINAL_FAILURE;
    }

    private static int requiredFactIndex(
            Map<OrchestrationId, Integer> factIndexes,
            OrchestrationId fact
    ) {
        Integer index = factIndexes.get(fact);
        if (index == null) {
            throw new IllegalArgumentException("Unindexed fact: " + fact);
        }
        return index;
    }

    private static void validateCondition(
            FactCondition condition,
            IntentVocabulary vocabulary,
            Set<OrchestrationId> usedFacts
    ) {
        usedFacts.add(condition.fact());
        FactType type = vocabulary.factType(condition.fact());
        if ((type == FactType.BOOLEAN || type == FactType.SIGNAL)
                && condition.expected() != 0.0D
                && condition.expected() != 1.0D) {
            throw new IllegalArgumentException(
                    "Boolean or signal condition must compare with 0 or 1: "
                            + condition.fact()
            );
        }
    }

    private static void validateConsideration(
            UtilityConsideration consideration,
            IntentVocabulary vocabulary,
            Set<OrchestrationId> usedFacts
    ) {
        usedFacts.add(consideration.fact());
        if (vocabulary.factType(consideration.fact())
                == FactType.SIGNAL) {
            throw new IllegalArgumentException(
                    "Signals cannot be Utility inputs: "
                            + consideration.fact()
            );
        }
    }

    private static <T> Map<OrchestrationId, T> uniqueById(
            Collection<T> values,
            java.util.function.Function<T, OrchestrationId> id,
            String kind
    ) {
        Map<OrchestrationId, T> result = new LinkedHashMap<>();
        for (T value : values) {
            OrchestrationId key = id.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Duplicate " + kind + " id: " + key
                );
            }
        }
        return result;
    }

    private static void requireMaximum(
            int actual,
            int maximum,
            String name
    ) {
        if (actual > maximum) {
            throw new IllegalArgumentException(
                    name + " exceeds maximum " + maximum + ": " + actual
            );
        }
    }

    public record CompiledCondition(
            int factIndex,
            FactCondition condition
    ) {
    }

    public record CompiledConsideration(
            int factIndex,
            UtilityConsideration consideration
    ) {
    }

    public record CompiledIntent(
            IntentDefinition definition,
            List<CompiledCondition> conditions,
            List<CompiledConsideration> considerations,
            CompiledPlan plan
    ) {
        public OrchestrationId id() {
            return definition.id();
        }
    }

    public record CompiledPlan(
            OrchestrationId id,
            int initialState,
            List<CompiledState> states,
            ResumePolicy resumePolicy,
            Set<Integer> checkpoints,
            int maximumSuspendTicks
    ) {
    }

    public record CompiledState(
            String id,
            OrchestrationId action,
            Map<String, String> parameters,
            int timeoutTicks,
            int successState,
            int failureState,
            int cancellationState
    ) {
    }
}
