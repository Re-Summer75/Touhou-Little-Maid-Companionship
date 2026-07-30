package com.laixia.maidintelligence.feature.interaction.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

public final class MaidMouthFeedRequestHandler {
    private static final double AIM_VALIDATION_INFLATE = 0.25D;
    private static final double MAX_FACE_CENTER_DISTANCE = 4.0D;
    private static final double REACH_TOLERANCE = 1.0D;

    private MaidMouthFeedRequestHandler() {
    }

    public static void handle(
            ServerPlayer player,
            int maidEntityId,
            float faceU,
            float faceV,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        Entity entity = player.serverLevel().getEntity(maidEntityId);
        if (!(entity instanceof EntityMaid maid)
                || player.isShiftKeyDown()
                || maid.isSleeping()
                || !MouthTargetRegion.contains(faceU, faceV)
                || !isValidFaceCenter(maid, worldCenter)
                || !isValidFaceNormal(worldNormal)
                || !maid.isOwnedBy(player)
                || !player.hasLineOfSight(maid)
                || !isAimingAtMaid(player, maid)) {
            return;
        }

        MaidFeedingService.feed(player, maid, worldCenter, worldNormal.normalize());
    }

    private static boolean isValidFaceCenter(EntityMaid maid, Vec3 position) {
        return Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z)
                && position.distanceToSqr(maid.getBoundingBox().getCenter())
                <= MAX_FACE_CENTER_DISTANCE * MAX_FACE_CENTER_DISTANCE;
    }

    private static boolean isValidFaceNormal(Vec3 normal) {
        double lengthSquared = normal.lengthSqr();
        return Double.isFinite(normal.x)
                && Double.isFinite(normal.y)
                && Double.isFinite(normal.z)
                && lengthSquared >= 0.5D
                && lengthSquared <= 1.5D;
    }

    private static boolean isAimingAtMaid(ServerPlayer player, EntityMaid maid) {
        double reach = player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) + REACH_TOLERANCE;
        if (player.distanceToSqr(maid) > reach * reach) {
            return false;
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        return maid.getBoundingBox()
                .inflate(AIM_VALIDATION_INFLATE)
                .clip(start, end)
                .isPresent();
    }
}
