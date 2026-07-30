package com.laixia.maidintelligence.feature.status.service;

import net.minecraft.world.InteractionHand;

public record ToolReplacementResult(
        boolean lowDurability,
        boolean replaced,
        InteractionHand hand,
        int remainingDurability,
        int maximumDurability
) {
    public static ToolReplacementResult none() {
        return new ToolReplacementResult(false, false, InteractionHand.MAIN_HAND, 0, 0);
    }

    public double remainingRatio() {
        if (maximumDurability <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) remainingDurability / maximumDurability));
    }
}
