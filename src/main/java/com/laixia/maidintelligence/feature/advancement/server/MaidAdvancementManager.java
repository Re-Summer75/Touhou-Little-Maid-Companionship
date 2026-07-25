package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * 女仆进度状态的持有者：每只女仆一份 {@link MaidAdvancementTracker}，每个维度一个
 * {@link MaidMirrorPlayer}，都是懒创建、卸载即释放。所有方法只在服务端主线程调用。
 * <p>
 * 进度落盘在 {@code <存档>/tlm_companionship/maid_advancements/<女仆UUID>.json}，
 * 直接走原版 {@code PlayerAdvancements} 的读写。之所以不存进 TLM TaskData：原版读档时会跳过
 * 已完成条目的监听注册，若改为手工恢复，已完成的 criteria 会重新触发一次奖励与聊天广播。
 */
public final class MaidAdvancementManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SAVE_DIRECTORY = "tlm_companionship/maid_advancements";
    /** 奖励发放可能再引出触发（例如经验升级），留几层余量后直接丢弃，避免无限递归。 */
    private static final int MAX_DEPTH = 4;

    private final Map<ResourceKey<Level>, MaidMirrorPlayer> mirrors = new HashMap<>();
    private final Map<UUID, MaidAdvancementTracker> trackers = new HashMap<>();
    private int depth;

    /**
     * 把一次原版触发器调用喂给指定女仆：绑定镜像玩家、确保进度存在、执行、恢复上一次绑定。
     */
    public void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        if (depth >= MAX_DEPTH) {
            return;
        }
        Session session = open(maid);
        if (session == null) {
            return;
        }
        depth++;
        try {
            action.accept(session.mirror);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to dispatch advancement trigger for maid {}", maid.getUUID(), exception);
        } finally {
            depth--;
            session.close();
        }
    }

    /**
     * 取（必要时创建）女仆的进度状态，供界面同步这类只读用途。
     */
    public Optional<MaidAdvancementTracker> tracker(EntityMaid maid) {
        Session session = open(maid);
        if (session == null) {
            return Optional.empty();
        }
        session.close();
        return Optional.of(session.tracker);
    }

    /**
     * 进度有变化时标脏，落盘只写真正动过的女仆。由 Forge 的进度事件驱动。
     */
    public void markDirty(Player player) {
        if (!(player instanceof MaidMirrorPlayer mirror)) {
            return;
        }
        EntityMaid maid = mirror.boundMaid();
        if (maid == null) {
            return;
        }
        MaidAdvancementTracker tracker = trackers.get(maid.getUUID());
        if (tracker != null) {
            tracker.markDirty();
        }
    }

    /** 女仆离开世界（卸载、死亡、跨维度）时释放监听并落盘。 */
    public void release(UUID maidId) {
        MaidAdvancementTracker tracker = trackers.remove(maidId);
        if (tracker != null) {
            tracker.dispose();
        }
    }

    public void saveAll() {
        trackers.values().forEach(MaidAdvancementTracker::save);
    }

    public void reloadAll(MinecraftServer server) {
        trackers.values().forEach(tracker -> tracker.reload(server.getAdvancements()));
    }

    public void forgetLevel(ResourceKey<Level> dimension) {
        mirrors.remove(dimension);
    }

    public void shutdown() {
        List<MaidAdvancementTracker> pending = new ArrayList<>(trackers.values());
        trackers.clear();
        mirrors.clear();
        depth = 0;
        pending.forEach(MaidAdvancementTracker::dispose);
    }

    @Nullable
    private Session open(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || maid.getOwnerUUID() == null) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        MaidMirrorPlayer mirror = mirrors.computeIfAbsent(
                level.dimension(),
                dimension -> MaidMirrorPlayer.create(level)
        );
        MaidMirrorPlayer.Binding previous = mirror.bind(maid, null);
        MaidAdvancementTracker tracker = trackers.get(maid.getUUID());
        if (tracker == null) {
            Path savePath = savePath(server, maid.getUUID());
            boolean firstRun = !Files.exists(savePath);
            // 构造时原版会读档并可能补发无 criteria 的进度，所以必须先绑好女仆。
            tracker = new MaidAdvancementTracker(server, savePath, mirror);
            trackers.put(maid.getUUID(), tracker);
            if (firstRun && MaidLegacyAchievementMigration.apply(
                    maid,
                    tracker.advancements(),
                    server.getAdvancements()
            )) {
                tracker.markDirty();
                tracker.save();
            }
        }
        // 女仆可能换了维度，进度里记的玩家要跟着换成本维度的镜像。
        tracker.advancements().setPlayer(mirror);
        mirror.bindAdvancements(tracker.advancements());
        return new Session(mirror, tracker, previous);
    }

    private static Path savePath(MinecraftServer server, UUID maidId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(SAVE_DIRECTORY)
                .resolve(maidId + ".json");
    }

    private static final class Session {
        private final MaidMirrorPlayer mirror;
        private final MaidAdvancementTracker tracker;
        private final MaidMirrorPlayer.Binding previous;

        private Session(
                MaidMirrorPlayer mirror,
                MaidAdvancementTracker tracker,
                MaidMirrorPlayer.Binding previous
        ) {
            this.mirror = mirror;
            this.tracker = tracker;
            this.previous = previous;
        }

        private void close() {
            mirror.restore(previous);
        }
    }
}
