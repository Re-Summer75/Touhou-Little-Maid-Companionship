package com.laixia.maidintelligence.feature.interaction.port;

/**
 * Interaction-owned status capabilities needed by manual feeding.
 */
public interface MaidFeedingStatusPort<S, I> {
    boolean isSaturationFull(S subject);

    void captureFoodNutrition(S subject, I food);

    void restoreFromFood(
            S subject,
            int nutrition,
            float saturationModifier
    );
}
