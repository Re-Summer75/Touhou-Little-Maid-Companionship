package com.laixia.maidintelligence.feature.behavior.forge;

import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.HungryOwnerRequestPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerReturnPolicy;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Independent controls for original companionship behaviors.
 */
public final class BehaviorServerConfig {
    public static final String FILE_NAME =
            "tlm_companionship-behavior-server.toml";

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue GAZE_RECALL_HOLD_TICKS;
    private static final ForgeConfigSpec.DoubleValue GAZE_RECALL_RANGE;
    private static final ForgeConfigSpec.BooleanValue HUNGRY_REQUEST_ENABLED;
    private static final ForgeConfigSpec.IntValue STANDARD_HUNGER_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue STANDARD_REQUEST_CHANCE;
    private static final ForgeConfigSpec.IntValue HIGH_TRUST_HUNGER_THRESHOLD;
    private static final ForgeConfigSpec.IntValue
            HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL;
    private static final ForgeConfigSpec.DoubleValue
            HIGH_TRUST_REQUEST_CHANCE;
    private static final ForgeConfigSpec.IntValue HUNGRY_REQUEST_INTERVAL;
    private static final ForgeConfigSpec.BooleanValue POST_TASK_RETURN_ENABLED;
    private static final ForgeConfigSpec.IntValue POST_TASK_SETTLE_TICKS;
    private static final ForgeConfigSpec.IntValue POST_TASK_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.IntValue POST_TASK_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.BooleanValue WANDER_RETURN_ENABLED;
    private static final ForgeConfigSpec.DoubleValue WANDER_RETURN_CHANCE;
    private static final ForgeConfigSpec.IntValue WANDER_RETURN_COOLDOWN_TICKS;
    private static final ForgeConfigSpec.IntValue OWNER_RETURN_CLOSE_DISTANCE;

