package com.laixia.maidintelligence.feature.orchestration.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record PlanDefinition(
        OrchestrationId id,
        String initialState,
        Map<String, State> states,
        ResumePolicy resumePolicy,
        Set<String> checkpoints,
        int maximumSuspendTicks
) {
    public static final String SUCCESS = "$success";
    public static final String FAILURE = "$failure";

    private static final Pattern STATE_ID =
            Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public PlanDefinition(
            OrchestrationId id,
            String initialState,
            Map<String, State> states
    ) {
        this(
                id,
                initialState,
                states,
                ResumePolicy.NEVER_RESUME,
                Set.of(),
                1_200
        );
    }

    public PlanDefinition(
            OrchestrationId id,
            String initialState,
            Map<String, State> states,
            ResumePolicy resumePolicy
    ) {
        this(id, initialState, states, resumePolicy, Set.of(), 1_200);
    }

    public PlanDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resumePolicy, "resumePolicy");
        Objects.requireNonNull(checkpoints, "checkpoints");
        requireStateId(initialState, "initialState");
        states = Map.copyOf(new LinkedHashMap<>(states));
        checkpoints = Set.copyOf(checkpoints);
        if (states.isEmpty()) {
            throw new IllegalArgumentException("plan states cannot be empty");
        }
        if (!states.containsKey(initialState)) {
            throw new IllegalArgumentException(
                    "Missing initial plan state: " + initialState
            );
        }
        states.forEach((stateId, state) -> {
            requireStateId(stateId, "state");
            Objects.requireNonNull(state, "state");
        });
        for (String checkpoint : checkpoints) {
            requireStateId(checkpoint, "checkpoint");
            if (!states.containsKey(checkpoint)) {
                throw new IllegalArgumentException(
                        "Unknown checkpoint state: " + checkpoint
                );
            }
        }
        if (maximumSuspendTicks < 1
                || maximumSuspendTicks > 72_000) {
            throw new IllegalArgumentException(
                    "maximumSuspendTicks must be in [1, 72000]"
            );
        }
    }

    public record State(
            OrchestrationId action,
            Map<String, String> parameters,
            int timeoutTicks,
            String onSuccess,
            String onFailure,
            String onCancelled
    ) {
        public State(
                OrchestrationId action,
                Map<String, String> parameters,
                int timeoutTicks,
                String onSuccess,
                String onFailure
        ) {
            this(
                    action,
                    parameters,
                    timeoutTicks,
                    onSuccess,
                    onFailure,
                    FAILURE
            );
        }

        public State {
            Objects.requireNonNull(action, "action");
            parameters = Map.copyOf(new LinkedHashMap<>(parameters));
            if (timeoutTicks < 1 || timeoutTicks > 72_000) {
                throw new IllegalArgumentException(
                        "timeoutTicks must be in [1, 72000]"
                );
            }
            requireTransition(onSuccess, "onSuccess");
            requireTransition(onFailure, "onFailure");
            requireTransition(onCancelled, "onCancelled");
        }
    }

    public static boolean terminal(String transition) {
        return SUCCESS.equals(transition) || FAILURE.equals(transition);
    }

    private static void requireTransition(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!terminal(value)) {
            requireStateId(value, name);
        }
    }

    private static void requireStateId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!STATE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    name + " is not a valid state id: " + value
            );
        }
    }
}
