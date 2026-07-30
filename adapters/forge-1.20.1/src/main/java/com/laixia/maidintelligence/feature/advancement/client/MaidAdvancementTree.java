package com.laixia.maidintelligence.feature.advancement.client;

import com.laixia.maidintelligence.feature.advancement.application.layout.AdvancementTreeLayout;
import com.laixia.maidintelligence.feature.advancement.domain.MaidAdvancementScope;
import com.laixia.maidintelligence.feature.advancement.codec.MinecraftResourceIds;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * 一棵根进度下的整颗树，对应原版的 {@code AdvancementTab}：负责布局边界、平移、
 * 背景平铺、连线与悬浮框的分发。进度数据来自女仆而不是玩家。
 */
@OnlyIn(Dist.CLIENT)
final class MaidAdvancementTree {
    private final Advancement root;
    private final DisplayInfo display;
    private final Map<Advancement, MaidAdvancementWidget> widgets = new LinkedHashMap<>();

    private double scrollX;
    private double scrollY;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private boolean centered;
    private float fade;
    private int completed;

    private MaidAdvancementTree(Advancement root, DisplayInfo display) {
        this.root = root;
        this.display = display;
    }

    @Nullable
    static MaidAdvancementTree create(Minecraft minecraft, Advancement root) {
        DisplayInfo display = root.getDisplay();
        if (display == null || !MaidAdvancementScope.includes(
                MinecraftResourceIds.toCore(root.getId())
        )) {
            return null;
        }
        MaidAdvancementTree tree = new MaidAdvancementTree(root, display);
        tree.add(minecraft, root);
        return tree;
    }

    private void add(Minecraft minecraft, Advancement advancement) {
        if (!MaidAdvancementScope.includes(
                MinecraftResourceIds.toCore(advancement.getId())
        )) {
            // 整条子树一起跳过：留下的子节点会因为找不到父控件变成画不出来的孤儿，还会把总数算多。
            return;
        }
        DisplayInfo info = advancement.getDisplay();
        if (info != null) {
            MaidAdvancementWidget widget = new MaidAdvancementWidget(minecraft, advancement, info);
            widgets.put(advancement, widget);
            minX = Math.min(minX, widget.x());
            maxX = Math.max(maxX, widget.x() + 28);
            minY = Math.min(minY, widget.y());
            maxY = Math.max(maxY, widget.y() + 27);
            firstVisibleParent(advancement).ifPresent(parent -> parent.addChild(widget));
        }
        for (Advancement child : advancement.getChildren()) {
            add(minecraft, child);
        }
    }

    private Optional<MaidAdvancementWidget> firstVisibleParent(Advancement advancement) {
        Advancement parent = advancement.getParent();
        while (parent != null && parent.getDisplay() == null) {
            parent = parent.getParent();
        }
        return Optional.ofNullable(parent).map(widgets::get);
    }

    Advancement root() {
        return root;
    }

    Component title() {
        return display.getTitle();
    }

    ItemStack icon() {
        return display.getIcon();
    }

    int total() {
        return widgets.size();
    }

    int completed() {
        return completed;
    }

    /**
     * 收到推送重建整棵树时，把旧树看到哪儿接过来。不接的话每拿一条进度视野就跳回中心。
     */
    void adoptViewFrom(MaidAdvancementTree previous) {
        if (previous.centered) {
            scrollX = previous.scrollX;
            scrollY = previous.scrollY;
            centered = true;
        }
    }

    void applyProgress(Map<ResourceLocation, AdvancementProgress> progress) {
        completed = 0;
        widgets.forEach((advancement, widget) -> {
            AdvancementProgress entry = progress.get(advancement.getId());
            widget.setProgress(entry);
            if (widget.isDone()) {
                completed++;
            }
        });
    }

    /**
     * 画树的内容。视口裁剪与坐标平移都在这里，画完恢复现场。
     */
    void drawContents(GuiGraphics graphics, int left, int top, int width, int height) {
        if (!centered) {
            scrollX = AdvancementTreeLayout.center(width, minX, maxX);
            scrollY = AdvancementTreeLayout.center(height, minY, maxY);
            centered = true;
        }

        graphics.enableScissor(left, top, left + width, top + height);
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        int offsetX = Mth.floor(scrollX);
        int offsetY = Mth.floor(scrollY);
        AdvancementStyle.drawTiledBackground(graphics, display.getBackground(), width, height, offsetX, offsetY);
        MaidAdvancementWidget rootWidget = widgets.get(root);
        if (rootWidget != null) {
            rootWidget.drawConnectivity(graphics, offsetX, offsetY, true);
            rootWidget.drawConnectivity(graphics, offsetX, offsetY, false);
            rootWidget.draw(graphics, offsetX, offsetY);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    /**
     * 画鼠标指着的那个条目的说明框，同时推进原版那套压暗渐变。
     * 说明框会溢出视口，所以这一步必须在裁剪之外画。
     */
    void drawHover(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            int mouseX,
            int mouseY,
            int screenWidth
    ) {
        int localX = mouseX - left;
        int localY = mouseY - top;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 400.0F);
        graphics.fill(0, 0, width, height, Mth.floor(fade * 255.0F) << 24);

        boolean hovered = false;
        int offsetX = Mth.floor(scrollX);
        int offsetY = Mth.floor(scrollY);
        if (localX > 0 && localX < width && localY > 0 && localY < height) {
            for (MaidAdvancementWidget widget : widgets.values()) {
                if (widget.isMouseOver(offsetX, offsetY, localX, localY)) {
                    hovered = true;
                    widget.drawHover(graphics, offsetX, offsetY, left, height, screenWidth);
                    break;
                }
            }
        }
        graphics.pose().popPose();

        fade = hovered
                ? Mth.clamp(fade + 0.02F, 0.0F, 0.3F)
                : Mth.clamp(fade - 0.04F, 0.0F, 1.0F);
    }

    void scroll(double dragX, double dragY, int width, int height) {
        scrollX = AdvancementTreeLayout.clampScroll(scrollX + dragX, minX, maxX, width);
        scrollY = AdvancementTreeLayout.clampScroll(scrollY + dragY, minY, maxY, height);
    }
}
