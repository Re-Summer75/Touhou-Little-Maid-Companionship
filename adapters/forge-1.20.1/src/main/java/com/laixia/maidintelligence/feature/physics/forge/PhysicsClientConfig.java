package com.laixia.maidintelligence.feature.physics.forge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Forge-owned client preferences for the secondary-motion feature.
 */
public final class PhysicsClientConfig {
    public static final String FILE_NAME =
            "tlm_companionship-client.toml";

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;

    private static volatile boolean enabled = true;
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("physics");
        ENABLED = builder
                .comment("Whether maid bone secondary motion is enabled.")
                .define("enabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private PhysicsClientConfig() {
    }

    @SuppressWarnings("removal") // Canonical registration API on Forge 1.20.1.
    public static synchronized void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        modEventBus.addListener(PhysicsClientConfig::onConfigEvent);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT,
                SPEC,
                FILE_NAME
        );
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            enabled = ENABLED.get();
        }
    }
}
