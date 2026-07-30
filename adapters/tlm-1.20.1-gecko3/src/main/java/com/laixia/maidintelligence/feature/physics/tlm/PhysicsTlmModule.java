package com.laixia.maidintelligence.feature.physics.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.physics.client.MaidSkeletonDebugLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class PhysicsTlmModule implements TlmFeatureModule {
    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
        renderer.addGeoLayerRenderer(
                new MaidSkeletonDebugLayer(renderer)
        );
    }
}