    private static volatile BehaviorTuning tuning = BehaviorTuning.defaults();
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("behavior");
        ENABLED = builder
                .comment(
                        "Whether original companionship AI behaviors are "
                                + "enabled. Vanilla-AI optimization has its "
                                + "own independent configuration."
                )
                .define("enabled", true);
        GAZE_RECALL_HOLD_TICKS = builder
                .comment(
                        "Continuous server ticks that the owner must aim at "
                                + "a maid before asking her to approach."
                )
                .defineInRange(
                        "gaze_recall_hold_ticks",
                        GazeRecallPolicy.DEFAULT_HOLD_TICKS,
                        1,
                        200
                );
        GAZE_RECALL_RANGE = builder
                .comment(
                        "Maximum server-side aiming distance for gaze recall."
                )
                .defineInRange(
                        "gaze_recall_range",
                        8.0D,
                        2.0D,
                        32.0D
                );
        builder.push("hungry_owner_request");
        HUNGRY_REQUEST_ENABLED = builder
                .comment(
                        "Whether a hungry maid may "
                                + "occasionally seek her owner for food."
                )
                .define("enabled", true);
        STANDARD_HUNGER_THRESHOLD = builder
                .comment(
                        "Standard request threshold, aligned with the "
                                + "existing needs-food dialogue."
                )
                .defineInRange(
                        "standard_hunger_threshold",
                        HungryOwnerRequestPolicy
                                .DEFAULT_STANDARD_HUNGER_THRESHOLD,
                        0,
                        100
                );
        STANDARD_REQUEST_CHANCE = builder
                .comment("Chance per check for the standard request branch.")
                .defineInRange(
                        "standard_chance",
                        HungryOwnerRequestPolicy
                                .DEFAULT_STANDARD_REQUEST_CHANCE,
                        0.0D,
                        1.0D
                );
        HIGH_TRUST_HUNGER_THRESHOLD = builder
                .comment(
                        "Hunger threshold for the high-trust request branch."
                )
                .defineInRange(
                        "high_trust_hunger_threshold",
                        HungryOwnerRequestPolicy
                                .DEFAULT_HIGH_TRUST_HUNGER_THRESHOLD,
                        0,
                        100
                );
        HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL = builder
                .comment(
                        "Minimum favorability level for the high-trust branch."
                )
                .defineInRange(
                        "high_trust_minimum_favorability_level",
                        HungryOwnerRequestPolicy
                                .DEFAULT_HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL,
                        0,
                        3
                );
        HIGH_TRUST_REQUEST_CHANCE = builder
                .comment(
                        "Chance per check for the high-trust request branch."
                )
                .defineInRange(
                        "high_trust_chance",
                        HungryOwnerRequestPolicy
                                .DEFAULT_HIGH_TRUST_REQUEST_CHANCE,
                        0.0D,
                        1.0D
                );
        HUNGRY_REQUEST_INTERVAL = builder
                .comment("Ticks between probabilistic food-request checks.")
                .defineInRange(
                        "check_interval_ticks",
                        HungryOwnerRequestPolicy
                                .DEFAULT_CHECK_INTERVAL_TICKS,
                        20,
                        1_200
                );
        builder.pop();
        builder.push("owner_return");
        POST_TASK_RETURN_ENABLED = builder
                .comment(
                        "Whether a maid returns to her owner once after a "
                                + "work-target sequence finishes."
                )
                .define("post_task_enabled", true);
        POST_TASK_SETTLE_TICKS = builder
                .comment(
                        "Target-free ticks used to distinguish the end of a "
                                + "task sequence from a brief target switch."
                )
                .defineInRange(
                        "post_task_settle_ticks",
                        OwnerReturnPolicy.DEFAULT_POST_TASK_SETTLE_TICKS,
                        0,
                        200
                );
        POST_TASK_TIMEOUT_TICKS = builder
                .comment(
                        "Ticks before a blocked post-task return request "
                                + "expires instead of firing late."
                )
                .defineInRange(
                        "post_task_timeout_ticks",
                        OwnerReturnPolicy.DEFAULT_POST_TASK_TIMEOUT_TICKS,
                        20,
                        1_200
                );
        POST_TASK_COOLDOWN_TICKS = builder
                .comment("Cooldown after a successful post-task return.")
                .defineInRange(
                        "post_task_cooldown_ticks",
                        OwnerReturnPolicy.DEFAULT_POST_TASK_COOLDOWN_TICKS,
                        0,
                        12_000
                );
        WANDER_RETURN_ENABLED = builder
                .comment(
                        "Whether a newly selected random stroll may be "
                                + "redirected toward the owner."
                )
                .define("wander_enabled", true);
        WANDER_RETURN_CHANCE = builder
                .comment("Chance per newly selected random stroll.")
                .defineInRange(
                        "wander_chance",
                        OwnerReturnPolicy.DEFAULT_WANDER_RETURN_CHANCE,
                        0.0D,
                        1.0D
                );
        WANDER_RETURN_COOLDOWN_TICKS = builder
                .comment("Cooldown after a random-stroll owner return.")
                .defineInRange(
                        "wander_cooldown_ticks",
                        OwnerReturnPolicy.DEFAULT_WANDER_COOLDOWN_TICKS,
                        0,
                        12_000
                );
        OWNER_RETURN_CLOSE_DISTANCE = builder
                .comment(
                        "Distance at which owner-return behavior is complete."
                )
                .defineInRange(
                        "close_enough_distance",
                        OwnerReturnPolicy.DEFAULT_CLOSE_ENOUGH_DISTANCE,
                        1,
                        8
                );
        builder.pop();
        builder.pop();
        SPEC = builder.build();
    }

    private BehaviorServerConfig() {
    }

    @SuppressWarnings("removal") // Canonical registration API on Forge 1.20.1.
    public static synchronized void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        modEventBus.addListener(BehaviorServerConfig::onConfigEvent);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                SPEC,
                FILE_NAME
        );
    }

    public static boolean isEnabled() {
        return tuning.enabled();
    }

    public static int gazeRecallHoldTicks() {
        return tuning.gazeRecallHoldTicks();
    }

    public static double gazeRecallRange() {
        return tuning.gazeRecallRange();
    }

    public static BehaviorTuning tuning() {
        return tuning;
    }

    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            tuning = new BehaviorTuning(
                    ENABLED.get(),
                    GAZE_RECALL_HOLD_TICKS.get(),
                    GAZE_RECALL_RANGE.get(),
                    new BehaviorTuning.HungryRequest(
                            HUNGRY_REQUEST_ENABLED.get(),
                            HUNGRY_REQUEST_INTERVAL.get(),
                            new BehaviorTuning.RequestBranch(
                                    STANDARD_HUNGER_THRESHOLD.get(),
                                    HungryOwnerRequestPolicy
                                            .DEFAULT_STANDARD_MINIMUM_FAVORABILITY_LEVEL,
                                    STANDARD_REQUEST_CHANCE.get()
                            ),
                            new BehaviorTuning.RequestBranch(
                                    HIGH_TRUST_HUNGER_THRESHOLD.get(),
                                    HIGH_TRUST_MINIMUM_FAVORABILITY_LEVEL.get(),
                                    HIGH_TRUST_REQUEST_CHANCE.get()
                            )
                    ),
                    new BehaviorTuning.OwnerReturn(
                            POST_TASK_RETURN_ENABLED.get(),
                            POST_TASK_SETTLE_TICKS.get(),
                            POST_TASK_TIMEOUT_TICKS.get(),
                            POST_TASK_COOLDOWN_TICKS.get(),
                            WANDER_RETURN_ENABLED.get(),
                            WANDER_RETURN_CHANCE.get(),
                            WANDER_RETURN_COOLDOWN_TICKS.get(),
                            OWNER_RETURN_CLOSE_DISTANCE.get()
                    )
            );
        }
    }
}
