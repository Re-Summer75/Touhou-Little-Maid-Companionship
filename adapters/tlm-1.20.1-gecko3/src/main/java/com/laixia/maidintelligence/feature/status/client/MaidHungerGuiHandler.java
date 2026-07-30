package com.laixia.maidintelligence.feature.status.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class MaidHungerGuiHandler {
    private static final ResourceLocation MAID_GUI_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid",
            "textures/gui/maid_gui_main.png"
    );
    private static final ResourceLocation MAID_GUI_SIDE = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid",
            "textures/gui/maid_gui_side.png"
    );
    private static final ResourceLocation VANILLA_GUI_ICONS = ResourceLocation.fromNamespaceAndPath(
            "minecraft",
            "textures/gui/icons.png"
    );

    private static final int ROW_X = 5;
    private static final int ROW_Y = 146;
    private static final int ROW_WIDTH = 67;
    private static final int ROW_HEIGHT = 9;
    private static final int BAR_X = 7;
    private static final int BAR_Y = 148;
    private static final int BAR_WIDTH = 43;
    private static final int BAR_HEIGHT = 5;
    private static final int ICON_X = 53;
    private static final int NUMBER_X = 63;
    private static final int NUMBER_Y = 147;
    private static final int FULL_HUNGER_ICON_U = 52;
    private static final int HUNGER_ICON_V = 27;
    private static final int TEXTURED_BAR_U = 2;
    private static final int TEXTURED_BAR_V = 28;
    private static final float HUNGER_TINT_RED = 1.0F;
    private static final float HUNGER_TINT_GREEN = 0.62F;
    private static final float HUNGER_TINT_BLUE = 0.24F;
    private static final int SATURATION_BAR_HEIGHT = 2;

    private final MaidStatusApi<EntityMaid> statusApi;

    public MaidHungerGuiHandler(MaidStatusApi<EntityMaid> statusApi) {
        this.statusApi = statusApi;
    }

    @SubscribeEvent
    public void onRender(MaidContainerGuiEvent.Render event) {
        MaidStatusState state = statusApi.getState(event.getGui().getMaid());
        int hunger = state.hunger();
        int left = event.getLeftPos();
        int top = event.getTopPos();
        GuiGraphics graphics = event.getGraphics();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 10.0F);
        clearFavorabilityRow(graphics, left, top);
        drawHungerBar(graphics, left, top, state);
        drawHungerIcon(graphics, left, top);
        drawHungerNumber(graphics, left, top, hunger);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public void onTooltip(MaidContainerGuiEvent.Tooltip event) {
        int left = event.getLeftPos();
        int top = event.getTopPos();
        if (event.getMouseX() < left + ROW_X
                || event.getMouseX() >= left + ROW_X + ROW_WIDTH
                || event.getMouseY() < top + ROW_Y
                || event.getMouseY() >= top + ROW_Y + ROW_HEIGHT) {
            return;
        }

        MaidStatusState state = statusApi.getState(event.getGui().getMaid());
        event.getGraphics().renderTooltip(
                Minecraft.getInstance().font,
                Component.translatable(
                        ModResources.translationKey("gui", "hunger"),
                        state.hunger(),
                        DefaultHungerPolicy.MAX_HUNGER,
                        String.format(Locale.ROOT, "%.1f", state.saturation()),
                        DefaultHungerPolicy.MAX_HUNGER
                ),
                event.getMouseX(),
                event.getMouseY()
        );
    }

    private static void clearFavorabilityRow(
            GuiGraphics graphics,
            int left,
            int top
    ) {
        graphics.blit(
                MAID_GUI_SIDE,
                left + ROW_X,
                top + ROW_Y,
                0,
                9,
                47,
                ROW_HEIGHT
        );
        graphics.blit(
                MAID_GUI_BACKGROUND,
                left + ICON_X,
                top + ROW_Y,
                ICON_X,
                ROW_Y,
                19,
                ROW_HEIGHT
        );
    }

    private static void drawHungerBar(
            GuiGraphics graphics,
            int left,
            int top,
            MaidStatusState state
    ) {
        int hungerWidth = Math.round(
                BAR_WIDTH * state.hunger() / (float) DefaultHungerPolicy.MAX_HUNGER
        );
        if (hungerWidth > 0) {
            RenderSystem.setShaderColor(
                    HUNGER_TINT_RED,
                    HUNGER_TINT_GREEN,
                    HUNGER_TINT_BLUE,
                    1.0F
            );
            graphics.blit(
                    MAID_GUI_SIDE,
                    left + BAR_X,
                    top + BAR_Y,
                    TEXTURED_BAR_U,
                    TEXTURED_BAR_V,
                    hungerWidth,
                    BAR_HEIGHT
            );
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int saturationWidth = Math.round(
                BAR_WIDTH * state.saturation() / DefaultHungerPolicy.MAX_HUNGER
        );
        if (saturationWidth > 0) {
            graphics.blit(
                    MAID_GUI_SIDE,
                    left + BAR_X,
                    top + BAR_Y + BAR_HEIGHT - SATURATION_BAR_HEIGHT,
                    TEXTURED_BAR_U,
                    TEXTURED_BAR_V,
                    saturationWidth,
                    SATURATION_BAR_HEIGHT
            );
        }
    }

    private static void drawHungerIcon(GuiGraphics graphics, int left, int top) {
        graphics.blit(
                VANILLA_GUI_ICONS,
                left + ICON_X,
                top + ROW_Y,
                FULL_HUNGER_ICON_U,
                HUNGER_ICON_V,
                9,
                9
        );
    }

    private static void drawHungerNumber(
            GuiGraphics graphics,
            int left,
            int top,
            int hunger
    ) {
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(
                font,
                String.valueOf(hunger),
                (left + NUMBER_X) * 2,
                (top + NUMBER_Y) * 2 + font.lineHeight / 2,
                0xFF555555,
                false
        );
        graphics.pose().popPose();
    }
}
