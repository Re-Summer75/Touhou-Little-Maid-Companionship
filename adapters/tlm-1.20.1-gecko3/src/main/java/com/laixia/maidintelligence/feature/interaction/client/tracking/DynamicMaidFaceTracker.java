package com.laixia.maidintelligence.feature.interaction.client.tracking;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import com.laixia.maidintelligence.platform.geometry.MinecraftGeometryAdapter;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class DynamicMaidFaceTracker {
    private static final double MAX_TRACE_DISTANCE = 8.0D;
    private static final Vec3d CROSSHAIR_RAY_DIRECTION =
            new Vec3d(0.0D, 0.0D, -1.0D);
    private static final long MAX_REGION_AGE_NANOS = 1_000_000_000L;
    private static final Map<EntityMaid, FaceRegion> REGIONS = new WeakHashMap<>();

    private DynamicMaidFaceTracker() {
    }

    public static void update(EntityMaid maid, MaidFacePlane plane) {
        Vec3d renderCenter = plane.point(
                MouthTargetRegion.CENTER_U,
                MouthTargetRegion.CENTER_V
        );
        Vec3d renderNormal = plane.normal();
        if (renderNormal.dot(renderCenter.scale(-1.0D)) < 0.0D) {
            renderNormal = renderNormal.scale(-1.0D);
        }
        REGIONS.put(maid, new FaceRegion(
                plane,
                ViewSpaceWorldPosition.fromRenderPosition(
                        MinecraftGeometryAdapter.toMinecraft(renderCenter)
                ),
                ViewSpaceWorldPosition.fromRenderDirection(
                        MinecraftGeometryAdapter.toMinecraft(renderNormal)
                ),
                System.nanoTime()
        ));
    }

    public static Optional<FaceCoordinates> trace(EntityMaid maid) {
        FaceRegion region = getFreshRegion(maid);
        if (region == null) {
            return Optional.empty();
        }

        return region.plane()
                .intersectTarget(
                        Vec3d.ZERO,
                        CROSSHAIR_RAY_DIRECTION,
                        MAX_TRACE_DISTANCE
                )
                .map(hit -> new FaceCoordinates(
                        hit.u(),
                        hit.v(),
                        region.worldCenter(),
                        region.worldNormal()
                ));
    }

    public static boolean isTargetingFeedPlane(EntityMaid maid) {
        return trace(maid).isPresent();
    }

    public static Optional<FaceCoordinates> getTrackedFace(EntityMaid maid) {
        FaceRegion region = getFreshRegion(maid);
        if (region == null) {
            return Optional.empty();
        }
        return Optional.of(new FaceCoordinates(
                MouthTargetRegion.CENTER_U,
                MouthTargetRegion.CENTER_V,
                region.worldCenter(),
                region.worldNormal()
        ));
    }

    public static void invalidate(EntityMaid maid) {
        REGIONS.remove(maid);
    }

    public static void clear() {
        REGIONS.clear();
    }

    private static FaceRegion getFreshRegion(EntityMaid maid) {
        FaceRegion region = REGIONS.get(maid);
        if (region == null || System.nanoTime() - region.updatedAtNanos() > MAX_REGION_AGE_NANOS) {
            REGIONS.remove(maid);
            return null;
        }
        return region;
    }

    public record FaceCoordinates(
            float u,
            float v,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
    }

    private record FaceRegion(
            MaidFacePlane plane,
            Vec3 worldCenter,
            Vec3 worldNormal,
            long updatedAtNanos
    ) {
    }
}
