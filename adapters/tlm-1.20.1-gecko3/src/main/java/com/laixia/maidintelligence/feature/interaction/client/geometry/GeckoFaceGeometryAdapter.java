package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.interaction.client.tracking.FaceTrackingGeometryCache;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;
import java.util.Optional;

public final class GeckoFaceGeometryAdapter {
    private GeckoFaceGeometryAdapter() {
    }

    public static Result resolve(
            AnimatedGeoModel model,
            PoseStack basePose,
            String variantKey
    ) {
        GeckoGeometryPlan plan = FaceTrackingGeometryCache.getOrCompute(
                model,
                variantKey,
                GeckoGeometryPlan.class,
                () -> GeckoGeometryDiscovery.discover(model, basePose)
        );
        if (plan.failureReason() != null) {
            if (plan.failureReason()
                    == FaceGeometry.FailureReason.INVALID_FRAME) {
                FaceTrackingGeometryCache.invalidatePlan(model);
            }
            return Result.failure(plan.failureReason());
        }

        FaceGeometry.Frame frame = GeckoGeometryTransforms.createFrame(
                basePose,
                plan.anchorHierarchy()
        ).orElse(null);
        if (frame == null) {
            FaceTrackingGeometryCache.invalidatePlan(model);
            return Result.failure(FaceGeometry.FailureReason.INVALID_FRAME);
        }
        boolean stalePlan = false;
        for (GeckoRankedHandle ranked : plan.rankedHandles()) {
            GeckoGeometryHandle handle = ranked.handle();
            if (!GeckoGeometryTransforms.isVisible(
                    handle.ownerHierarchy(),
                    handle.owner()
            )) {
                continue;
            }
            List<Vec3d> facePositions =
                    GeckoGeometryCapture.captureFacePositions(
                            handle,
                            basePose
                    );
            if (facePositions == null) {
                stalePlan = true;
                continue;
            }
            MaidFacePlane plane = MaidFacePlane
                    .fromVertices(facePositions, frame)
                    .orElse(null);
            if (plane != null) {
                return Result.success(
                        plane,
                        ranked.confidence(),
                        new FaceGeometry.Key(
                                FaceGeometry.Source.GECKO,
                                handle.path(),
                                handle.cubeIndex(),
                                handle.faceIndex()
                        )
                );
            }
        }
        if (stalePlan) {
            FaceTrackingGeometryCache.invalidatePlan(model);
        }
        return Result.failure(FaceGeometry.FailureReason.NO_GEOMETRY);
    }

    public record Result(
            Optional<MaidFacePlane> plane,
            double confidence,
            FaceGeometry.Key key,
            FaceGeometry.FailureReason failureReason
    ) {
        private static Result success(
                MaidFacePlane plane,
                double confidence,
                FaceGeometry.Key key
        ) {
            return new Result(Optional.of(plane), confidence, key, null);
        }

        private static Result failure(FaceGeometry.FailureReason reason) {
            return new Result(Optional.empty(), 0.0D, null, reason);
        }
    }
}
