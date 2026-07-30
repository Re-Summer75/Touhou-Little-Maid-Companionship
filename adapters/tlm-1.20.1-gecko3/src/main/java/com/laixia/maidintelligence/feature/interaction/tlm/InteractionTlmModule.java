package com.laixia.maidintelligence.feature.interaction.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.interaction.client.DynamicMaidFaceTracker;
import com.laixia.maidintelligence.feature.interaction.client.GeckoMaidFaceTrackingLayer;
import com.laixia.maidintelligence.feature.interaction.client.MaidFaceTrackingLayer;
import com.laixia.maidintelligence.feature.interaction.client.YsmFaceCaptureBoundaryLayer;
import com.laixia.maidintelligence.feature.interaction.service.MaidFeedingService;
import com.laixia.maidintelligence.mixin.client.LivingEntityRendererAccessor;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;

public final class InteractionTlmModule implements TlmFeatureModule {
    private final MaidFeedingService feeding;

    public InteractionTlmModule(MaidFeedingService feeding) {
        this.feeding = Objects.requireNonNull(feeding, "feeding");
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
        overlay.addTips(
                interactionTip("glass_bottle"),
                Items.GLASS_BOTTLE
        );
        overlay.addSpecialTips(
                interactionTip("feed_saturation_full"),
                (stack, maid, player) -> maid.isOwnedBy(player)
                        && !player.isShiftKeyDown()
                        && feeding.isFeedableFood(maid, stack)
                        && feeding.isSaturationFull(maid)
                        && DynamicMaidFaceTracker
                        .isTargetingFeedPlane(maid)
        );
        overlay.addSpecialTips(
                interactionTip("feed"),
                (stack, maid, player) -> maid.isOwnedBy(player)
                        && !player.isShiftKeyDown()
                        && feeding.isFeedableFood(maid, stack)
                        && !feeding.isSaturationFull(maid)
                        && DynamicMaidFaceTracker
                        .isTargetingFeedPlane(maid)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("unchecked")
    public void registerMaidLayer(
            EntityMaidRenderer renderer,
            EntityRendererProvider.Context context
    ) {
        LivingEntityRendererAccessor<Mob, BedrockModel<Mob>> accessor =
                (LivingEntityRendererAccessor<Mob, BedrockModel<Mob>>)
                        (Object) renderer;
        // Insert before TLM layers that leave transforms on the shared stack.
        accessor.maidIntelligence$getLayers().add(
                0,
                new MaidFaceTrackingLayer(renderer)
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
        renderer.getLayerRenderers().add(
                0,
                new YsmFaceCaptureBoundaryLayer(renderer)
        );
        renderer.addGeoLayerRenderer(
                new GeckoMaidFaceTrackingLayer(renderer)
        );
    }

    private static String interactionTip(String path) {
        return ModResources.translationKey(
                "overlay",
                "interaction." + path
        );
    }
}
