package com.laixia.maidintelligence.feature.interaction.application;

import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FaceCandidateMeasurement {
    static final double MIN_FACING = 0.45D;
    private static final double MIN_AXIS_ALIGNMENT = 0.50D;

    private FaceCandidateMeasurement() {
    }

    static List<MeasuredCandidate> measureAll(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    ) {
        List<MeasuredCandidate> measured = new ArrayList<>();
        for (FaceGeometry.Candidate candidate : candidates) {
            measure(candidate, frame).ifPresent(measured::add);
        }
        return measured;
    }

    private static Optional<MeasuredCandidate> measure(
            FaceGeometry.Candidate candidate,
            FaceGeometry.Frame frame
    ) {
        if (candidate.role() == FaceBoneClassifier.Role.EXCLUDED
                || candidate.vertices().size() != 4
                || !positiveFinite(candidate.groupWidth())
                || !positiveFinite(candidate.groupHeight())
                || !positiveFinite(candidate.groupDepth())) {
            return Optional.empty();
        }

        FaceGeometry.OrderedQuad quad = candidate.orderedQuad();
        if (quad == null) {
            quad = FaceGeometry
                    .orderQuad(candidate.geometry(), frame)
                    .orElse(null);
        }
        if (quad == null) {
            return Optional.empty();
        }

        Vec3d outward = candidate.outward();
        double facing;
        if (outward.lengthSqr() <= 1.0E-10D) {
            if (!candidate.thin()
                    || (candidate.role()
                    != FaceBoneClassifier.Role.FACE
                    && candidate.role()
                    != FaceBoneClassifier.Role.BLINK)) {
                return Optional.empty();
            }
            facing = quad.normal().dot(frame.forward());
        } else {
            facing = outward.normalize().dot(frame.forward());
        }
        if (facing < MIN_FACING) {
            return Optional.empty();
        }

        double rightAlignment = Math.abs(
                quad.rightSpan().normalize().dot(frame.right())
        );
        double upAlignment = Math.abs(
                quad.upSpan().normalize().dot(frame.up())
        );
        if (rightAlignment < MIN_AXIS_ALIGNMENT
                || upAlignment < MIN_AXIS_ALIGNMENT) {
            return Optional.empty();
        }
        return Optional.of(new MeasuredCandidate(
                candidate,
                quad,
                facing,
                (rightAlignment + upAlignment) * 0.5D
        ));
    }

    static MeasuredCandidate chooseReference(
            List<MeasuredCandidate> measured
    ) {
        List<MeasuredCandidate> solid =
                new ArrayList<>(measured.size());
        for (MeasuredCandidate candidate : measured) {
            if (!candidate.candidate().thin()
                    && candidate.candidate().role()
                    != FaceBoneClassifier.Role.FEATURE) {
                solid.add(candidate);
            }
        }
        if (!solid.isEmpty()) {
            double maximumArea = 0.0D;
            for (MeasuredCandidate candidate : solid) {
                maximumArea = Math.max(
                        maximumArea,
                        candidate.quad().area()
                );
            }
            MeasuredCandidate best = null;
            double bestScore = 0.0D;
            for (MeasuredCandidate candidate : solid) {
                if (candidate.quad().area()
                        < maximumArea * 0.35D) {
                    continue;
                }
                double candidateScore = referenceScore(
                        candidate,
                        solid,
                        maximumArea
                );
                if (best == null || isPreferred(
                        candidateScore,
                        candidate,
                        bestScore,
                        best
                )) {
                    best = candidate;
                    bestScore = candidateScore;
                }
            }
            return best;
        }

        MeasuredCandidate best = null;
        for (MeasuredCandidate candidate : measured) {
            FaceGeometry.Candidate geometry = candidate.candidate();
            if (!geometry.thin()
                    || (geometry.role()
                    != FaceBoneClassifier.Role.FACE
                    && geometry.role()
                    != FaceBoneClassifier.Role.BLINK)) {
                continue;
            }
            if (best == null || isPreferred(
                    candidate.quad().area(),
                    candidate,
                    best.quad().area(),
                    best
            )) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Mirrors Stream.max on a score-then-reversed-key comparator: an incoming
     * candidate replaces the incumbent only when strictly greater.
     */
    private static boolean isPreferred(
            double candidateScore,
            MeasuredCandidate candidate,
            double incumbentScore,
            MeasuredCandidate incumbent
    ) {
        int scoreOrder = Double.compare(
                candidateScore,
                incumbentScore
        );
        if (scoreOrder != 0) {
            return scoreOrder > 0;
        }
        return candidate.candidate().key()
                .compareTo(incumbent.candidate().key()) < 0;
    }

    private static double referenceScore(
            MeasuredCandidate candidate,
            List<MeasuredCandidate> solid,
            double maximumArea
    ) {
        FaceGeometry.Candidate geometry = candidate.candidate();
        double minimumAxis = Math.min(
                geometry.groupWidth(),
                Math.min(
                        geometry.groupHeight(),
                        geometry.groupDepth()
                )
        );
        double maximumAxis = Math.max(
                geometry.groupWidth(),
                Math.max(
                        geometry.groupHeight(),
                        geometry.groupDepth()
                )
        );
        double cubeQuality = minimumAxis / maximumAxis;
        double faceAspect = Math.min(
                candidate.quad().width(),
                candidate.quad().height()
        ) / Math.max(
                candidate.quad().width(),
                candidate.quad().height()
        );
        double areaCoverage = Math.sqrt(
                candidate.quad().area() / maximumArea
        );
        double shellPenalty = isOuterShell(candidate, solid)
                ? 0.25D
                : 0.0D;
        return 0.45D * cubeQuality
                + 0.25D * faceAspect
                + 0.10D * areaCoverage
                + roleReferenceBias(geometry.role())
                - shellPenalty;
    }

    static boolean isOuterShell(
            MeasuredCandidate candidate,
            List<MeasuredCandidate> all
    ) {
        FaceGeometry.Candidate outer = candidate.candidate();
        for (MeasuredCandidate otherCandidate : all) {
            if (otherCandidate == candidate) {
                continue;
            }
            FaceGeometry.Candidate inner =
                    otherCandidate.candidate();
            double maximumSize = Math.max(
                    inner.groupWidth(),
                    Math.max(
                            inner.groupHeight(),
                            inner.groupDepth()
                    )
            );
            double contactDistance = maximumSize * 0.18D;
            if (outer.groupCenter().distanceToSqr(
                    inner.groupCenter()
            ) > contactDistance * contactDistance) {
                continue;
            }
            if (largerWithin(
                    outer.groupWidth(),
                    inner.groupWidth()
            ) && largerWithin(
                    outer.groupHeight(),
                    inner.groupHeight()
            ) && largerWithin(
                    outer.groupDepth(),
                    inner.groupDepth()
            )) {
                return true;
            }
        }
        return false;
    }

    private static boolean largerWithin(
            double outer,
            double inner
    ) {
        return outer > inner * 1.015D
                && outer <= inner * 1.35D;
    }

    private static double roleReferenceBias(
            FaceBoneClassifier.Role role
    ) {
        return switch (role) {
            case FACE -> 0.20D;
            case HEAD -> 0.18D;
            case BLINK -> 0.12D;
            case NEUTRAL -> 0.06D;
            case FEATURE -> -0.20D;
            case EXCLUDED -> -1.0D;
        };
    }

    static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 1.0E-5D;
    }

    record MeasuredCandidate(
            FaceGeometry.Candidate candidate,
            FaceGeometry.OrderedQuad quad,
            double facing,
            double axisAlignment
    ) {
    }
}
