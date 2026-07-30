package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@OnlyIn(Dist.CLIENT)
public final class ClientPhysicsSetup {
    private static final AtomicBoolean CUSTOM_PACK_REFRESH_QUEUED =
            new AtomicBoolean();

    private ClientPhysicsSetup() {
    }

    public static void initialize(IEventBus modEventBus, IEventBus gameEventBus) {
        modEventBus.addListener(ClientPhysicsSetup::registerReloadListener);
        gameEventBus.addListener(ClientPhysicsSetup::onEntityInteract);
        gameEventBus.addListener(ClientPhysicsSetup::onEntityLeaveLevel);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            PhysicsMetadataLoader.reload(resourceManager);
            PhysicsBonePlanCache.clear();
            MaidBonePhysics.clear();
        });
    }

    public static void refreshAfterCustomPackLoad() {
        if (!CUSTOM_PACK_REFRESH_QUEUED.compareAndSet(false, true)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.delayedExecutor(
                1L,
                TimeUnit.MILLISECONDS,
                minecraft
        ).execute(() -> {
            try {
                PhysicsMetadataLoader.reload(minecraft.getResourceManager());
                PhysicsBonePlanCache.clear();
                MaidBonePhysics.clear();
            } finally {
                CUSTOM_PACK_REFRESH_QUEUED.set(false);
            }
        });
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity livingEntity) {
            MaidBonePhysics.forget(livingEntity);
            if (livingEntity instanceof EntityMaid maid) {
                PhysicsDisplacementLog.forget(maid);
            }
        }
    }

    /**
     * Right-clicking a maid with the debug stick dumps its live skeleton to the
     * chat and log instead of running any normal interaction. Shift binds the
     * displacement log to that maid instead, which is cancelled here before
     * TouhouLittleMaid's own shift handler can open her inventory.
     */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()
                || !PhysicsDebugStick.isDebugStick(event.getItemStack())
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            PhysicsDisplacementLog.toggle(event.getEntity(), maid);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
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
