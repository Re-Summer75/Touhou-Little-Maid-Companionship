package com.laixia.maidintelligence.feature.status;

import com.google.gson.JsonElement;
import com.laixia.maidintelligence.feature.status.application.MaidStatusApplication;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.port.MaidStatusStore;
import com.laixia.maidintelligence.feature.status.codec.MaidStatusStateCodec;
import com.mojang.serialization.JsonOps;

public final class StatusDomainVerification {
    private StatusDomainVerification() {
    }

    public static void main(String[] args) {
        verifiesCodecRoundTrip();
        verifiesStatusApplication();
        verifiesHungerDrainRates();
        verifiesHungerRestorationAndClamp();
        verifiesHungerRegenerationTiers();
        verifiesPlayerStyleSaturation();
        verifiesFullSaturationBoundary();
        verifiesToolDurabilityThreshold();
        System.out.println("Status domain verification passed.");
    }

    private static void verifiesCodecRoundTrip() {
        MaidStatusState expected = new MaidStatusState(37, 12.5F, 3.0F);
        JsonElement encoded = MaidStatusStateCodec.CODEC.encodeStart(
                JsonOps.INSTANCE,
                expected
        )
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        MaidStatusState decoded = MaidStatusStateCodec.CODEC.parse(
                JsonOps.INSTANCE,
                encoded
        )
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        require(expected.equals(decoded), "Codec round trip changed maid status");

        MaidStatusState legacy = MaidStatusStateCodec.CODEC.parse(
                JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"hunger\":42}")
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        require(
                legacy.equals(new MaidStatusState(42)),
                "Legacy hunger-only status did not default saturation to zero"
        );
    }

    private static void verifiesStatusApplication() {
        InMemoryStore store = new InMemoryStore();
        MaidStatusApplication<String> application = new MaidStatusApplication<>(
                store,
                DefaultHungerPolicy.INSTANCE
        );

        application.setHunger("maid", 25);
        application.restoreFromFood(
                "maid",
                4,
                0.3F
        );
        MaidStatusState restored = application.getState("maid");
        require(
                restored.equals(new MaidStatusState(45, 37.0F, 0.0F)),
                "Status application did not persist pure hunger transitions"
        );
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
        MaidStatusState saturationDrained = policy.drain(
                new MaidStatusState(50, 2.5F, 0.0F),
                3
        );
        require(
                saturationDrained.hunger() == 50 && saturationDrained.saturation() == 0.0F,
                "Saturation did not absorb hunger drain first"
        );
    }

    private static void verifiesHungerRestorationAndClamp() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        MaidStatusState restored = policy.restoreFromFood(
                new MaidStatusState(25),
                4,
                0.3F
        );
        require(restored.hunger() == 45, "Unexpected food restoration");
        require(restored.saturation() == 12.0F, "Unexpected saturation restoration");
        require(
                policy.restoreFromFood(
                        new MaidStatusState(95, 90.0F, 0.0F),
                        20,
                        0.8F
                ).equals(new MaidStatusState(100, 100.0F, 0.0F)),
                "Food restoration did not clamp hunger and saturation"
        );
        require(
                policy.shouldAutoEat(new MaidStatusState(DefaultHungerPolicy.AUTO_EAT_THRESHOLD)),
                "Auto-eat threshold should be inclusive"
        );
    }

    private static void verifiesHungerRegenerationTiers() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        require(
                policy.regenerationIntervalTicks(
                        new MaidStatusState(100, 25.0F, 0.0F)
                ) == 10,
                "High hunger regeneration changed when saturation was present"
        );
        require(
                policy.saturatedRegenerationIntervalTicks(
                        new MaidStatusState(100, 25.0F, 0.0F)
                ) == 10,
                "Saturation enhancement interval is incorrect"
        );
        require(
                policy.regenerationIntervalTicks(new MaidStatusState(79)) == 80,
                "Medium hunger regeneration interval is incorrect"
        );
        require(
                policy.regenerationIntervalTicks(new MaidStatusState(59)) == 160,
                "Low hunger regeneration interval is incorrect"
        );
        require(
                policy.regenerationIntervalTicks(new MaidStatusState(40)) == 0,
                "Regeneration should stop at the auto-eat threshold"
        );
        require(
                policy.consumeForRegeneration(new MaidStatusState(80)).hunger() == 79,
                "Regeneration should consume one hunger point"
        );
    }

    private static void verifiesPlayerStyleSaturation() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        MaidStatusState saturated = new MaidStatusState(100, 25.0F, 0.0F);
        require(
                Math.abs(policy.saturatedRegenerationHealth(saturated) - 5.0F / 6.0F)
                        < 1.0E-6F,
                "Saturated healing amount did not match player scaling"
        );

        MaidStatusState exhausted = policy.consumeForSaturatedRegeneration(saturated);
        require(exhausted.exhaustion() == 5.0F, "Fast healing exhaustion is incorrect");

        MaidStatusState settled = policy.settleExhaustion(exhausted);
        require(
                settled.equals(new MaidStatusState(100, 20.0F, 1.0F)),
                "Exhaustion did not consume scaled saturation"
        );
    }

    private static void verifiesFullSaturationBoundary() {
        DefaultHungerPolicy policy = DefaultHungerPolicy.INSTANCE;
        require(
                policy.isSaturationFull(new MaidStatusState(100, 100.0F, 0.0F)),
                "Maximum saturation should reject further feeding"
        );
        require(
                !policy.isSaturationFull(new MaidStatusState(100, 99.0F, 0.0F)),
                "Feeding should resume after saturation is consumed"
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

    private static final class InMemoryStore implements MaidStatusStore<String> {
        private MaidStatusState state = MaidStatusState.initial();

        @Override
        public MaidStatusState get(String subject) {
            return state;
        }

        @Override
        public void set(String subject, MaidStatusState state) {
            this.state = state;
        }
    }
}
