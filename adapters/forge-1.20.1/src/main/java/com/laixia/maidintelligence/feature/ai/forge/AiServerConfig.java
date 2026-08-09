package com.laixia.maidintelligence.feature.ai.forge;

import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatBalance;
import com.laixia.maidintelligence.feature.behavior.domain.combat.CombatPolicies;
import com.laixia.maidintelligence.platform.resource.ModResources;
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
            ModResources.configPath("server.toml");

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue PREFERRED_RANGE;
    private static final ForgeConfigSpec.DoubleValue RETREAT_OVERSHOOT;
    private static final ForgeConfigSpec.DoubleValue SAFE_GAP;
    private static final ForgeConfigSpec.DoubleValue MELEE_SUPPRESSION;
    private static final ForgeConfigSpec.DoubleValue SAFETY_MARGIN;
    private static final ForgeConfigSpec.DoubleValue BAIL_OUT_HEALTH;
    private static final ForgeConfigSpec.DoubleValue SURVIVABLE_BLOW_SHARE;
    private static final ForgeConfigSpec.DoubleValue BLOW_CAUTION;

    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("ai");

        // How a fight is judged, as opposed to how it is fought. The decisions
        // stay in code; what counts as "too close" or "too risky" is a dial,
        // and dials belong to whoever runs the server.
        builder.push("combat_balance");
        PREFERRED_RANGE = builder
                .comment(
                        "Ceiling on the stand-off distance. The weapon states "
                                + "its own reach - a vanilla bow fifteen "
                                + "blocks, a crossbow eight - and she holds "
                                + "the smaller of the two. The default is her "
                                + "sixteen-block perception less the two she "
                                + "is allowed to drift, so she can always see "
                                + "the range she is holding."
                )
                .defineInRange("preferred_range", 14.0D, 2.0D, 32.0D);
        RETREAT_OVERSHOOT = builder
                .comment(
                        "Extra blocks taken beyond the range being held, so a "
                                + "retreat buys seconds rather than one tick."
                )
                .defineInRange("retreat_overshoot", 3.0D, 0.5D, 16.0D);
        SAFE_GAP = builder
                .comment(
                        "Clearance beyond a target's reach while her swing is "
                                + "recovering."
                )
                .defineInRange("safe_gap", 1.0D, 0.25D, 8.0D);
        MELEE_SUPPRESSION = builder
                .comment(
                        "Share of one melee attacker's output that her own "
                                + "knockback is assumed to deny."
                )
                .defineInRange("melee_suppression", 0.8D, 0.0D, 1.0D);
        SAFETY_MARGIN = builder
                .comment(
                        "How much faster than her own death she must finish "
                                + "before committing. Above 1; higher is shyer."
                )
                .defineInRange("safety_margin", 1.2D, 1.0D, 4.0D);
        BAIL_OUT_HEALTH = builder
                .comment(
                        "Health fraction under which she stops accepting even "
                                + "a winning trade."
                )
                .defineInRange("bail_out_health", 0.3D, 0.05D, 1.0D);
        SURVIVABLE_BLOW_SHARE = builder
                .comment(
                        "Share of her health a single blow may take before it "
                                + "raises the margin she demands."
                )
                .defineInRange("survivable_blow_share", 0.25D, 0.05D, 1.0D);
        BLOW_CAUTION = builder
                .comment(
                        "How sharply that margin climbs once one blow is a "
                                + "serious share of her."
                )
                .defineInRange("blow_caution", 2.0D, 0.0D, 8.0D);
        builder.pop();
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









    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            // Installed through one entry point so a balance can never be half
            // applied: a maid holding the old preferred range while judging
            // risk by the new margins is a combination nobody chose.
            CombatPolicies.install(new CombatBalance(
                    PREFERRED_RANGE.get(),
                    RETREAT_OVERSHOOT.get(),
                    SAFE_GAP.get(),
                    MELEE_SUPPRESSION.get(),
                    SAFETY_MARGIN.get(),
                    BAIL_OUT_HEALTH.get(),
                    SURVIVABLE_BLOW_SHARE.get(),
                    BLOW_CAUTION.get()
            ));
        }
    }
}
