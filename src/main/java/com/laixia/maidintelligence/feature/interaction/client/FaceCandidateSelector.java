package com.laixia.maidintelligence.feature.interaction.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class FaceCandidateSelector {
    private static final double MIN_FACING = 0.45D;
    private static final double MIN_AXIS_ALIGNMENT = 0.50D;
    private static final double MIN_SCORE = 0.68D;
    private static final double MIN_CONFIDENCE = 0.62D;

    private FaceCandidateSelector() {
    }

    static FaceGeometry.Selection select(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    ) {
        if (candidates.isEmpty()) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.NO_GEOMETRY
            );
        }

        List<MeasuredCandidate> measured = new ArrayList<>();
        for (FaceGeometry.Candidate candidate : candidates) {
            measure(candidate, frame).ifPresent(measured::add);
        }
        if (measured.isEmpty()) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.NO_GEOMETRY
            );
        }

        MeasuredCandidate reference = chooseReference(measured);
        if (reference == null) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        List<ScoredCandidate> scored = measured.stream()
                .map(candidate -> score(candidate, reference, measured))
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(candidate -> candidate.measured().candidate().key()))
                .toList();
        List<ScoredCandidate> distinct = deduplicate(scored);
        if (distinct.isEmpty()) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        double bestScore = distinct.get(0).score();
        double secondScore = distinct.size() > 1 ? distinct.get(1).score() : 0.0D;
        double margin = bestScore - secondScore;
        double bestConfidence = bestScore * (
                0.55D + 0.45D * clamp(margin / 0.12D)
        );
        if (bestScore < MIN_SCORE || bestConfidence < MIN_CONFIDENCE) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        List<FaceGeometry.RankedCandidate> ranked = new ArrayList<>();
        for (int index = 0; index < distinct.size(); index++) {
            ScoredCandidate current = distinct.get(index);
            if (current.score() < MIN_SCORE - 0.08D) {
                continue;
            }
            double nextScore = index + 1 < distinct.size()
                    ? distinct.get(index + 1).score()
                    : 0.0D;
            double confidence = current.score() * (
                    0.55D + 0.45D * clamp((current.score() - nextScore) / 0.12D)
            );
            ranked.add(new FaceGeometry.RankedCandidate(
                    current.measured().candidate(),
                    current.measured().quad(),
                    current.score(),
                    confidence
            ));
        }
        return new FaceGeometry.Selection(ranked, null);
    }

    static FaceGeometry.Selection selectPrioritizingSemanticSurface(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    ) {
        List<FaceGeometry.Candidate> semanticCandidates = candidates.stream()
                .filter(candidate -> candidate.role() == FaceBoneClassifier.Role.FACE
                        || candidate.role() == FaceBoneClassifier.Role.BLINK)
                .toList();
        if (semanticCandidates.isEmpty()) {
            return select(candidates, frame);
        }

        FaceGeometry.Selection semanticSelection = select(semanticCandidates, frame);
        if (!semanticSelection.isAccepted()) {
            return select(candidates, frame);
        }

        // Keep geometric head candidates as runtime fallbacks when an animated
        // face or blink plane is temporarily hidden or collapsed.
        FaceGeometry.Selection fallbackSelection = select(candidates, frame);
        if (!fallbackSelection.isAccepted()) {
            return semanticSelection;
        }

        List<FaceGeometry.RankedCandidate> ranked = new ArrayList<>(
                semanticSelection.ranked()
        );
        for (FaceGeometry.RankedCandidate fallback : fallbackSelection.ranked()) {
            boolean alreadyRanked = ranked.stream().anyMatch(candidate ->
                    candidate.candidate().key().equals(fallback.candidate().key())
            );
            if (!alreadyRanked) {
                ranked.add(fallback);
            }
        }
        return new FaceGeometry.Selection(ranked, null);
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

        FaceGeometry.OrderedQuad quad = FaceGeometry
                .orderQuad(candidate.vertices(), frame)
                .orElse(null);
        if (quad == null) {
            return Optional.empty();
        }

        Vec3 outward = candidate.outward();
        double facing;
        if (outward.lengthSqr() <= 1.0E-10D) {
            if (!candidate.thin()
                    || (candidate.role() != FaceBoneClassifier.Role.FACE
                    && candidate.role() != FaceBoneClassifier.Role.BLINK)) {
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

    private static MeasuredCandidate chooseReference(List<MeasuredCandidate> measured) {
        List<MeasuredCandidate> solid = measured.stream()
                .filter(candidate -> !candidate.candidate().thin())
                .filter(candidate -> candidate.candidate().role()
                        != FaceBoneClassifier.Role.FEATURE)
                .toList();
        if (!solid.isEmpty()) {
            double maximumArea = solid.stream()
                    .mapToDouble(candidate -> candidate.quad().area())
                    .max()
                    .orElse(0.0D);
            return solid.stream()
                    .filter(candidate -> candidate.quad().area() >= maximumArea * 0.35D)
                    .max(Comparator
                            .comparingDouble((MeasuredCandidate candidate) -> referenceScore(
                                    candidate,
                                    solid,
                                    maximumArea
                            ))
                            .thenComparing(
                                    candidate -> candidate.candidate().key(),
                                    Comparator.reverseOrder()
                            ))
                    .orElse(null);
        }

        return measured.stream()
                .filter(candidate -> candidate.candidate().thin())
                .filter(candidate -> candidate.candidate().role()
                        == FaceBoneClassifier.Role.FACE
                        || candidate.candidate().role()
                        == FaceBoneClassifier.Role.BLINK)
                .max(Comparator
                        .comparingDouble((MeasuredCandidate candidate) ->
                                candidate.quad().area())
                        .thenComparing(
                                candidate -> candidate.candidate().key(),
                                Comparator.reverseOrder()
                        ))
                .orElse(null);
    }

    private static double referenceScore(
            MeasuredCandidate candidate,
            List<MeasuredCandidate> solid,
            double maximumArea
    ) {
        FaceGeometry.Candidate geometry = candidate.candidate();
        double minimumAxis = Math.min(
                geometry.groupWidth(),
                Math.min(geometry.groupHeight(), geometry.groupDepth())
        );
        double maximumAxis = Math.max(
                geometry.groupWidth(),
                Math.max(geometry.groupHeight(), geometry.groupDepth())
        );
        double cubeQuality = minimumAxis / maximumAxis;
        double faceAspect = Math.min(
                candidate.quad().width(),
                candidate.quad().height()
        ) / Math.max(candidate.quad().width(), candidate.quad().height());
        double areaCoverage = Math.sqrt(candidate.quad().area() / maximumArea);
        double shellPenalty = isOuterShell(candidate, solid) ? 0.25D : 0.0D;
        return 0.45D * cubeQuality
                + 0.25D * faceAspect
                + 0.10D * areaCoverage
                + roleReferenceBias(geometry.role())
                - shellPenalty;
    }

    private static boolean isOuterShell(
            MeasuredCandidate candidate,
            List<MeasuredCandidate> all
    ) {
        FaceGeometry.Candidate outer = candidate.candidate();
        for (MeasuredCandidate otherCandidate : all) {
            if (otherCandidate == candidate) {
                continue;
            }
            FaceGeometry.Candidate inner = otherCandidate.candidate();
            double maximumSize = Math.max(
                    inner.groupWidth(),
                    Math.max(inner.groupHeight(), inner.groupDepth())
            );
            if (outer.groupCenter().distanceTo(inner.groupCenter()) > maximumSize * 0.18D) {
                continue;
            }
            if (largerWithin(outer.groupWidth(), inner.groupWidth())
                    && largerWithin(outer.groupHeight(), inner.groupHeight())
                    && largerWithin(outer.groupDepth(), inner.groupDepth())) {
                return true;
            }
        }
        return false;
    }

    private static boolean largerWithin(double outer, double inner) {
        return outer > inner * 1.015D && outer <= inner * 1.35D;
    }

    private static ScoredCandidate score(
            MeasuredCandidate candidate,
            MeasuredCandidate reference,
            List<MeasuredCandidate> all
    ) {
        FaceGeometry.Candidate geometry = candidate.candidate();
        FaceGeometry.Candidate referenceGeometry = reference.candidate();
        double widthRatio = candidate.quad().width() / reference.quad().width();
        double heightRatio = candidate.quad().height() / reference.quad().height();
        double sizeScore = logGaussian(widthRatio, 1.7D)
                * logGaussian(heightRatio, 1.7D);

        double referenceDiagonal = Math.sqrt(
                reference.quad().width() * reference.quad().width()
                        + reference.quad().height() * reference.quad().height()
        );
        Vec3 centerOffset = candidate.quad().center()
                .subtract(reference.quad().center());
        double centerScore = Math.exp(
                -square(centerOffset.length() / Math.max(referenceDiagonal * 0.45D, 1.0E-5D))
        );
        double depthOffset = Math.abs(centerOffset.dot(reference.quad().normal()));
        double depthScore = Math.exp(
                -square(depthOffset / Math.max(referenceGeometry.groupDepth() * 0.30D, 1.0E-5D))
        );
        double aspectScore = Math.min(
                candidate.quad().width(),
                candidate.quad().height()
        ) / Math.max(candidate.quad().width(), candidate.quad().height());
        double facingScore = clamp(
                (candidate.facing() - MIN_FACING) / (1.0D - MIN_FACING)
        );

        double score = 0.30D * facingScore
                + 0.14D * candidate.axisAlignment()
                + 0.20D * sizeScore
                + 0.12D * centerScore
                + 0.10D * depthScore
                + 0.10D * aspectScore
                + roleCandidateBias(geometry.role());
        if (geometry.thin()
                && geometry.role() != FaceBoneClassifier.Role.FACE
                && geometry.role() != FaceBoneClassifier.Role.BLINK) {
            score -= 0.15D;
        }
        if (isOuterShell(candidate, all)) {
            score -= 0.18D;
        }
        return new ScoredCandidate(candidate, clamp(score));
    }

    private static List<ScoredCandidate> deduplicate(List<ScoredCandidate> scored) {
        List<ScoredCandidate> distinct = new ArrayList<>();
        for (ScoredCandidate candidate : scored) {
            boolean duplicate = distinct.stream().anyMatch(existing ->
                    geometricallyEquivalent(
                            candidate.measured().quad(),
                            existing.measured().quad()
                    )
            );
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
        return first.center().distanceTo(second.center()) <= scale * 0.03D
                && relativeDifference(first.width(), second.width()) <= 0.05D
                && relativeDifference(first.height(), second.height()) <= 0.05D
                && first.normal().dot(second.normal()) >= 0.98D;
    }

    private static double roleReferenceBias(FaceBoneClassifier.Role role) {
        return switch (role) {
            case FACE -> 0.20D;
            case HEAD -> 0.18D;
            case BLINK -> 0.12D;
            case NEUTRAL -> 0.06D;
            case FEATURE -> -0.20D;
            case EXCLUDED -> -1.0D;
        };
    }

    private static double roleCandidateBias(FaceBoneClassifier.Role role) {
        return switch (role) {
            case FACE -> 0.08D;
            case HEAD -> 0.06D;
            case BLINK -> 0.05D;
            case NEUTRAL -> 0.01D;
            case FEATURE -> -0.18D;
            case EXCLUDED -> -1.0D;
        };
    }

    private static double logGaussian(double value, double spread) {
        if (!positiveFinite(value)) {
            return 0.0D;
        }
        return Math.exp(-0.5D * square(Math.log(value) / Math.log(spread)));
    }

    private static double relativeDifference(double first, double second) {
        return Math.abs(first - second) / Math.max(Math.max(first, second), 1.0E-5D);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 1.0E-5D;
    }

    private record MeasuredCandidate(
            FaceGeometry.Candidate candidate,
            FaceGeometry.OrderedQuad quad,
            double facing,
            double axisAlignment
    ) {
    }

    private record ScoredCandidate(
            MeasuredCandidate measured,
            double score
    ) {
    }
}
