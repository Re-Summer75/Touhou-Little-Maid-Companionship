package com.laixia.maidintelligence.feature.ai.domain;

import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;

/**
 * Computes a transient radius without mutating TLM's persisted value.
 */
public final class DynamicActivityRadiusPolicy {
    public static final DynamicActivityRadiusPolicy INSTANCE =
            new DynamicActivityRadiusPolicy();

    private DynamicActivityRadiusPolicy() {
    }

    public float effectiveRadius(
            float baseRadius,
            boolean ownerStationary,
            ActivityKind activityKind,
            MaidAiTuning.ActivityRadius tuning
    ) {
        if (!tuning.enabled()
                || !ownerStationary
                || !Float.isFinite(baseRadius)
                || baseRadius < 0.0F) {
            return baseRadius;
        }

        int bonus = switch (activityKind) {
            case IDLE -> tuning.idleBonus();
            case WORK -> tuning.workBonus();
            case COMBAT -> tuning.combatBonus();
        };
        float expanded = baseRadius + bonus;
        float capped = Math.min(expanded, tuning.maximumRadius());
        return Math.max(baseRadius, capped);
    }

    public enum ActivityKind {
        IDLE,
        WORK,
        COMBAT
    }
}
