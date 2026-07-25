package com.laixia.maidintelligence.feature.advancement.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 进度页入口。默认是顶部 Tab 条上的一格，套用本体 {@code MaidTabButton} 的画法：
 * 当前就在进度页时画选中框，其余时候只画图标、让暗色导轨透出来。
 * <p>
 * 六格 Tab 全被其他附属占满时退化成 {@code (72,14)} 的 9×9 小图标按钮，
 * 位置见 docs/MAID_GUI_ANALYSIS.md 第 15.2 节。
 */
@OnlyIn(Dist.CLIENT)
public final class MaidAdvancementTabButton extends Button implements ITooltipButton {
    public static final int COMPACT_SIZE = 9;
    public static final int COMPACT_X = 72;
    public static final int COMPACT_Y = 14;

    private static final ResourceLocation TAB_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "textures/gui/maid_gui_side.png");
    private static final int TAB_SELECTED_V = 21;
    private static final int TAB_ICON_OFFSET_X = 4;
    private static final int TAB_ICON_OFFSET_Y = 6;
    private static final int TEXTURE_SIZE = 256;

    private static final ItemStack ICON = new ItemStack(Items.NETHER_STAR);
    private static final float COMPACT_ICON_SCALE = COMPACT_SIZE / 16.0F;
    private static final int HOVER_OVERLAY = 0x40FFFFFF;

    private static final List<Component> TOOLTIP = List.of(
            Component.translatable(ModResources.translationKey("gui", "advancement.tab")),
            Component.translatable(ModResources.translationKey("gui", "advancement.tab.desc"))
    );

    private final boolean selected;
    private int tabTextureU = -1;

    public MaidAdvancementTabButton(boolean selected, OnPress onPress) {
        super(0, 0, AdvancementTabSlots.WIDTH, AdvancementTabSlots.HEIGHT, Component.empty(), onPress, DEFAULT_NARRATION);
        this.selected = selected;
        // 与本体一致：正处在该页时 Tab 不可点，也不再弹提示。
        this.active = !selected;
    }

    public void placeInTabRow(int leftPos, int topPos, int slot) {
        this.setX(leftPos + AdvancementTabSlots.x(slot));
        this.setY(topPos + AdvancementTabSlots.Y);
        this.width = AdvancementTabSlots.WIDTH;
        this.height = AdvancementTabSlots.HEIGHT;
        this.tabTextureU = AdvancementTabSlots.textureU(slot);
    }

    public void placeInCorner(int leftPos, int topPos) {
        this.setX(leftPos + COMPACT_X);
        this.setY(topPos + COMPACT_Y);
        this.width = COMPACT_SIZE;
        this.height = COMPACT_SIZE;
        this.tabTextureU = -1;
    }

    public boolean isInTabRow() {
        return tabTextureU >= 0;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isInTabRow()) {
            renderTab(graphics);
        } else {
            renderCompact(graphics);
        }
    }

    @Override
    public boolean isTooltipHovered() {
        return this.active && this.isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        graphics.renderComponentTooltip(mc.font, TOOLTIP, mouseX, mouseY);
    }

    private void renderTab(GuiGraphics graphics) {
        RenderSystem.enableDepthTest();
        if (selected) {
            graphics.blit(
                    TAB_TEXTURE,
                    this.getX(),
                    this.getY(),
                    tabTextureU,
                    TAB_SELECTED_V,
                    this.width,
                    this.height,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE
            );
        }
        graphics.renderFakeItem(ICON, this.getX() + TAB_ICON_OFFSET_X, this.getY() + TAB_ICON_OFFSET_Y);
    }

    private void renderCompact(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(this.getX(), this.getY(), 0.0F);
        graphics.pose().scale(COMPACT_ICON_SCALE, COMPACT_ICON_SCALE, 1.0F);
        graphics.renderFakeItem(ICON, 0, 0);
        graphics.pose().popPose();

        if (isHovered()) {
            graphics.fill(
                    this.getX(),
                    this.getY(),
                    this.getX() + this.width,
                    this.getY() + this.height,
                    HOVER_OVERLAY
            );
        }
    }
}
