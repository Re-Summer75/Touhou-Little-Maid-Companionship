package com.laixia.maidintelligence.feature.advancement.server;

import com.laixia.maidintelligence.feature.advancement.codec.MinecraftResourceIds;
import com.laixia.maidintelligence.feature.advancement.domain.MaidAdvancementScope;
import com.mojang.datafixers.DataFixer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.players.PlayerList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一只女仆的进度状态。内部就是一份真正的原版 {@link PlayerAdvancements}，
 * 因此监听注册、判定、奖励发放、存档格式全都和玩家完全一致。
 */
public final class MaidAdvancementTracker {
    private final PlayerAdvancements advancements;
    private boolean dirty;

    MaidAdvancementTracker(MinecraftServer server, Path savePath, MaidMirrorPlayer mirror) {
        this.advancements = new ScopedAdvancements(
                server.getFixerUpper(),
                server.getPlayerList(),
                server.getAdvancements(),
                savePath,
                mirror
        );
    }

    PlayerAdvancements advancements() {
        return advancements;
    }

    void markDirty() {
        dirty = true;
    }

    void save() {
        if (dirty) {
            advancements.save();
            dirty = false;
        }
    }

    /**
     * 数据包重载后重新解析进度树，对齐原版对在线玩家的处理。
     */
    void reload(ServerAdvancementManager manager) {
        save();
        advancements.reload(manager);
    }

    void dispose() {
        save();
        advancements.stopListening();
    }

    /**
     * 按女仆自己的完成情况算一遍可见性，取界面要用的条目与进度。
     * <p>
     * 用的是原版那套评估器，所以「自己或两级以内的祖先完成了才显示」这条规则与玩家一模一样：
     * 没有 display 的条目（上千条配方进度）与隐藏条目自然被挡掉，不必另外过滤。
     */
    public MaidAdvancementSnapshot snapshot(ServerAdvancementManager manager) {
        List<Advancement> visible = new ArrayList<>();
        Map<ResourceLocation, AdvancementProgress> progress = new LinkedHashMap<>();
        for (Advancement root : manager.getAllAdvancements()) {
            if (root.getParent() != null || !MaidAdvancementScope.includes(
                    MinecraftResourceIds.toCore(root.getId())
            )) {
                continue;
            }
            AdvancementVisibilityEvaluator.evaluateVisibility(
                    root,
                    advancement -> advancements.getOrStartProgress(advancement).isDone(),
                    (advancement, isVisible) -> {
                        if (!isVisible) {
                            return;
                        }
                        visible.add(advancement);
                        AdvancementProgress entry = advancements.getOrStartProgress(advancement);
                        if (entry.hasProgress()) {
                            progress.put(advancement.getId(), entry);
                        }
                    }
            );
        }
        return new MaidAdvancementSnapshot(visible, progress);
    }

    /**
     * 只是在原版实现外面加一道范围判断：不算女仆的进度一律拒绝授予，
     * 于是进度不记、奖励不发、聊天也不广播。
     * <p>
     * 注意 {@code award} 会被原版构造函数里的自动触发检查调用，所以判断只能用静态状态，
     * 不能依赖本类的实例字段——那时候字段还没初始化。
     */
    private static final class ScopedAdvancements extends PlayerAdvancements {
        private ScopedAdvancements(
                DataFixer fixer,
                PlayerList playerList,
                ServerAdvancementManager manager,
                Path savePath,
                MaidMirrorPlayer mirror
        ) {
            super(fixer, playerList, manager, savePath, mirror);
        }

        @Override
        public boolean award(Advancement advancement, String criterion) {
            return MaidAdvancementScope.includes(
                    MinecraftResourceIds.toCore(
                            advancement.getId()
                    )
            ) && super.award(advancement, criterion);
        }
    }
}
