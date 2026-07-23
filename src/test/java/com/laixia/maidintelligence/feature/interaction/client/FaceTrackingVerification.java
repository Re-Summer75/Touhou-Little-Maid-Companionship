package com.laixia.maidintelligence.feature.interaction.client;

import com.laixia.maidintelligence.feature.interaction.domain.MouthTargetRegion;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FaceTrackingVerification {
    private static final FaceGeometry.Frame FRAME = new FaceGeometry.Frame(
            Vec3.ZERO,
            new Vec3(1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 0.0D, -1.0D)
    );

    private FaceTrackingVerification() {
    }

    public static void main(String[] args) {
        verifiesBoneAliasesAndExclusions();
        verifiesStableQuadOrdering();
        verifiesFrontFaceSelection();
        verifiesOuterHairShellPenalty();
        verifiesThinFaceFallback();
        verifiesSemanticFacePlanePrecedesHeadCube();
        verifiesAmbiguousCandidatesAreRejected();
        verifiesRotatedMirroredOrdering();
        verifiesSkewedPlaneCoordinates();
        verifiesPrecomputedQuadSelectionMatches();
        System.out.println("Face tracking verification passed.");
    }

    private static void verifiesBoneAliasesAndExclusions() {
        require(FaceBoneClassifier.anchorPriority("Head") == 100, "Head alias failed");
        require(
                FaceBoneClassifier.anchorPriority("bipedHead") == 95,
                "Biped head alias failed"
        );
        require(
                FaceBoneClassifier.anchorPriority("head_2") == 70,
                "Numbered head alias failed"
        );
        require(
                FaceBoneClassifier.anchorPriority("headdress") < 0,
                "Headdress was treated as a head anchor"
        );
        require(
                FaceBoneClassifier.classify(
                        "frontHair",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.EXCLUDED,
                "Hair subtree was not excluded"
        );
        require(
                FaceBoneClassifier.classify(
                        "blink",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.BLINK,
                "Blink plane was not classified"
        );
        require(
                FaceBoneClassifier.classify(
                        "_bink",
                        FaceBoneClassifier.Role.HEAD
                ) == FaceBoneClassifier.Role.BLINK,
                "Legacy bink plane alias was not classified"
        );
    }

    private static void verifiesStableQuadOrdering() {
        List<Vec3> expected = frontQuad(1.0D, 1.0D, -0.5D);
        List<List<Vec3>> permutations = new ArrayList<>();
        permute(new ArrayList<>(expected), 0, permutations);
        for (List<Vec3> permutation : permutations) {
            FaceGeometry.OrderedQuad ordered = FaceGeometry
                    .orderQuad(permutation, FRAME)
                    .orElseThrow(() -> new AssertionError("Valid quad was rejected"));
            require(
                    ordered.vertices().equals(expected),
                    "Quad ordering changed with input permutation"
            );
        }
    }

    private static void verifiesFrontFaceSelection() {
        FaceGeometry.Candidate front = candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate back = candidate(
                "head",
                0,
                3,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.0D, 1.0D, 0.5D),
                new Vec3(0.0D, 0.0D, 1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );

        FaceGeometry.Selection selection = FaceCandidateSelector.select(
                List.of(back, front),
                FRAME
        );
        require(selection.isAccepted(), "Front face was not accepted");
        require(
                selection.ranked().get(0).candidate().key().faceIndex() == 2,
                "Back face won front-facing selection"
        );
    }

    private static void verifiesOuterHairShellPenalty() {
        FaceGeometry.Candidate head = candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate outerShell = candidate(
                "head",
                1,
                2,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.15D, 1.15D, -0.575D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.15D,
                1.15D,
                1.15D,
                false
        );

        FaceGeometry.Selection selection = FaceCandidateSelector.select(
                List.of(outerShell, head),
                FRAME
        );
        require(selection.isAccepted(), "Concentric head geometry was rejected");
        require(
                selection.ranked().get(0).candidate().key().cubeIndex() == 0,
                "Inflated outer shell displaced the actual head cube"
        );
    }

    private static void verifiesThinFaceFallback() {
        FaceGeometry.Candidate face = candidate(
                "head/face",
                0,
                2,
                FaceBoneClassifier.Role.FACE,
                frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                0.001D,
                true
        );
        FaceGeometry.Selection selection = FaceCandidateSelector.select(
                List.of(face),
                FRAME
        );
        require(selection.isAccepted(), "Explicit thin face plane was rejected");
    }

    private static void verifiesSemanticFacePlanePrecedesHeadCube() {
        FaceGeometry.Candidate head = candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate blink = candidate(
                "head/blink",
                0,
                2,
                FaceBoneClassifier.Role.BLINK,
                frontQuad(1.0D, 1.0D, -0.501D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                0.001D,
                true
        );

        FaceGeometry.Selection selection =
                FaceCandidateSelector.selectPrioritizingSemanticSurface(
                        List.of(head, blink),
                        FRAME
                );
        require(selection.isAccepted(), "Explicit Bedrock face plane was rejected");
        require(
                selection.ranked().get(0).candidate().role()
                        == FaceBoneClassifier.Role.BLINK,
                "Head cube displaced the explicit Bedrock face plane"
        );
        require(
                selection.ranked().stream().anyMatch(candidate ->
                        candidate.candidate().role() == FaceBoneClassifier.Role.HEAD
                ),
                "Head cube fallback was not retained"
        );
    }

    private static void verifiesAmbiguousCandidatesAreRejected() {
        FaceGeometry.Candidate first = candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate second = candidate(
                "head",
                1,
                2,
                FaceBoneClassifier.Role.HEAD,
                translate(frontQuad(1.0D, 1.0D, -0.5D), 0.04D, 0.0D, 0.0D),
                new Vec3(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Selection selection = FaceCandidateSelector.select(
                List.of(first, second),
                FRAME
        );
        require(
                !selection.isAccepted()
                        && selection.failureReason()
                        == FaceGeometry.FailureReason.LOW_CONFIDENCE,
                "Geometrically ambiguous candidates were accepted"
        );
    }

    private static void verifiesRotatedMirroredOrdering() {
        Vec3 right = new Vec3(1.0D, 0.0D, -1.0D).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 forward = up.cross(right).normalize();
        FaceGeometry.Frame rotatedFrame = new FaceGeometry.Frame(
                Vec3.ZERO,
                right,
                up,
                forward
        );
        Vec3 center = forward.scale(0.5D);
        Vec3 leftBottom = center.subtract(right.scale(0.5D)).subtract(up.scale(0.5D));
        Vec3 rightBottom = leftBottom.add(right);
        Vec3 leftTop = leftBottom.add(up);
        Vec3 rightTop = rightBottom.add(up);
        FaceGeometry.OrderedQuad ordered = FaceGeometry.orderQuad(
                List.of(rightTop, leftTop, rightBottom, leftBottom),
                rotatedFrame
        ).orElseThrow(() -> new AssertionError("Rotated mirrored quad was rejected"));
        require(
                ordered.rightSpan().normalize().dot(right) >= 0.999D
                        && ordered.upSpan().normalize().dot(up) >= 0.999D
                        && ordered.normal().dot(forward) >= 0.999D,
                "Rotated mirrored quad axes were not canonicalized"
        );
    }

    private static void verifiesSkewedPlaneCoordinates() {
        Vec3 origin = new Vec3(-0.53D, -0.10D, -2.0D);
        Vec3 right = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = new Vec3(0.30D, 1.0D, 0.0D);
        List<Vec3> vertices = List.of(
                origin,
                origin.add(right),
                origin.add(right).add(up),
                origin.add(up)
        );
        MaidFacePlane plane = MaidFacePlane
                .fromVertices(vertices, FRAME)
                .orElseThrow(() -> new AssertionError("Skewed face plane was rejected"));
        Vec3 expectedTarget = new Vec3(0.0D, 0.0D, -2.0D);
        require(
                plane.point(
                        MouthTargetRegion.CENTER_U,
                        MouthTargetRegion.CENTER_V
                ).distanceTo(expectedTarget) <= 1.0E-6D,
                "Skewed face point mapping changed"
        );
        MaidFacePlane.TargetHit hit = plane.intersectTarget(
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -1.0D),
                8.0D
        ).orElseThrow(() -> new AssertionError("Skewed mouth target was missed"));
        require(
                Math.abs(hit.u() - MouthTargetRegion.CENTER_U) <= 1.0E-5F
                        && Math.abs(hit.v() - MouthTargetRegion.CENTER_V) <= 1.0E-5F,
                "Gram coordinate solve returned the wrong mouth UV"
        );
    }

    private static void verifiesPrecomputedQuadSelectionMatches() {
        List<FaceGeometry.Candidate> plain = List.of(
                candidate(
                        "head",
                        0,
                        3,
                        FaceBoneClassifier.Role.HEAD,
                        frontQuad(1.0D, 1.0D, 0.5D),
                        new Vec3(0.0D, 0.0D, 1.0D),
                        1.0D,
                        1.0D,
                        1.0D,
                        false
                ),
                candidate(
                        "head",
                        0,
                        2,
                        FaceBoneClassifier.Role.HEAD,
                        frontQuad(1.0D, 1.0D, -0.5D),
                        new Vec3(0.0D, 0.0D, -1.0D),
                        1.0D,
                        1.0D,
                        1.0D,
                        false
                ),
                candidate(
                        "head",
                        1,
                        2,
                        FaceBoneClassifier.Role.HEAD,
                        frontQuad(1.15D, 1.15D, -0.575D),
                        new Vec3(0.0D, 0.0D, -1.0D),
                        1.15D,
                        1.15D,
                        1.15D,
                        false
                )
        );
        require(
                plain.get(0).orderedQuad() == null,
                "Compat candidate constructor should leave the quad cache empty"
        );

        List<FaceGeometry.Candidate> precomputed = new ArrayList<>();
        for (FaceGeometry.Candidate candidate : plain) {
            precomputed.add(new FaceGeometry.Candidate(
                    candidate.key(),
                    candidate.role(),
                    candidate.vertices(),
                    candidate.outward(),
                    candidate.groupCenter(),
                    candidate.groupWidth(),
                    candidate.groupHeight(),
                    candidate.groupDepth(),
                    candidate.thin(),
                    FaceGeometry.orderQuad(candidate.vertices(), FRAME)
                            .orElseThrow(() -> new AssertionError(
                                    "Precomputed quad ordering failed"
                            ))
            ));
        }

        FaceGeometry.Selection expected = FaceCandidateSelector.select(plain, FRAME);
        FaceGeometry.Selection actual = FaceCandidateSelector.select(
                precomputed,
                FRAME
        );
        require(
                expected.isAccepted() && actual.isAccepted(),
                "Precomputed quad selection acceptance diverged"
        );
        require(
                expected.ranked().size() == actual.ranked().size(),
                "Precomputed quad ranking size diverged"
        );
        for (int index = 0; index < expected.ranked().size(); index++) {
            FaceGeometry.RankedCandidate expectedRanked = expected.ranked().get(index);
            FaceGeometry.RankedCandidate actualRanked = actual.ranked().get(index);
            require(
                    expectedRanked.candidate().key()
                            .equals(actualRanked.candidate().key()),
                    "Precomputed quad ranking order diverged"
            );
            require(
                    expectedRanked.score() == actualRanked.score()
                            && expectedRanked.confidence() == actualRanked.confidence(),
                    "Precomputed quad scores diverged"
            );
            require(
                    expectedRanked.quad().vertices()
                            .equals(actualRanked.quad().vertices()),
                    "Precomputed quad geometry diverged"
            );
        }
    }

    private static FaceGeometry.Candidate candidate(
            String path,
            int cubeIndex,
            int faceIndex,
            FaceBoneClassifier.Role role,
            List<Vec3> vertices,
            Vec3 outward,
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
                Vec3.ZERO,
                width,
                height,
                depth,
                thin
        );
    }

    private static List<Vec3> frontQuad(double width, double height, double z) {
        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        return List.of(
                new Vec3(-halfWidth, -halfHeight, z),
                new Vec3(halfWidth, -halfHeight, z),
                new Vec3(halfWidth, halfHeight, z),
                new Vec3(-halfWidth, halfHeight, z)
        );
    }

    private static List<Vec3> translate(
            List<Vec3> vertices,
            double x,
            double y,
            double z
    ) {
        Vec3 offset = new Vec3(x, y, z);
        return vertices.stream().map(vertex -> vertex.add(offset)).toList();
    }

    private static void permute(
            List<Vec3> values,
            int index,
            List<List<Vec3>> output
    ) {
        if (index == values.size()) {
            output.add(List.copyOf(values));
            return;
        }
        for (int current = index; current < values.size(); current++) {
            java.util.Collections.swap(values, index, current);
            permute(values, index + 1, output);
            java.util.Collections.swap(values, index, current);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
