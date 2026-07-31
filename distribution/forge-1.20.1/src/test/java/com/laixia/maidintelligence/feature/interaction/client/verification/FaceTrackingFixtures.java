package com.laixia.maidintelligence.feature.interaction.client.verification;

import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.application.FaceCandidateSelector;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.Collections;
import java.util.List;

final class FaceTrackingFixtures {
    static final FaceSelectionApi SELECTOR =
            new FaceCandidateSelector();
    static final FaceGeometry.Frame FRAME = new FaceGeometry.Frame(
            Vec3d.ZERO,
            new Vec3d(1.0D, 0.0D, 0.0D),
            new Vec3d(0.0D, 1.0D, 0.0D),
            new Vec3d(0.0D, 0.0D, -1.0D)
    );

    private FaceTrackingFixtures() {
    }

    static FaceGeometry.Candidate candidate(
            String path,
            int cubeIndex,
            int faceIndex,
            FaceBoneClassifier.Role role,
            List<Vec3d> vertices,
            Vec3d outward,
            double width,
            double height,
            double depth,
            boolean thin
    ) {
        return new FaceGeometry.Candidate(
                new FaceGeometry.Key(
                        FaceGeometry.Source.GECKO,
                        path,
                        cubeIndex,
                        faceIndex
                ),
                role,
                vertices,
                outward,
                Vec3d.ZERO,
                width,
                height,
                depth,
                thin
        );
    }

    static List<Vec3d> frontQuad(
            double width,
            double height,
            double z
    ) {
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        return List.of(
                new Vec3d(-halfWidth, -halfHeight, z),
                new Vec3d(halfWidth, -halfHeight, z),
                new Vec3d(halfWidth, halfHeight, z),
                new Vec3d(-halfWidth, halfHeight, z)
        );
    }

    static List<Vec3d> translate(
            List<Vec3d> vertices,
            double x,
            double y,
            double z
    ) {
        Vec3d offset = new Vec3d(x, y, z);
        return vertices.stream().map(vertex -> vertex.add(offset)).toList();
    }

    static void permute(
            List<Vec3d> values,
            int index,
            List<List<Vec3d>> output
    ) {
        if (index == values.size()) {
            output.add(List.copyOf(values));
            return;
        }
        for (int current = index; current < values.size(); current++) {
            Collections.swap(values, index, current);
            permute(values, index + 1, output);
            Collections.swap(values, index, current);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
