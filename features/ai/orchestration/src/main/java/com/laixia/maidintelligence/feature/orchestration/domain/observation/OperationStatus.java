package com.laixia.maidintelligence.feature.orchestration.domain.observation;

import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;

public enum OperationStatus {
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public static OperationStatus from(ActionResult result) {
        return switch (result) {
            case SUCCEEDED -> SUCCEEDED;
            case FAILED -> FAILED;
            case CANCELLED -> CANCELLED;
            case RUNNING -> throw new IllegalArgumentException(
                    "Running is not a terminal operation status"
            );
        };
    }
}
