package com.laixia.maidintelligence.feature.interaction;

import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.interaction.client.ClientInteractionSetup;
import com.laixia.maidintelligence.feature.interaction.client.DynamicMaidFaceTracker;
import com.laixia.maidintelligence.feature.interaction.client.GeckoMaidFaceTrackingLayer;
import com.laixia.maidintelligence.feature.interaction.client.MaidFaceTrackingLayer;
import com.laixia.maidintelligence.feature.interaction.event.MaidAutomaticEatingParticleHandler;
import com.laixia.maidintelligence.feature.interaction.event.MaidDirectItemInteractionHandler;
import com.laixia.maidintelligence.feature.interaction.event.MaidInteractionHandler;
import com.laixia.maidintelligence.feature.interaction.service.MaidFeedingService;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

public final class MaidInteractionFeature implements FeatureModule, TlmFeatureModule {
    public static final MaidInteractionFeature INSTANCE = new MaidInteractionFeature();

    private boolean initialized;

    private MaidInteractionFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        context.gameEventBus().register(new MaidInteractionHandler());
        context.gameEventBus().register(new MaidDirectItemInteractionHandler());
        context.gameEventBus().register(new MaidAutomaticEatingParticleHandler());
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientInteractionSetup.initialize(
                        context.modEventBus(),
                        context.gameEventBus()
                )
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void registerMaidTips(MaidTipsOverlay overlay) {
        overlay.addTips(interactionTip("empty_hand"), Items.AIR);
        overlay.addTips(
                interactionTip("feed_aim"),
                Items.GOLDEN_APPLE,
                Items.ENCHANTED_GOLDEN_APPLE,
                Items.CAKE
        );
        overlay.addTips(interactionTip("potion"), Items.POTION);
        overlay.addTips(interactionTip("milk"), Items.MILK_BUCKET);
        overlay.addTips(interactionTip("glass_bottle"), Items.GLASS_BOTTLE);
        overlay.addSpecialTips(
                interactionTip("feed_saturation_full"),
                (stack, maid, player) -> maid.isOwnedBy(player)
                        && !player.isShiftKeyDown()
                        && MaidFeedingService.isFeedableFood(maid, stack)
                        && MaidFeedingService.isSaturationFull(maid)
                        && DynamicMaidFaceTracker.isTargetingFeedPlane(maid)
        );
        overlay.addSpecialTips(
                interactionTip("feed"),
                (stack, maid, player) -> maid.isOwnedBy(player)
                        && !player.isShiftKeyDown()
                        && MaidFeedingService.isFeedableFood(maid, stack)
                        && !MaidFeedingService.isSaturationFull(maid)
                        && DynamicMaidFaceTracker.isTargetingFeedPlane(maid)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void registerMaidLayer(
            EntityMaidRenderer renderer,
            EntityRendererProvider.Context context
    ) {
        renderer.addLayer(new MaidFaceTrackingLayer(renderer));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
        renderer.addGeoLayerRenderer(new GeckoMaidFaceTrackingLayer(renderer));
    }

    private static String interactionTip(String path) {
        return ModResources.translationKey("overlay", "interaction." + path);
    }
}
