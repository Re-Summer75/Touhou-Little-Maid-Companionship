package com.laixia.maidintelligence.feature.interaction.client.verification;

import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class FaceGeometryScenarios {
    private FaceGeometryScenarios() {
    }

    static void verifiesInvalidFramesAreRejected() {
        FaceTrackingFixtures.require(
                FaceGeometry.Frame.tryCreate(
                        Vec3d.ZERO,
                        new Vec3d(1.0D, 0.0D, 0.0D),
                        new Vec3d(0.0D, 1.0D, 0.0D),
                        new Vec3d(0.0D, 0.0D, -1.0D)
                ).isPresent(),
                "Valid face frame was rejected"
        );
        FaceTrackingFixtures.require(
                FaceGeometry.Frame.tryCreate(
                        Vec3d.ZERO,
                        Vec3d.ZERO,
                        new Vec3d(0.0D, 1.0D, 0.0D),
                        new Vec3d(0.0D, 0.0D, -1.0D)
                ).isEmpty(),
                "Zero-length face axis was accepted"
        );
        FaceTrackingFixtures.require(
                FaceGeometry.Frame.tryCreate(
                        Vec3d.ZERO,
                        new Vec3d(Double.NaN, 0.0D, 0.0D),
                        new Vec3d(0.0D, 1.0D, 0.0D),
                        new Vec3d(0.0D, 0.0D, -1.0D)
                ).isEmpty(),
                "Non-finite face axis was accepted"
        );
        FaceTrackingFixtures.require(
                FaceGeometry.Frame.tryCreate(
                        Vec3d.ZERO,
                        new Vec3d(1.0D, 0.0D, 0.0D),
                        new Vec3d(2.0D, 0.0D, 0.0D),
                        new Vec3d(0.0D, 0.0D, -1.0D)
                ).isEmpty(),
                "Singular face basis was accepted"
        );
    }

    static void verifiesBoneAliasesAndExclusions() {
        FaceTrackingFixtures.require(
                FaceBoneClassifier.anchorPriority("Head") == 100,
                "Head alias failed"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.anchorPriority("bipedHead") == 95,
                "Biped head alias failed"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.anchorPriority("head_2") == 70,
                "Numbered head alias failed"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.anchorPriority("headdress") < 0,
                "Headdress was treated as a head anchor"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.classify(
                        "frontHair",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.EXCLUDED,
                "Hair subtree was not excluded"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.classify(
                        "blink",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.BLINK,
                "Blink plane was not classified"
        );
        FaceTrackingFixtures.require(
                FaceBoneClassifier.classify(
                        "_bink",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.BLINK,
                "Legacy bink plane alias was not classified"
        );
    }

    static void verifiesStableQuadOrdering() {
        List<Vec3d> expected = FaceTrackingFixtures.frontQuad(
                1.0D,
                1.0D,
                -0.5D
        );
        List<List<Vec3d>> permutations = new ArrayList<>();
        FaceTrackingFixtures.permute(
                new ArrayList<>(expected),
                0,
                permutations
        );
        for (List<Vec3d> permutation : permutations) {
            FaceGeometry.OrderedQuad ordered = FaceGeometry
                    .orderQuad(permutation, FaceTrackingFixtures.FRAME)
                    .orElseThrow(() -> new AssertionError("Valid quad was rejected"));
            FaceTrackingFixtures.require(
                    ordered.vertices().equals(expected),
                    "Quad ordering changed with input permutation"
            );
        }
    }

    static void verifiesRotatedMirroredOrdering() {
        Vec3d right = new Vec3d(
                1.0D,
                0.0D,
                -1.0D
        ).normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d forward = up.cross(right).normalize();
        FaceGeometry.Frame rotatedFrame = new FaceGeometry.Frame(
                Vec3d.ZERO,
                right,
                up,
                forward
        );
        Vec3d center = forward.scale(0.5D);
        Vec3d leftBottom = center
                .subtract(right.scale(0.5D))
                .subtract(up.scale(0.5D));
        Vec3d rightBottom = leftBottom.add(right);
        Vec3d leftTop = leftBottom.add(up);
        Vec3d rightTop = rightBottom.add(up);
        FaceGeometry.OrderedQuad ordered = FaceGeometry.orderQuad(
                List.of(rightTop, leftTop, rightBottom, leftBottom),
                rotatedFrame
        ).orElseThrow(() -> new AssertionError("Rotated mirrored quad was rejected"));
        FaceTrackingFixtures.require(
                ordered.rightSpan().normalize().dot(right) >= 0.999D
                        && ordered.upSpan().normalize().dot(up) >= 0.999D
                        && ordered.normal().dot(forward) >= 0.999D,
                "Rotated mirrored quad axes were not canonicalized"
        );
    }

    static void verifiesSkewedPlaneCoordinates() {
        Vec3d origin = new Vec3d(-0.53D, -0.10D, -2.0D);
        Vec3d right = new Vec3d(1.0D, 0.0D, 0.0D);
        Vec3d up = new Vec3d(0.30D, 1.0D, 0.0D);
        List<Vec3d> vertices = List.of(
                origin,
                origin.add(right),
                origin.add(right).add(up),
                origin.add(up)
        );
        MaidFacePlane plane = MaidFacePlane
                .fromVertices(vertices, FaceTrackingFixtures.FRAME)
                .orElseThrow(() -> new AssertionError("Skewed face plane was rejected"));
        Vec3d expectedTarget =
                new Vec3d(0.0D, 0.0D, -2.0D);
        FaceTrackingFixtures.require(
                plane.point(
                        MouthTargetRegion.CENTER_U,
                        MouthTargetRegion.CENTER_V
                ).distanceTo(expectedTarget) <= 1.0E-6D,
                "Skewed face point mapping changed"
        );
        MaidFacePlane.TargetHit hit = plane.intersectTarget(
                Vec3d.ZERO,
                new Vec3d(0.0D, 0.0D, -1.0D),
                8.0D
        ).orElseThrow(() -> new AssertionError("Skewed mouth target was missed"));
        FaceTrackingFixtures.require(
                Math.abs(hit.u() - MouthTargetRegion.CENTER_U) <= 1.0E-5F
                        && Math.abs(hit.v() - MouthTargetRegion.CENTER_V) <= 1.0E-5F,
                "Gram coordinate solve returned the wrong mouth UV"
        );
    }
}
