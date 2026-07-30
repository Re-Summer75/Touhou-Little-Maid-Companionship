package com.laixia.maidintelligence.feature.advancement.client;

import com.laixia.maidintelligence.feature.advancement.menu.AdvancementMenus;
import com.laixia.maidintelligence.feature.advancement.menu.MaidAdvancementContainer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * TLM-specific client objects supplied to the Forge lifecycle adapter.
 */
@OnlyIn(Dist.CLIENT)
public final class TlmAdvancementClientRuntime {
    private TlmAdvancementClientRuntime() {
    }

    public static Object createGuiHandler() {
        return new AdvancementGuiHandler();
    }

    @SuppressWarnings("unchecked")
    public static void registerScreen() {
        MenuType<MaidAdvancementContainer> menuType =
                (MenuType<MaidAdvancementContainer>) (MenuType<?>)
                        AdvancementMenus.ADVANCEMENT_PAGE.get();
        MenuScreens.register(
                menuType,
                MaidAdvancementPageScreen::new
        );
    }
}
