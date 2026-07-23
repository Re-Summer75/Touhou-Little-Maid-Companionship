package com.laixia.maidintelligence.feature.physics;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.physics.client.ClientPhysicsSetup;
import com.laixia.maidintelligence.feature.physics.client.MaidSkeletonDebugLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;

/**
 * Registers the client-side weighted bone-physics layer. The simulation itself
 * is driven by a mixin on the Gecko maid renderer; this module wires up the
 * resource-reload cleanup, the {@code /maidphysicsdebug} stick command, and the
 * skeleton debug overlay layer.
 */
public final class MaidPhysicsFeature implements FeatureModule, TlmFeatureModule {
    public static final MaidPhysicsFeature INSTANCE = new MaidPhysicsFeature();

    private boolean initialized;

    private MaidPhysicsFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        context.gameEventBus().addListener(PhysicsDebugCommands::onRegisterCommands);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientPhysicsSetup.initialize(
                        context.modEventBus(),
                        context.gameEventBus()
                )
        );
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
        renderer.addGeoLayerRenderer(new MaidSkeletonDebugLayer(renderer));
    }
}
