package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@OnlyIn(Dist.CLIENT)
public final class ClientPhysicsSetup {
    private ClientPhysicsSetup() {
    }

    public static void initialize(IEventBus modEventBus, IEventBus gameEventBus) {
        modEventBus.addListener(ClientPhysicsSetup::registerReloadListener);
        gameEventBus.addListener(ClientPhysicsSetup::onEntityInteract);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener) resourceManager -> MaidBonePhysics.clear()
        );
    }

    /**
     * Right-clicking a maid with the debug stick dumps its live skeleton to the
     * chat and log instead of running any normal interaction.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()
                || !PhysicsDebugStick.isDebugStick(event.getItemStack())
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        AnimatedGeoModel model = MaidBonePhysics.lastModel(maid);
        if (model == null) {
            event.getEntity().sendSystemMessage(Component
                    .literal("骨架未就绪：靠近女仆让其渲染一帧后再右键")
                    .withStyle(ChatFormatting.RED));
        } else {
            PhysicsDebugSkeletonDump.dump(event.getEntity(), maid, model);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
