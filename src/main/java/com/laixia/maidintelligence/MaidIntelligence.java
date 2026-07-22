package com.laixia.maidintelligence;

import com.laixia.maidintelligence.core.feature.FeatureCatalog;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.gametest.LevelGameTests;
import com.laixia.maidintelligence.gametest.StatusFeedbackGameTests;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(MaidIntelligence.MOD_ID)
public final class MaidIntelligence {
    public static final String MOD_ID = "maid_intelligence";

    public MaidIntelligence() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        FeatureContext context = new FeatureContext(modEventBus, MinecraftForge.EVENT_BUS);

        ModNetwork.initialize();
        modEventBus.addListener(this::registerGameTests);
        FeatureCatalog.all().forEach(feature -> feature.initialize(context));
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(LevelGameTests.class);
        event.register(StatusFeedbackGameTests.class);
    }
}
