package com.laixia.maidintelligence.feature.advancement.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.menu.MaidAdvancementContainer;
import com.laixia.maidintelligence.feature.advancement.network.AdvancementNetwork;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementManager;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import com.laixia.maidintelligence.feature.advancement.server.MaidBridgeMemory;
import com.laixia.maidintelligence.feature.advancement.server.MaidMirrorPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆进度状态的生命周期：谁该落盘、谁该释放监听、什么时候要跟着数据包重载。
 */
public final class MaidAdvancementLifecycleHandlers {
    private final MaidAdvancementManager manager;
    private final MaidBridgeMemory bridgeMemory;

    public MaidAdvancementLifecycleHandlers(MaidAdvancementManager manager, MaidBridgeMemory bridgeMemory) {
        this.manager = manager;
        this.bridgeMemory = bridgeMemory;
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        manager.release(maid.getUUID());
        bridgeMemory.forget(maid.getUUID());
    }

    @SubscribeEvent
    public void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        manager.markDirty(event.getEntity());
        if (event.getEntity() instanceof MaidMirrorPlayer mirror && mirror.boundMaid() != null) {
            pushToViewers(mirror.boundMaid());
        }
    }

    /**
     * 页面开着的时候重发一份快照。不能只补动过的那一条：完成一条会连带让它周围两级
     * 原本不可见的条目露出来，那些条目的定义客户端还没有。
     */
    private void pushToViewers(EntityMaid maid) {
        MinecraftServer server = maid.level().getServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof MaidAdvancementContainer container
                    && container.getMaid() == maid) {
                viewers.add(player);
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        manager.tracker(maid).ifPresent(tracker -> {
            MaidAdvancementSnapshot snapshot = tracker.snapshot(server.getAdvancements());
            viewers.forEach(player -> AdvancementNetwork.sendSnapshot(
                    player,
                    maid.getId(),
                    snapshot
            ));
        });
    }

    @SubscribeEvent
    public void onLevelSave(LevelEvent.Save event) {
        manager.saveAll();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            manager.forgetLevel(level.dimension());
        }
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        // 只处理 /reload 这类全局同步；单个玩家登录时进度树没变。
        if (event.getPlayer() != null) {
            return;
        }
        MinecraftServer server = event.getPlayerList().getServer();
        manager.reloadAll(server);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        manager.saveAll();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        manager.shutdown();
        bridgeMemory.clear();
    }
}
