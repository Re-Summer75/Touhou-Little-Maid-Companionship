package com.laixia.maidintelligence.feature.advancement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidgetType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * 一个进度节点，是原版 {@code AdvancementWidget} 的移植：位置换算、连线、边框、
 * 悬浮说明框的九宫格与折行策略全部照原样，只把「屏幕宽 / 视口高」这些原版写死的量改成参数，
 * 因为女仆界面里的视口比原版进度界面小得多。
 */
@OnlyIn(Dist.CLIENT)
final class MaidAdvancementWidget {
    private static final int TITLE_MAX_WIDTH = 163;
    private static final int[] TEST_SPLIT_OFFSETS = {0, 10, -10, 25, -25};

    private final Minecraft minecraft;
    private final Advancement advancement;
    private final DisplayInfo display;
    private final FormattedCharSequence title;
    private final List<FormattedCharSequence> description;
    private final List<MaidAdvancementWidget> children = new ArrayList<>();
    private final int width;
    private final int x;
    private final int y;

    @Nullable
    private MaidAdvancementWidget parent;
    @Nullable
    private AdvancementProgress progress;

    MaidAdvancementWidget(Minecraft minecraft, Advancement advancement, DisplayInfo display) {
        this.minecraft = minecraft;
        this.advancement = advancement;
        this.display = display;
        this.title = Language.getInstance()
                .getVisualOrder(minecraft.font.substrByWidth(display.getTitle(), TITLE_MAX_WIDTH));
        this.x = Mth.floor(display.getX() * 28.0F);
        this.y = Mth.floor(display.getY() * 27.0F);

        int required = advancement.getMaxCriteraRequired();
        int digits = String.valueOf(required).length();
        int counterWidth = required > 1
                ? minecraft.font.width("  ") + minecraft.font.width("0") * digits * 2 + minecraft.font.width("/")
                : 0;
        int boxWidth = 29 + minecraft.font.width(this.title) + counterWidth;
        this.description = Language.getInstance().getVisualOrder(findOptimalLines(
                ComponentUtils.mergeStyles(
                        display.getDescription().copy(),
                        Style.EMPTY.withColor(display.getFrame().getChatColor())
                ),
                boxWidth
        ));
        for (FormattedCharSequence line : this.description) {
            boxWidth = Math.max(boxWidth, minecraft.font.width(line));
        }
        this.width = boxWidth + 3 + 5;
    }

