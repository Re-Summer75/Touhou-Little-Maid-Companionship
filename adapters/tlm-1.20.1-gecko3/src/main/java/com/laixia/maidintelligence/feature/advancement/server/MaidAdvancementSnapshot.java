package com.laixia.maidintelligence.feature.advancement.server;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 一只女仆当前该让界面看到的东西：可见的进度条目本身，加上其中有进度的那几条的完成情况。
 * <p>
 * 条目定义也要一起送，不能指望客户端本来就有：客户端那份进度表是服务端**按玩家可见性**
 * 下发的，而女仆专属的那些进度玩家永远不会完成，所以客户端压根不知道它们存在。
 * 可见性的判定与原版对玩家的完全一致（自己或两级以内的祖先已完成），只是换成按女仆的进度算。
 *
 * @param visible  可见条目，一定包含每个可见条目的所有祖先，客户端才解析得出父子关系
 * @param progress 其中有进度的条目
 */
public record MaidAdvancementSnapshot(
        List<Advancement> visible,
        Map<ResourceLocation, AdvancementProgress> progress
) {
}
