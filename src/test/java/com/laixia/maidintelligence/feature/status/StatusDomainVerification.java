package com.laixia.maidintelligence.feature.status;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.mojang.serialization.JsonOps;

public final class StatusDomainVerification {
    private StatusDomainVerification() {
    }

    public static void main(String[] args) {
        verifiesCodecRoundTrip();
        verifiesHungerDrainRates();
        verifiesHungerRestorationAndClamp();
        verifiesToolDurabilityThreshold();
        System.out.println("Status domain verification passed.");
    }

    private static void verifiesCodecRoundTrip() {
        MaidStatusState expected = new MaidStatusState(37);
        JsonElement encoded = MaidStatusState.CODEC.encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        MaidStatusState decoded = MaidStatusState.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        require(expected.equals(decoded), "Codec round trip changed maid status");
    }

    private static void verifiesHungerDrainRates() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        int workUnits = DefaultHungerPolicy.WORK_UNITS_PER_TICK * 200;
        int idleUnits = DefaultHungerPolicy.IDLE_UNITS_PER_TICK * 400;
        require(
                workUnits == DefaultHungerPolicy.HUNGER_UNITS_PER_POINT,
                "Work hunger rate is not one point per 200 ticks"
        );
        require(
                idleUnits == DefaultHungerPolicy.HUNGER_UNITS_PER_POINT,
                "Idle hunger rate is not one point per 400 ticks"
        );
        require(
                policy.activityUnitsPerTick(false, true) == 0,
                "Resting should not consume hunger"
        );
        require(
                policy.drain(new MaidStatusState(1), 3).hunger() == 0,
                "Hunger drain did not clamp to zero"
        );
    }

    private static void verifiesHungerRestorationAndClamp() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        MaidStatusState restored = policy.restoreFromNutrition(new MaidStatusState(25), 4);
        require(restored.hunger() == 45, "Unexpected food restoration");
        require(
                policy.restoreFromNutrition(new MaidStatusState(95), 20).hunger()
                        == DefaultHungerPolicy.MAX_HUNGER,
                "Food restoration did not clamp to maximum hunger"
        );
        require(
                policy.shouldAutoEat(new MaidStatusState(DefaultHungerPolicy.AUTO_EAT_THRESHOLD)),
                "Auto-eat threshold should be inclusive"
        );
    }

    private static void verifiesToolDurabilityThreshold() {
        DefaultToolDurabilityPolicy policy = DefaultToolDurabilityPolicy.INSTANCE;
        require(policy.warningThreshold(250) == 25, "Expected a ten-percent warning threshold");
        require(policy.warningThreshold(59) == 10, "Expected the minimum warning threshold");
        require(policy.isLowDurability(250, 225), "Remaining ten percent should be low durability");
        require(!policy.isLowDurability(250, 224), "Durability above ten percent should be healthy");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