    Advancement advancement() {
        return advancement;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    void setProgress(@Nullable AdvancementProgress progress) {
        this.progress = progress;
    }

    boolean isDone() {
        return progress != null && progress.isDone();
    }

    void addChild(MaidAdvancementWidget child) {
        children.add(child);
        child.parent = this;
    }

    void drawConnectivity(GuiGraphics graphics, int scrollX, int scrollY, boolean dropShadow) {
        if (parent != null) {
            int parentRight = scrollX + parent.x + 13;
            int elbow = scrollX + parent.x + 26 + 4;
            int parentMiddle = scrollY + parent.y + 13;
            int selfLeft = scrollX + x + 13;
            int selfMiddle = scrollY + y + 13;
            int color = dropShadow ? 0xFF000000 : 0xFFFFFFFF;
            if (dropShadow) {
                graphics.hLine(elbow, parentRight, parentMiddle - 1, color);
                graphics.hLine(elbow + 1, parentRight, parentMiddle, color);
                graphics.hLine(elbow, parentRight, parentMiddle + 1, color);
                graphics.hLine(selfLeft, elbow - 1, selfMiddle - 1, color);
                graphics.hLine(selfLeft, elbow - 1, selfMiddle, color);
                graphics.hLine(selfLeft, elbow - 1, selfMiddle + 1, color);
                graphics.vLine(elbow - 1, selfMiddle, parentMiddle, color);
                graphics.vLine(elbow + 1, selfMiddle, parentMiddle, color);
            } else {
                graphics.hLine(elbow, parentRight, parentMiddle, color);
                graphics.hLine(selfLeft, elbow, selfMiddle, color);
                graphics.vLine(elbow, selfMiddle, parentMiddle, color);
            }
        }
        for (MaidAdvancementWidget child : children) {
            child.drawConnectivity(graphics, scrollX, scrollY, dropShadow);
        }
    }

    void draw(GuiGraphics graphics, int scrollX, int scrollY) {
        if (isVisible()) {
            float percent = progress == null ? 0.0F : progress.getPercent();
            AdvancementWidgetType type = percent >= 1.0F
                    ? AdvancementWidgetType.OBTAINED
                    : AdvancementWidgetType.UNOBTAINED;
            graphics.blit(
                    AdvancementStyle.WIDGETS,
                    scrollX + x + AdvancementStyle.FRAME_OFFSET_X,
                    scrollY + y,
                    display.getFrame().getTexture(),
                    AdvancementStyle.FRAME_V + type.getIndex() * AdvancementStyle.FRAME_SIZE,
                    AdvancementStyle.FRAME_SIZE,
                    AdvancementStyle.FRAME_SIZE
            );
            graphics.renderFakeItem(
                    display.getIcon(),
                    scrollX + x + AdvancementStyle.ICON_OFFSET_X,
                    scrollY + y + AdvancementStyle.ICON_OFFSET_Y
            );
        }
        for (MaidAdvancementWidget child : children) {
            child.draw(graphics, scrollX, scrollY);
        }
    }

    boolean isMouseOver(int scrollX, int scrollY, int mouseX, int mouseY) {
        if (!isVisible()) {
            return false;
        }
        int left = scrollX + x;
        int top = scrollY + y;
        return mouseX >= left
                && mouseX <= left + AdvancementStyle.FRAME_SIZE
                && mouseY >= top
                && mouseY <= top + AdvancementStyle.FRAME_SIZE;
    }

    /**
     * 画悬浮说明框。坐标按已平移到视口原点的局部坐标算，
     * {@code viewportLeft} 只用来判断说明框会不会顶出屏幕右边，{@code viewportHeight} 决定说明往上还是往下展开。
     */
    void drawHover(
            GuiGraphics graphics,
            int scrollX,
            int scrollY,
            int viewportLeft,
            int viewportHeight,
            int screenWidth
    ) {
        boolean flipHorizontally = viewportLeft + scrollX + x + width + AdvancementStyle.FRAME_SIZE >= screenWidth;
        String progressText = progress == null ? null : progress.getProgressText();
        int progressWidth = progressText == null ? 0 : minecraft.font.width(progressText);
        boolean descriptionAbove = viewportHeight - scrollY - y - AdvancementStyle.ENTRY_HEIGHT
                <= 6 + description.size() * 9;
        float percent = progress == null ? 0.0F : progress.getPercent();
        int split = Mth.floor(percent * width);

        AdvancementWidgetType leftBar;
        AdvancementWidgetType rightBar;
        AdvancementWidgetType frame;
        if (percent >= 1.0F) {
            split = width / 2;
            leftBar = AdvancementWidgetType.OBTAINED;
            rightBar = AdvancementWidgetType.OBTAINED;
            frame = AdvancementWidgetType.OBTAINED;
        } else if (split < 2) {
            split = width / 2;
            leftBar = AdvancementWidgetType.UNOBTAINED;
            rightBar = AdvancementWidgetType.UNOBTAINED;
            frame = AdvancementWidgetType.UNOBTAINED;
        } else if (split > width - 2) {
            split = width / 2;
            leftBar = AdvancementWidgetType.OBTAINED;
            rightBar = AdvancementWidgetType.OBTAINED;
            frame = AdvancementWidgetType.UNOBTAINED;
        } else {
            leftBar = AdvancementWidgetType.OBTAINED;
            rightBar = AdvancementWidgetType.UNOBTAINED;
            frame = AdvancementWidgetType.UNOBTAINED;
        }
        int rest = width - split;

        RenderSystem.enableBlend();
        int boxTop = scrollY + y;
        int boxLeft = flipHorizontally
                ? scrollX + x - width + AdvancementStyle.FRAME_SIZE + 6
                : scrollX + x;
        int panelHeight = 32 + description.size() * 9;
        if (!description.isEmpty()) {
            graphics.blitNineSliced(
                    AdvancementStyle.WIDGETS,
                    boxLeft,
                    descriptionAbove ? boxTop + AdvancementStyle.ENTRY_HEIGHT - panelHeight : boxTop,
                    width,
                    panelHeight,
                    10,
                    200,
                    26,
                    0,
                    52
            );
        }

        graphics.blit(
                AdvancementStyle.WIDGETS,
                boxLeft,
                boxTop,
                0,
                leftBar.getIndex() * AdvancementStyle.ENTRY_HEIGHT,
                split,
                AdvancementStyle.ENTRY_HEIGHT
        );
        graphics.blit(
                AdvancementStyle.WIDGETS,
                boxLeft + split,
                boxTop,
                AdvancementStyle.BOX_TEXTURE_WIDTH - rest,
                rightBar.getIndex() * AdvancementStyle.ENTRY_HEIGHT,
                rest,
                AdvancementStyle.ENTRY_HEIGHT
        );
        graphics.blit(
                AdvancementStyle.WIDGETS,
                scrollX + x + AdvancementStyle.FRAME_OFFSET_X,
                scrollY + y,
                display.getFrame().getTexture(),
                AdvancementStyle.FRAME_V + frame.getIndex() * AdvancementStyle.FRAME_SIZE,
                AdvancementStyle.FRAME_SIZE,
                AdvancementStyle.FRAME_SIZE
        );

        int titleY = scrollY + y + AdvancementStyle.TITLE_OFFSET_Y;
        if (flipHorizontally) {
            graphics.drawString(minecraft.font, title, boxLeft + 5, titleY, AdvancementStyle.TEXT_COLOR);
            if (progressText != null) {
                graphics.drawString(
                        minecraft.font,
                        progressText,
                        scrollX + x - progressWidth,
                        titleY,
                        AdvancementStyle.TEXT_COLOR
                );
            }
        } else {
            graphics.drawString(
                    minecraft.font,
                    title,
                    scrollX + x + AdvancementStyle.TITLE_OFFSET_X,
                    titleY,
                    AdvancementStyle.TEXT_COLOR
            );
            if (progressText != null) {
                graphics.drawString(
                        minecraft.font,
                        progressText,
                        scrollX + x + width - progressWidth - 5,
                        titleY,
                        AdvancementStyle.TEXT_COLOR
                );
            }
        }

        for (int line = 0; line < description.size(); line++) {
            int lineY = descriptionAbove
                    ? boxTop + AdvancementStyle.ENTRY_HEIGHT - panelHeight + 7 + line * 9
                    : scrollY + y + AdvancementStyle.TITLE_OFFSET_Y + 17 + line * 9;
            graphics.drawString(
                    minecraft.font,
                    description.get(line),
                    boxLeft + 5,
                    lineY,
                    AdvancementStyle.DESCRIPTION_COLOR,
                    false
            );
        }

        graphics.renderFakeItem(
                display.getIcon(),
                scrollX + x + AdvancementStyle.ICON_OFFSET_X,
                scrollY + y + AdvancementStyle.ICON_OFFSET_Y
        );
    }

    /** 隐藏条目只有拿到之后才显示，和原版一致。 */
    private boolean isVisible() {
        return !display.isHidden() || isDone();
    }

    private List<FormattedText> findOptimalLines(Component text, int maxWidth) {
        StringSplitter splitter = minecraft.font.getSplitter();
        List<FormattedText> best = null;
        float bestDelta = Float.MAX_VALUE;
        for (int offset : TEST_SPLIT_OFFSETS) {
            List<FormattedText> candidate = splitter.splitLines(text, maxWidth - offset, Style.EMPTY);
            float delta = Math.abs(maxLineWidth(splitter, candidate) - (float) maxWidth);
            if (delta <= 10.0F) {
                return candidate;
            }
            if (delta < bestDelta) {
                bestDelta = delta;
                best = candidate;
            }
        }
        return best == null ? List.of() : best;
    }

    private static float maxLineWidth(StringSplitter splitter, List<FormattedText> lines) {
        return (float) lines.stream().mapToDouble(splitter::stringWidth).max().orElse(0.0D);
    }
}
