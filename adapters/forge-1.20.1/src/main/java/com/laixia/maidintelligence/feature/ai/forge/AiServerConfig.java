package com.laixia.maidintelligence.feature.ai.forge;

import com.laixia.maidintelligence.feature.ai.api.MaidAiTuning;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationMode;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Server-owned controls for maid AI performance optimizations.
 */
public final class AiServerConfig {
    public static final String FILE_NAME =
            "tlm_companionship-server.toml";

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue REACHABLE_PATH_CACHE_TICKS;
    private static final ForgeConfigSpec.BooleanValue PROFILING_ENABLED;
    private static final ForgeConfigSpec.BooleanValue
            DYNAMIC_ACTIVITY_RADIUS_ENABLED;
    private static final ForgeConfigSpec.IntValue STATIONARY_CONFIRM_TICKS;
    private static final ForgeConfigSpec.IntValue MOVING_CONFIRM_TICKS;
    private static final ForgeConfigSpec.IntValue IDLE_RADIUS_BONUS;
    private static final ForgeConfigSpec.IntValue WORK_RADIUS_BONUS;
    private static final ForgeConfigSpec.IntValue COMBAT_RADIUS_BONUS;
    private static final ForgeConfigSpec.IntValue MAXIMUM_ACTIVITY_RADIUS;
    private static final ForgeConfigSpec.BooleanValue COMBAT_REACTION_ENABLED;
    private static final ForgeConfigSpec.IntValue
            STATIONARY_COMBAT_SCAN_INTERVAL;
    private static final ForgeConfigSpec.IntValue MOVING_COMBAT_SCAN_INTERVAL;
    private static final ForgeConfigSpec.IntValue COMBAT_CANDIDATE_LIMIT;
    private static final ForgeConfigSpec.IntValue RECENT_THREAT_TICKS;
    private static final ForgeConfigSpec.EnumValue<MovementCoordinationMode>
            MOVEMENT_COORDINATION_MODE;
    private static final ForgeConfigSpec.IntValue MOVEMENT_LEASE_TICKS;
    private static final ForgeConfigSpec.IntValue PICKUP_COMMITMENT_TICKS;
    private static final ForgeConfigSpec.IntValue MOVEMENT_FAIL_OPEN_TICKS;

