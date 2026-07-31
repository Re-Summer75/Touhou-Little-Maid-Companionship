package com.laixia.maidintelligence.feature.interaction.application;

import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class FaceCandidateScoring {
    private FaceCandidateScoring() {
    }

    static ScoredCandidate score(
            FaceCandidateMeasurement.MeasuredCandidate candidate,
            FaceCandidateMeasurement.MeasuredCandidate reference,
            List<FaceCandidateMeasurement.MeasuredCandidate> all
    ) {
        FaceGeometry.Candidate geometry = candidate.candidate();
        FaceGeometry.Candidate referenceGeometry =
                reference.candidate();
        double widthRatio = candidate.quad().width()
                / reference.quad().width();
        double heightRatio = candidate.quad().height()
                / reference.quad().height();
        double sizeScore = logGaussian(widthRatio, 1.7D)
                * logGaussian(heightRatio, 1.7D);

        double referenceDiagonal = Math.sqrt(
                reference.quad().width()
                        * reference.quad().width()
                        + reference.quad().height()
                        * reference.quad().height()
        );
        Vec3d centerOffset = candidate.quad().center()
                .subtract(reference.quad().center());
        double centerScore = Math.exp(
                -square(centerOffset.length()
                        / Math.max(
                                referenceDiagonal * 0.45D,
                                1.0E-5D
                        ))
        );
        double depthOffset = Math.abs(
                centerOffset.dot(reference.quad().normal())
        );
        double depthScore = Math.exp(
                -square(depthOffset
                        / Math.max(
                                referenceGeometry.groupDepth()
                                        * 0.30D,
                                1.0E-5D
                        ))
        );
        double aspectScore = Math.min(
                candidate.quad().width(),
                candidate.quad().height()
        ) / Math.max(
                candidate.quad().width(),
                candidate.quad().height()
        );
        double facingScore = clamp(
                (candidate.facing() - FaceCandidateMeasurement.MIN_FACING)
                        / (1.0D - FaceCandidateMeasurement.MIN_FACING)
        );

        double score = 0.30D * facingScore
                + 0.14D * candidate.axisAlignment()
                + 0.20D * sizeScore
                + 0.12D * centerScore
                + 0.10D * depthScore
                + 0.10D * aspectScore
                + roleCandidateBias(geometry.role());
        if (geometry.thin()
                && geometry.role()
                != FaceBoneClassifier.Role.FACE
                && geometry.role()
                != FaceBoneClassifier.Role.BLINK) {
            score -= 0.15D;
        }
        if (FaceCandidateMeasurement.isOuterShell(candidate, all)) {
            score -= 0.18D;
        }
        return new ScoredCandidate(candidate, clamp(score));
    }

    static List<ScoredCandidate> deduplicate(
            List<ScoredCandidate> scored
    ) {
        List<ScoredCandidate> distinct = new ArrayList<>();
        for (ScoredCandidate candidate : scored) {
            boolean duplicate = false;
            for (ScoredCandidate existing : distinct) {
                if (geometricallyEquivalent(
                        candidate.measured().quad(),
                        existing.measured().quad()
                )) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                distinct.add(candidate);
            }
        }
        return distinct;
    }

    private static boolean geometricallyEquivalent(
            FaceGeometry.OrderedQuad first,
            FaceGeometry.OrderedQuad second
    ) {
        double scale = Math.max(
                Math.max(first.width(), first.height()),
                Math.max(second.width(), second.height())
        );
        double mergeDistance = scale * 0.03D;
        return first.center().distanceToSqr(second.center())
                <= mergeDistance * mergeDistance
                && relativeDifference(
                        first.width(),
                        second.width()
                ) <= 0.05D
                && relativeDifference(
                        first.height(),
                        second.height()
                ) <= 0.05D
                && first.normal().dot(second.normal()) >= 0.98D;
    }

    private static double roleCandidateBias(
            FaceBoneClassifier.Role role
    ) {
        return switch (role) {
            case FACE -> 0.08D;
            case HEAD -> 0.06D;
            case BLINK -> 0.05D;
            case NEUTRAL -> 0.01D;
            case FEATURE -> -0.18D;
            case EXCLUDED -> -1.0D;
        };
    }

    private static double logGaussian(
            double value,
            double spread
    ) {
        if (!FaceCandidateMeasurement.positiveFinite(value)) {
            return 0.0D;
        }
        return Math.exp(
                -0.5D * square(
                        Math.log(value) / Math.log(spread)
                )
        );
    }

    private static double relativeDifference(
            double first,
            double second
    ) {
        return Math.abs(first - second)
                / Math.max(
                        Math.max(first, second),
                        1.0E-5D
                );
    }

    private static double square(double value) {
        return value * value;
    }

    static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    record ScoredCandidate(
            FaceCandidateMeasurement.MeasuredCandidate measured,
            double score
    ) {
    }
}
