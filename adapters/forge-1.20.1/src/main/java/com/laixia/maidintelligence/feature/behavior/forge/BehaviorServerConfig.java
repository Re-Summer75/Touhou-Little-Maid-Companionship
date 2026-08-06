package com.laixia.maidintelligence.feature.behavior.forge;

import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;
import com.laixia.maidintelligence.feature.orchestration.api.IntentRolloutMode;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Runtime safety controls; behavior meaning is data-pack authoritative.
 */
public final class BehaviorServerConfig {
    public static final String FILE_NAME =
            ModResources.configPath("behavior-server.toml");

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.EnumValue<IntentRolloutMode>
            ROLLOUT_MODE;
    private static final ForgeConfigSpec.EnumValue<LearningMode>
            LEARNING_MODE;
    private static final ForgeConfigSpec.IntValue EVALUATION_INTERVAL_TICKS;
    private static final ForgeConfigSpec.IntValue MAX_CANDIDATE_EVALUATIONS;
    private static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_ENABLED;
    private static final ForgeConfigSpec.IntValue GAZE_SENSOR_TIMING_REVISION;
    private static final ForgeConfigSpec.IntValue GAZE_RECALL_HOLD_TICKS;
    private static final ForgeConfigSpec.DoubleValue GAZE_RECALL_RANGE;

    private static volatile BehaviorTuning tuning = BehaviorTuning.defaults();
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("intent_ai");
        ENABLED = builder
                .comment("Whether data-driven companionship intents run.")
                .define("enabled", true);
        ROLLOUT_MODE = builder
                .comment(
                        "LIVE_ONLY runs authoritative behavior only; "
                                + "SHADOW_COMPARE evaluates a dry-run copy "
                                + "before live behavior for diagnostics."
                )
                .defineEnum(
                        "rollout_mode",
                        IntentRolloutMode.LIVE_ONLY
                );
        LEARNING_MODE = builder
                .comment(
                        "SHADOW records bounded projections without changing "
                                + "Utility; ACTIVE applies capped soft modifiers."
                )
                .defineEnum("learning_mode", LearningMode.SHADOW);
        EVALUATION_INTERVAL_TICKS = builder
                .comment(
                        "Ticks between routine Utility evaluations. "
                                + "Signals still request an immediate pass."
                )
                .defineInRange(
                        "evaluation_interval_ticks",
                        5,
                        1,
                        100
                );
        MAX_CANDIDATE_EVALUATIONS = builder
                .comment(
                        "Maximum intent candidates evaluated in one pass."
                )
                .defineInRange(
                        "max_candidate_evaluations",
                        64,
                        5,
                        128
                );
        DIAGNOSTICS_ENABLED = builder
                .comment("Whether per-maid explain snapshots are retained.")
                .define("diagnostics_enabled", true);
        builder.push("gaze_sensor");
        GAZE_SENSOR_TIMING_REVISION = builder
                .comment("Internal revision for gaze timing default migration.")
                .defineInRange(
                        "timing_revision",
                        1,
                        1,
                        GazeRecallPolicy.CURRENT_TIMING_REVISION
                );
        GAZE_RECALL_HOLD_TICKS = builder
                .comment(
                        "Continuous server ticks aimed at a maid before "
                                + "submitting a gaze-recall signal."
                )
                .defineInRange(
                        "hold_ticks",
                        GazeRecallPolicy.DEFAULT_HOLD_TICKS,
                        1,
                        200
                );
        GAZE_RECALL_RANGE = builder
                .comment("Maximum server-side gaze sensor distance.")
                .defineInRange(
                        "range",
                        GazeRecallPolicy.DEFAULT_RANGE,
                        2.0D,
                        32.0D
                );
        builder.pop(2);
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
            int revision = GAZE_SENSOR_TIMING_REVISION.get();
            int holdTicks = GazeRecallPolicy.migrateHoldTicks(
                    GAZE_RECALL_HOLD_TICKS.get(),
                    revision
            );
            double range = GazeRecallPolicy.migrateRange(
                    GAZE_RECALL_RANGE.get(),
                    revision
            );
            if (revision < GazeRecallPolicy.CURRENT_TIMING_REVISION) {
                GAZE_RECALL_HOLD_TICKS.set(holdTicks);
                GAZE_RECALL_RANGE.set(range);
                GAZE_SENSOR_TIMING_REVISION.set(
                        GazeRecallPolicy.CURRENT_TIMING_REVISION
                );
                event.getConfig().save();
            }
            tuning = new BehaviorTuning(
                    ENABLED.get(),
                    ROLLOUT_MODE.get(),
                    LEARNING_MODE.get(),
                    EVALUATION_INTERVAL_TICKS.get(),
                    MAX_CANDIDATE_EVALUATIONS.get(),
                    DIAGNOSTICS_ENABLED.get(),
                    holdTicks,
                    range
            );
        }
    }
}