    private static volatile MaidAiTuning tuning = MaidAiTuning.defaults();
    private static volatile MovementCoordinationMode movementCoordinationMode =
            MovementCoordinationMode.CONSERVATIVE;
    private static volatile int movementLeaseTicks = 12;
    private static volatile int pickupCommitmentTicks = 40;
    private static volatile int movementFailOpenTicks = 40;
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("ai");
        ENABLED = builder
                .comment("Whether behavior-preserving maid AI optimizations are enabled.")
                .define("enabled", true);
        REACHABLE_PATH_CACHE_TICKS = builder
                .comment(
                        "Ticks to reuse successful reachability checks. "
                                + "Failures are never cached."
                )
                .defineInRange("reachable_path_cache_ticks", 20, 0, 100);
        PROFILING_ENABLED = builder
                .comment(
                        "Collect per-maid AI-step timing. Disabled by default "
                                + "to avoid diagnostic overhead."
                )
                .define("profiling", false);
        builder.push("activity_radius");
        DYNAMIC_ACTIVITY_RADIUS_ENABLED = builder
                .comment(
                        "Expand the transient activity radius while the owner "
                                + "remains stationary. The persisted TLM "
                                + "radius is never modified."
                )
                .define("enabled", true);
        STATIONARY_CONFIRM_TICKS = builder
                .comment("Stationary ticks required before radius expansion.")
                .defineInRange("stationary_confirm_ticks", 20, 1, 200);
        MOVING_CONFIRM_TICKS = builder
                .comment("Moving ticks required to restore the base radius.")
                .defineInRange("moving_confirm_ticks", 3, 1, 40);
        IDLE_RADIUS_BONUS = builder
                .comment("Extra blocks for built-in idle activity.")
                .defineInRange("idle_bonus", 4, 0, 32);
        WORK_RADIUS_BONUS = builder
                .comment("Extra blocks for built-in work activity.")
                .defineInRange("work_bonus", 8, 0, 32);
        COMBAT_RADIUS_BONUS = builder
                .comment("Extra blocks for built-in combat activity.")
                .defineInRange("combat_bonus", 12, 0, 32);
        MAXIMUM_ACTIVITY_RADIUS = builder
                .comment("Hard cap for every transient expanded radius.")
                .defineInRange("maximum_radius", 24, 3, 64);
        builder.pop();
        builder.push("combat_reaction");
        COMBAT_REACTION_ENABLED = builder
                .comment(
                        "Seed TLM's existing combat Brain memory from recent "
                                + "threats and bounded proactive scans."
                )
                .define("enabled", true);
        STATIONARY_COMBAT_SCAN_INTERVAL = builder
                .comment(
                        "Ticks between proactive scans while the owner is "
                                + "stationary."
                )
                .defineInRange("stationary_scan_interval", 10, 2, 40);
        MOVING_COMBAT_SCAN_INTERVAL = builder
                .comment(
                        "Ticks between proactive scans while the owner moves."
                )
                .defineInRange("moving_scan_interval", 20, 2, 80);
        COMBAT_CANDIDATE_LIMIT = builder
                .comment("Maximum entities evaluated by one proactive scan.")
                .defineInRange("candidate_limit", 64, 8, 256);
        RECENT_THREAT_TICKS = builder
                .comment(
                        "Maximum age of owner/maid combat memories used for "
                                + "immediate reaction."
                )
                .defineInRange("recent_threat_ticks", 200, 20, 600);
        builder.pop();
        MOVEMENT_COORDINATION_MODE = builder
                .comment(
                        "Movement intent coordination: OFF restores TLM "
                                + "behavior, OBSERVE only records conflicts, "
                                + "and CONSERVATIVE stabilizes known built-in "
                                + "movement writers."
                )
                .defineEnum(
                        "movement_coordination_mode",
                        MovementCoordinationMode.CONSERVATIVE
                );
        MOVEMENT_LEASE_TICKS = builder
                .comment(
                        "Maximum ticks that a known movement target is "
                                + "protected from lower-priority built-in "
                                + "writers."
                )
                .defineInRange("movement_lease_ticks", 12, 1, 100);
        PICKUP_COMMITMENT_TICKS = builder
                .comment(
                        "Ticks that an active item pickup may defer normal "
                                + "owner following. Emergency teleport and "
                                + "invalid item targets still release it."
                )
                .defineInRange("pickup_commitment_ticks", 40, 1, 100);
        MOVEMENT_FAIL_OPEN_TICKS = builder
                .comment(
                        "Ticks to suspend coordination after an unknown "
                                + "behavior replaces a managed target."
                )
                .defineInRange("movement_fail_open_ticks", 40, 1, 200);
        builder.pop();
        SPEC = builder.build();
    }

    private AiServerConfig() {
    }

    @SuppressWarnings("removal") // Canonical registration API on Forge 1.20.1.
    public static synchronized void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        modEventBus.addListener(AiServerConfig::onConfigEvent);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                SPEC,
                FILE_NAME
        );
    }

    public static boolean isEnabled() {
        return tuning.performance().enabled();
    }

    public static int reachablePathCacheTicks() {
        return tuning.performance().reachablePathCacheTicks();
    }

    public static boolean isProfilingEnabled() {
        return tuning.performance().profilingEnabled();
    }

    public static MaidAiTuning tuning() {
        return tuning;
    }

    public static MovementCoordinationMode movementCoordinationMode() {
        return movementCoordinationMode;
    }

    public static int movementLeaseTicks() {
        return movementLeaseTicks;
    }

    public static int pickupCommitmentTicks() {
        return pickupCommitmentTicks;
    }

    public static int movementFailOpenTicks() {
        return movementFailOpenTicks;
    }

    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            tuning = new MaidAiTuning(
                    new MaidAiTuning.Performance(
                            ENABLED.get(),
                            REACHABLE_PATH_CACHE_TICKS.get(),
                            PROFILING_ENABLED.get()
                    ),
                    new MaidAiTuning.ActivityRadius(
                            DYNAMIC_ACTIVITY_RADIUS_ENABLED.get(),
                            STATIONARY_CONFIRM_TICKS.get(),
                            MOVING_CONFIRM_TICKS.get(),
                            IDLE_RADIUS_BONUS.get(),
                            WORK_RADIUS_BONUS.get(),
                            COMBAT_RADIUS_BONUS.get(),
                            MAXIMUM_ACTIVITY_RADIUS.get()
                    ),
                    new MaidAiTuning.CombatReaction(
                            COMBAT_REACTION_ENABLED.get(),
                            STATIONARY_COMBAT_SCAN_INTERVAL.get(),
                            MOVING_COMBAT_SCAN_INTERVAL.get(),
                            COMBAT_CANDIDATE_LIMIT.get(),
                            RECENT_THREAT_TICKS.get()
                    )
            );
            movementCoordinationMode = MOVEMENT_COORDINATION_MODE.get();
            movementLeaseTicks = MOVEMENT_LEASE_TICKS.get();
            pickupCommitmentTicks = PICKUP_COMMITMENT_TICKS.get();
            movementFailOpenTicks = MOVEMENT_FAIL_OPEN_TICKS.get();
        }
    }
}
