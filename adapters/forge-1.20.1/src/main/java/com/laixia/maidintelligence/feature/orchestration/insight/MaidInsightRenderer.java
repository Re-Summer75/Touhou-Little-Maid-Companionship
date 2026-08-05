package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.platform.item.SoulLensItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws the soul lens panel beside whoever the client has an insight for.
 *
 * <p>Hooked to {@link RenderLivingEvent.Post} rather than added as a model
 * layer. A layer runs inside the model renderer, where the pose stack already
 * carries the entity's body yaw, so billboarding there composes two rotations
 * and the text leans with whichever way she is facing. This event hands over
 * the same frame a vanilla name tag is drawn in, and the transform below is
 * the same sequence vanilla uses in it.
 *
 * <p>Nothing here knows what a maid is. The client cache is keyed by entity id
 * and only ever filled for maids, so "has an insight" is the whole test — which
 * keeps the renderer out of the TLM adapter entirely.
 */
public final class MaidInsightRenderer {
    /** Blocks to the viewer's right of the entity, before text scaling. */
    private static final float SIDE_OFFSET = 0.75F;

    /** Same scale vanilla name tags use, so the panel matches them. */
    private static final float TEXT_SCALE = 0.025F;

    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND = 0x50000000;
    private static final int TEXT_COLOUR = 0xFFFFFFFF;

    private static final double MAX_DISTANCE_SQUARED = 20.0D * 20.0D;

    private MaidInsightRenderer() {
    }

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!SoulLensItem.isHeldBy(player)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (player.distanceToSqr(entity) > MAX_DISTANCE_SQUARED) {
            return;
        }
        MaidInsight insight = ClientMaidInsights.get(entity.getId());
        if (insight == null) {
            return;
        }
        List<Component> lines = MaidInsightLines.build(insight);
        if (lines.isEmpty()) {
            return;
        }
        draw(
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                minecraft.font,
                lines,
                entity
        );
    }

    private static void draw(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Font font,
            List<Component> lines,
            LivingEntity entity
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() + 0.5F, 0.0F);
        poseStack.mulPose(
                Minecraft.getInstance()
                        .getEntityRenderDispatcher()
                        .cameraOrientation()
        );
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        // Sideways last, in the billboarded frame, so "right" is the viewer's
        // right and not a fixed compass direction. Negative because the scale
        // above mirrors the X axis, exactly as vanilla name tags do.
        poseStack.translate(-SIDE_OFFSET / TEXT_SCALE, 0.0F, 0.0F);

        Matrix4f matrix = poseStack.last().pose();
        int top = -(lines.size() * LINE_HEIGHT) / 2;
        /*
         * SEE_THROUGH with a background colour is what vanilla name tags use:
         * each line's backing quad is drawn in the same pass as its glyphs, so
         * the panel stays legible against any terrain without this class
         * managing a render type or sorting anything itself.
         */
        for (int index = 0; index < lines.size(); index++) {
            font.drawInBatch(
                    lines.get(index),
                    0.0F,
                    top + index * LINE_HEIGHT,
                    TEXT_COLOUR,
                    false,
                    matrix,
                    bufferSource,
                    Font.DisplayMode.SEE_THROUGH,
                    BACKGROUND,
                    packedLight
            );
        }
        poseStack.popPose();
    }
}
