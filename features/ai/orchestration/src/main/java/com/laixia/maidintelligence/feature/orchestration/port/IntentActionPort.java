package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.Map;

public interface IntentActionPort<M> {
    ActionResult execute(
            M subject,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    );

    default void cancel(
            M subject,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
    }

    default void quiesce(
            M subject,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
        cancel(subject, action, parameters);
    }

    default void halt(
            M subject,
            OrchestrationId action,
            Map<String, String> parameters,
            String reason
    ) {
        cancel(subject, action, parameters);
    }

    default boolean revalidate(
            M subject,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime
    ) {
        return true;
    }
}
