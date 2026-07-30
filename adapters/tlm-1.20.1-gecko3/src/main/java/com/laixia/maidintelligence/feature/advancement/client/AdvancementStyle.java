package com.laixia.maidintelligence.feature.advancement.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 原版进度界面的贴图常量与背景平铺，供 {@link MaidAdvancementWidget} 与
 * {@link MaidAdvancementTree} 复用，数值全部取自 {@code AdvancementWidget} 与 {@code AdvancementTab}。
 */
@OnlyIn(Dist.CLIENT)
final class AdvancementStyle {
    static final ResourceLocation WIDGETS =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/advancements/widgets.png");
    /** 条目边框与进度条都是 26 像素高。 */
    static final int ENTRY_HEIGHT = 26;
    static final int FRAME_SIZE = 26;
    /** 边框在贴图里的起始行，往下每 26 像素一档（已达成 / 未达成）。 */
    static final int FRAME_V = 128;
    /** 进度条贴图总宽，右半段要从末尾往回取。 */
    static final int BOX_TEXTURE_WIDTH = 200;
    static final int ICON_OFFSET_X = 8;
    static final int ICON_OFFSET_Y = 5;
    static final int FRAME_OFFSET_X = 3;
    static final int TITLE_OFFSET_X = 32;
    static final int TITLE_OFFSET_Y = 9;
    static final int TEXT_COLOR = 0xFFFFFFFF;
    static final int DESCRIPTION_COLOR = 0xFFAAAAAA;

    private static final int TILE_SIZE = 16;

    private AdvancementStyle() {
    }

    /**
     * 平铺根进度指定的背景贴图并跟随平移，和原版拖动进度树时的观感一致。
     * 坐标按已平移到视口原点的局部坐标算。
     */
    static void drawTiledBackground(
            GuiGraphics graphics,
            @Nullable ResourceLocation background,
            int width,
            int height,
            int scrollX,
            int scrollY
    ) {
        ResourceLocation texture = Objects.requireNonNullElse(
                background,
                TextureManager.INTENTIONAL_MISSING_TEXTURE
        );
        int offsetX = scrollX % TILE_SIZE;
        int offsetY = scrollY % TILE_SIZE;
        for (int x = offsetX - TILE_SIZE; x < width; x += TILE_SIZE) {
            for (int y = offsetY - TILE_SIZE; y < height; y += TILE_SIZE) {
                graphics.blit(texture, x, y, 0.0F, 0.0F, TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
    }
}
