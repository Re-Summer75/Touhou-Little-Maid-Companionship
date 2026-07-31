package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.laixia.maidintelligence.feature.interaction.client.tracking.FaceTrackingGeometryCache;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Optional;

public final class BedrockFaceGeometryAdapter {
    private BedrockFaceGeometryAdapter() {
    }

    public static Result resolve(
            BedrockModel<Mob> model,
            PoseStack basePose,
            String variantKey
    ) {
        BedrockGeometryPlan plan = FaceTrackingGeometryCache.getOrCompute(
                model,
                variantKey,
                BedrockGeometryPlan.class,
                () -> BedrockGeometryDiscovery.discover(model, basePose)
        );
        if (plan.failureReason() != null) {
            if (plan.failureReason()
                    == FaceGeometry.FailureReason.INVALID_FRAME) {
                FaceTrackingGeometryCache.invalidatePlan(model);
            }
            return Result.failure(plan.failureReason());
        }

        FaceGeometry.Frame frame = BedrockGeometryTransforms.createFrame(
                basePose,
                plan.anchorHierarchy()
        ).orElse(null);
        if (frame == null) {
            FaceTrackingGeometryCache.invalidatePlan(model);
            return Result.failure(FaceGeometry.FailureReason.INVALID_FRAME);
        }
        boolean stalePlan = false;
        for (BedrockRankedHandle ranked : plan.rankedHandles()) {
            BedrockGeometryHandle handle = ranked.handle();
            boolean semanticSurface = handle.role() == FaceBoneClassifier.Role.FACE
                    || handle.role() == FaceBoneClassifier.Role.BLINK;
            if (!BedrockGeometryTransforms.isUsable(
                    handle.ownerHierarchy(),
                    semanticSurface
            )) {
                continue;
            }
            List<Vec3d> facePositions =
                    BedrockGeometryCapture.captureFacePositions(
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
                                FaceGeometry.Source.BEDROCK,
                                handle.path(),
                                handle.cubeIndex(),
                                handle.faceOrdinal()
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
