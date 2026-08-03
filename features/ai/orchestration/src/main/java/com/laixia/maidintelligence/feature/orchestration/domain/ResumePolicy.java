package com.laixia.maidintelligence.feature.orchestration.domain;

public enum ResumePolicy {
    NEVER_RESUME(false),
    RESTART_STEP(true),
    RESUME_CHECKPOINT(true),
    REPLAN_SUFFIX(true),
    ATOMIC(false);

    private final boolean suspendable;

    ResumePolicy(boolean suspendable) {
        this.suspendable = suspendable;
    }

    public boolean suspendable() {
        return suspendable;
    }
}
