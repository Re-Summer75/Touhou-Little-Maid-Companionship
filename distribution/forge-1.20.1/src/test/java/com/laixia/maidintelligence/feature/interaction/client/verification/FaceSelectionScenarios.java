package com.laixia.maidintelligence.feature.interaction.client.verification;

import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class FaceSelectionScenarios {
    private FaceSelectionScenarios() {
    }

    static void verifiesFrontFaceSelection() {
        FaceGeometry.Candidate front = FaceTrackingFixtures.candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate back = FaceTrackingFixtures.candidate(
                "head",
                0,
                3,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, 0.5D),
                new Vec3d(0.0D, 0.0D, 1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );

        FaceGeometry.Selection selection = FaceTrackingFixtures.SELECTOR.select(
                List.of(back, front),
                FaceTrackingFixtures.FRAME
        );
        FaceTrackingFixtures.require(
                selection.isAccepted(),
                "Front face was not accepted"
        );
        FaceTrackingFixtures.require(
                selection.ranked().get(0).candidate().key().faceIndex() == 2,
                "Back face won front-facing selection"
        );
    }

    static void verifiesOuterHairShellPenalty() {
        FaceGeometry.Candidate head = FaceTrackingFixtures.candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate outerShell = FaceTrackingFixtures.candidate(
                "head",
                1,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.15D, 1.15D, -0.575D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.15D,
                1.15D,
                1.15D,
                false
        );

        FaceGeometry.Selection selection = FaceTrackingFixtures.SELECTOR.select(
                List.of(outerShell, head),
                FaceTrackingFixtures.FRAME
        );
        FaceTrackingFixtures.require(
                selection.isAccepted(),
                "Concentric head geometry was rejected"
        );
        FaceTrackingFixtures.require(
                selection.ranked().get(0).candidate().key().cubeIndex() == 0,
                "Inflated outer shell displaced the actual head cube"
        );
    }

    static void verifiesThinFaceFallback() {
        FaceGeometry.Candidate face = FaceTrackingFixtures.candidate(
                "head/face",
                0,
                2,
                FaceBoneClassifier.Role.FACE,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                0.001D,
                true
        );
        FaceGeometry.Selection selection = FaceTrackingFixtures.SELECTOR.select(
                List.of(face),
                FaceTrackingFixtures.FRAME
        );
        FaceTrackingFixtures.require(
                selection.isAccepted(),
                "Explicit thin face plane was rejected"
        );
    }

    static void verifiesSemanticFacePlanePrecedesHeadCube() {
        FaceGeometry.Candidate head = FaceTrackingFixtures.candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate blink = FaceTrackingFixtures.candidate(
                "head/blink",
                0,
                2,
                FaceBoneClassifier.Role.BLINK,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.501D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                0.001D,
                true
        );

        FaceGeometry.Selection selection =
                FaceTrackingFixtures.SELECTOR.selectPrioritizingSemanticSurface(
                        List.of(head, blink),
                        FaceTrackingFixtures.FRAME
                );
        FaceTrackingFixtures.require(
                selection.isAccepted(),
                "Explicit Bedrock face plane was rejected"
        );
        FaceTrackingFixtures.require(
                selection.ranked().get(0).candidate().role()
                        == FaceBoneClassifier.Role.BLINK,
                "Head cube displaced the explicit Bedrock face plane"
        );
        FaceTrackingFixtures.require(
                selection.ranked().stream().anyMatch(candidate ->
                        candidate.candidate().role() == FaceBoneClassifier.Role.HEAD
                ),
                "Head cube fallback was not retained"
        );
    }

    static void verifiesAmbiguousCandidatesAreRejected() {
        FaceGeometry.Candidate first = FaceTrackingFixtures.candidate(
                "head",
                0,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Candidate second = FaceTrackingFixtures.candidate(
                "head",
                1,
                2,
                FaceBoneClassifier.Role.HEAD,
                FaceTrackingFixtures.translate(
                        FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                        0.04D,
                        0.0D,
                        0.0D
                ),
                new Vec3d(0.0D, 0.0D, -1.0D),
                1.0D,
                1.0D,
                1.0D,
                false
        );
        FaceGeometry.Selection selection = FaceTrackingFixtures.SELECTOR.select(
                List.of(first, second),
                FaceTrackingFixtures.FRAME
        );
        FaceTrackingFixtures.require(
                !selection.isAccepted()
                        && selection.failureReason()
                        == FaceGeometry.FailureReason.LOW_CONFIDENCE,
                "Geometrically ambiguous candidates were accepted"
        );
    }

    static void verifiesPrecomputedQuadSelectionMatches() {
        List<FaceGeometry.Candidate> plain = List.of(
                FaceTrackingFixtures.candidate(
                        "head",
                        0,
                        3,
                        FaceBoneClassifier.Role.HEAD,
                        FaceTrackingFixtures.frontQuad(1.0D, 1.0D, 0.5D),
                        new Vec3d(0.0D, 0.0D, 1.0D),
                        1.0D,
                        1.0D,
                        1.0D,
                        false
                ),
                FaceTrackingFixtures.candidate(
                        "head",
                        0,
                        2,
                        FaceBoneClassifier.Role.HEAD,
                        FaceTrackingFixtures.frontQuad(1.0D, 1.0D, -0.5D),
                        new Vec3d(0.0D, 0.0D, -1.0D),
                        1.0D,
                        1.0D,
                        1.0D,
                        false
                ),
                FaceTrackingFixtures.candidate(
                        "head",
                        1,
                        2,
                        FaceBoneClassifier.Role.HEAD,
                        FaceTrackingFixtures.frontQuad(1.15D, 1.15D, -0.575D),
                        new Vec3d(0.0D, 0.0D, -1.0D),
                        1.15D,
                        1.15D,
                        1.15D,
                        false
                )
        );
        FaceTrackingFixtures.require(
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
                    FaceGeometry.orderQuad(
                            candidate.vertices(),
                            FaceTrackingFixtures.FRAME
                    )
                            .orElseThrow(() -> new AssertionError(
                                    "Precomputed quad ordering failed"
                            ))
            ));
        }

        FaceGeometry.Selection expected = FaceTrackingFixtures.SELECTOR.select(
                plain,
                FaceTrackingFixtures.FRAME
        );
        FaceGeometry.Selection actual = FaceTrackingFixtures.SELECTOR.select(
                precomputed,
                FaceTrackingFixtures.FRAME
        );
        FaceTrackingFixtures.require(
                expected.isAccepted() && actual.isAccepted(),
                "Precomputed quad selection acceptance diverged"
        );
        FaceTrackingFixtures.require(
                expected.ranked().size() == actual.ranked().size(),
                "Precomputed quad ranking size diverged"
        );
        for (int index = 0; index < expected.ranked().size(); index++) {
            FaceGeometry.RankedCandidate expectedRanked =
                    expected.ranked().get(index);
            FaceGeometry.RankedCandidate actualRanked =
                    actual.ranked().get(index);
            FaceTrackingFixtures.require(
                    expectedRanked.candidate().key()
                            .equals(actualRanked.candidate().key()),
                    "Precomputed quad ranking order diverged"
            );
            FaceTrackingFixtures.require(
                    expectedRanked.score() == actualRanked.score()
                            && expectedRanked.confidence() == actualRanked.confidence(),
                    "Precomputed quad scores diverged"
            );
            FaceTrackingFixtures.require(
                    expectedRanked.quad().vertices()
                            .equals(actualRanked.quad().vertices()),
                    "Precomputed quad geometry diverged"
            );
        }
    }
}
