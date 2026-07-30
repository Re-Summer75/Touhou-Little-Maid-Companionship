package com.laixia.maidintelligence.feature.advancement.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端缓存的女仆进度：**条目表与完成情况都在这里**，和玩家自己那份完全分开。
 * <p>
 * 不能借玩家那份（{@code minecraft.player.connection.getAdvancements()}）：那份是服务端按
 * 玩家可见性下发的，女仆专属的进度玩家永远不会完成，所以里面压根没有；女仆在别的树里做到的地方
 * 也常常还没对玩家开放。所以服务端按女仆的可见性算好、连定义一起送，这里单独存一份。
 * <p>
 * {@link #version()} 每次收到推送都会自增，界面靠它决定要不要重建。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMaidAdvancements {
    private static final AdvancementList ADVANCEMENTS = new AdvancementList();
    private static final Map<ResourceLocation, AdvancementProgress> PROGRESS = new HashMap<>();

    private static int maidEntityId = -1;
    private static int version;

    public static void accept(int entityId, ClientboundUpdateAdvancementsPacket update) {
        if (update.shouldReset() || maidEntityId != entityId) {
            ADVANCEMENTS.clear();
            PROGRESS.clear();
            maidEntityId = entityId;
        }
        ADVANCEMENTS.remove(update.getRemoved());
        ADVANCEMENTS.add(update.getAdded());
        update.getProgress().forEach((id, progress) -> {
            Advancement advancement = ADVANCEMENTS.get(id);
            if (advancement != null) {
                // 线上格式只带每条 criterion 的状态，完成条件本身不传，
                // 不从条目定义补回去的话 isDone 与百分比恒为「未完成」。原版收玩家进度也是在这一步补的。
                progress.update(advancement.getCriteria(), advancement.getRequirements());
                PROGRESS.put(id, progress);
            }
        });
        version++;
    }

    /** 这只女仆当前可见的根进度，界面按它建树。 */
    public static Iterable<Advancement> rootsFor(int entityId) {
        return maidEntityId == entityId ? ADVANCEMENTS.getRoots() : List.of();
    }

    public static Map<ResourceLocation, AdvancementProgress> progressFor(int entityId) {
        return maidEntityId == entityId ? PROGRESS : Map.of();
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        ADVANCEMENTS.clear();
        PROGRESS.clear();
        maidEntityId = -1;
        version++;
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
