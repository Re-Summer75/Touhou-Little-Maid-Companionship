package com.laixia.maidintelligence.feature.interaction.client.capture;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class YsmFaceCandidateCollector {
    private YsmFaceCandidateCollector() {
    }

    static List<FaceGeometry.Candidate> collect(
            YsmCaptureSession session,
            EntityMaid maid
    ) {
        FaceGeometry.Frame frame = session.frame();
        double entityHeight = Math.max(maid.getBbHeight(), 1.0D);
        double horizontalLimit = entityHeight * 0.55D;
        double forwardLimit = entityHeight * 0.55D;
        double minimumUp = -entityHeight * 0.18D;
        double maximumUp = entityHeight * 0.65D;
        Vec3d origin = frame.origin();
        Vec3d right = frame.right();
        Vec3d up = frame.up();
        Vec3d forward = frame.forward();

        List<FaceGeometry.Candidate> candidates = new ArrayList<>();
        List<YsmCapturedStream> streams = session.streams();
        for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
            YsmCapturedStream stream = streams.get(streamIndex);
            double[] positions = stream.positions();
            float[] normals = stream.normals();
            int quadCount = stream.vertexCount() / 4;
            for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
                int base = quadIndex * 12;
                // The head-region test only needs the quad centroid, which is
                // permutation-independent, so it can run on the raw capture
                // buffer before any quad ordering or vector boxing happens.
                double centerX = (positions[base]
                        + positions[base + 3]
                        + positions[base + 6]
                        + positions[base + 9]) * 0.25D;
                double centerY = (positions[base + 1]
                        + positions[base + 4]
                        + positions[base + 7]
                        + positions[base + 10]) * 0.25D;
                double centerZ = (positions[base + 2]
                        + positions[base + 5]
                        + positions[base + 8]
                        + positions[base + 11]) * 0.25D;
                double offsetX = centerX - origin.x;
                double offsetY = centerY - origin.y;
                double offsetZ = centerZ - origin.z;
                double horizontal = Math.abs(
                        offsetX * right.x + offsetY * right.y + offsetZ * right.z
                );
                double vertical = offsetX * up.x + offsetY * up.y + offsetZ * up.z;
                double forwardDistance = Math.abs(
                        offsetX * forward.x + offsetY * forward.y + offsetZ * forward.z
                );
                if (horizontal > horizontalLimit
                        || forwardDistance > forwardLimit
                        || vertical < minimumUp
                        || vertical > maximumUp) {
                    continue;
                }

                double normalSumX = (double) normals[base]
                        + normals[base + 3]
                        + normals[base + 6]
                        + normals[base + 9];
                double normalSumY = (double) normals[base + 1]
                        + normals[base + 4]
                        + normals[base + 7]
                        + normals[base + 10];
                double normalSumZ = (double) normals[base + 2]
                        + normals[base + 5]
                        + normals[base + 8]
                        + normals[base + 11];
                double normalLengthSqr = normalSumX * normalSumX
                        + normalSumY * normalSumY
                        + normalSumZ * normalSumZ;
                if (normalLengthSqr <= 1.0E-10D) {
                    continue;
                }

                List<Vec3d> quadPositions = List.of(
                        new Vec3d(
                                positions[base],
                                positions[base + 1],
                                positions[base + 2]
                        ),
                        new Vec3d(
                                positions[base + 3],
                                positions[base + 4],
                                positions[base + 5]
                        ),
                        new Vec3d(
                                positions[base + 6],
                                positions[base + 7],
                                positions[base + 8]
                        ),
                        new Vec3d(
                                positions[base + 9],
                                positions[base + 10],
                                positions[base + 11]
                        )
                );
                FaceGeometry.OrderedQuad quad = FaceGeometry
                        .orderQuad(quadPositions, frame)
                        .orElse(null);
                if (quad == null) {
                    continue;
                }

                Vec3d outward = new Vec3d(
                        normalSumX,
                        normalSumY,
                        normalSumZ
                )
                        .normalize();
                double estimatedDepth = Math.max(
                        Math.min(quad.width(), quad.height()),
                        1.0E-4D
                );
                Vec3d groupCenter = quad.center().subtract(
                        outward.normalize().scale(estimatedDepth * 0.5D)
                );
                candidates.add(new FaceGeometry.Candidate(
                        new FaceGeometry.Key(
                                FaceGeometry.Source.YSM,
                                "ysm/stream_" + streamIndex,
                                quadIndex,
                                0
                        ),
                        FaceBoneClassifier.Role.HEAD,
                        quadPositions,
                        outward,
                        groupCenter,
                        quad.width(),
                        quad.height(),
                        estimatedDepth,
                        false,
                        quad
                ));
            }
        }
        return candidates;
    }
}
