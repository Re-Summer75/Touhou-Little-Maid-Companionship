package com.laixia.maidintelligence.feature.advancement.client;

import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.feature.advancement.menu.AdvancementMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public final class AdvancementClientSetup {
    private AdvancementClientSetup() {
    }

    public static void register(FeatureContext context) {
        context.gameEventBus().register(new ClientMaidAdvancements());
        context.gameEventBus().register(new AdvancementGuiHandler());
        context.modEventBus().addListener(AdvancementClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                AdvancementMenus.ADVANCEMENT_PAGE.get(),
                MaidAdvancementPageScreen::new
        ));
    }
}
