package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Decides whether a maid needs face tracking this frame, so the capture and
 * geometry pipeline can be skipped entirely when nothing could consume the
 * result. Skipping never changes an observable outcome:
 *
 * <ul>
 *     <li>{@link DynamicMaidFaceTracker#trace} rays are capped at 8 blocks
 *     from the camera; beyond the distance gate they fail regardless of
 *     whether a region exists.</li>
 *     <li>{@link DynamicMaidFaceTracker#getTrackedFace} is only consumed by
 *     eating-particle spawns, which require the maid to be using an item or
 *     to have a pending tracked-face effect — both keep tracking on.</li>
 *     <li>Debug hitboxes keep tracking on so the marker overlay stays
 *     complete.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class FaceTrackingDemand {
    // trace() reaches 8 blocks from the camera; the rest is headroom for face
    // planes that sit far from the entity origin on oversized models.
    private static final double MAX_RELEVANT_DISTANCE_SQR = 24.0D * 24.0D;

    private FaceTrackingDemand() {
    }

    static boolean shouldTrack(EntityMaid maid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            return true;
        }
        if (maid.isUsingItem()) {
            return true;
        }
        if (MaidEatingParticleEffect.hasPendingTrackedFaceEffect(maid)) {
            return true;
        }
        return maid.distanceToSqr(
                minecraft.gameRenderer.getMainCamera().getPosition()
        ) <= MAX_RELEVANT_DISTANCE_SQR;
    }
}
