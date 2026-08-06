package com.laixia.maidintelligence.feature.interaction.forge;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 女仆右键交互方案的开关。
 *
 * <p>做成配置而不是游戏内按键绑定，是因为这套手势带鼠标键：原版按键设置只接受
 * 键盘组合键，而在 Forge 里声明 {@code SHIFT + 鼠标右键} 会让原版 {@code keyUse}
 * 收不到点击，连潜行放置方块一起失效。
 *
 * <p>用 COMMON 而非 SERVER，是为了让配置落在 {@code config/} 而不是存档目录，
 * 单人玩家和服主都能直接找到；交互判定发生在服务端，多人游戏以服务端的值为准。
 */
public final class InteractionConfig {
    public static final String FILE_NAME =
            ModResources.configPath("interaction.toml");

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue CUSTOM_KEYS;

    private static volatile boolean customKeys = true;
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("interaction");
        CUSTOM_KEYS = builder
                .comment(
                        "Whether this mod's own maid interaction keys are used.",
                        "true: Shift + right-click opens the maid screen;"
                                + " empty-hand right-click toggles sitting,"
                                + " dismounts or ejects passengers;"
                                + " right-click holding an item runs only the"
                                + " item interaction.",
                        "false: right-click interaction is left entirely to"
                                + " Touhou Little Maid.",
                        "Mouth feeding, potions and milk are separate features"
                                + " and are NOT affected by this switch."
                )
                .define("customKeys", true);
        builder.pop();
        SPEC = builder.build();
    }

    private InteractionConfig() {
    }

    @SuppressWarnings("removal") // Canonical registration API on Forge 1.20.1.
    public static synchronized void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;
        modEventBus.addListener(InteractionConfig::onConfigEvent);
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                SPEC,
                FILE_NAME
        );
    }

    public static boolean usesCustomKeys() {
        return customKeys;
    }

    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            customKeys = CUSTOM_KEYS.get();
        }
    }
}
