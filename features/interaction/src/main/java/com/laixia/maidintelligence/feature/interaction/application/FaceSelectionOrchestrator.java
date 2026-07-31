package com.laixia.maidintelligence.feature.interaction.application;

import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class FaceSelectionOrchestrator {
    private static final double MIN_SCORE = 0.68D;
    private static final double MIN_CONFIDENCE = 0.62D;

    private FaceSelectionOrchestrator() {
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

        List<FaceCandidateMeasurement.MeasuredCandidate> measured =
                FaceCandidateMeasurement.measureAll(candidates, frame);
        if (measured.isEmpty()) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.NO_GEOMETRY
            );
        }

        FaceCandidateMeasurement.MeasuredCandidate reference =
                FaceCandidateMeasurement.chooseReference(measured);
        if (reference == null) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        List<FaceCandidateScoring.ScoredCandidate> scored =
                new ArrayList<>(measured.size());
        for (FaceCandidateMeasurement.MeasuredCandidate candidate : measured) {
            scored.add(FaceCandidateScoring.score(
                    candidate,
                    reference,
                    measured
            ));
        }
        scored.sort(Comparator
                .comparingDouble(FaceCandidateScoring.ScoredCandidate::score)
                .reversed()
                .thenComparing(candidate ->
                        candidate.measured().candidate().key()));
        List<FaceCandidateScoring.ScoredCandidate> distinct =
                FaceCandidateScoring.deduplicate(scored);
        if (distinct.isEmpty()) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        double bestScore = distinct.get(0).score();
        double secondScore = distinct.size() > 1
                ? distinct.get(1).score()
                : 0.0D;
        double margin = bestScore - secondScore;
        double bestConfidence = bestScore * (
                0.55D + 0.45D * FaceCandidateScoring.clamp(
                        margin / 0.12D
                )
        );
        if (bestScore < MIN_SCORE
                || bestConfidence < MIN_CONFIDENCE) {
            return new FaceGeometry.Selection(
                    List.of(),
                    FaceGeometry.FailureReason.LOW_CONFIDENCE
            );
        }

        List<FaceGeometry.RankedCandidate> ranked =
                new ArrayList<>();
        for (int index = 0; index < distinct.size(); index++) {
            FaceCandidateScoring.ScoredCandidate current =
                    distinct.get(index);
            if (current.score() < MIN_SCORE - 0.08D) {
                continue;
            }
            double nextScore = index + 1 < distinct.size()
                    ? distinct.get(index + 1).score()
                    : 0.0D;
            double confidence = current.score() * (
                    0.55D + 0.45D * FaceCandidateScoring.clamp(
                            (current.score() - nextScore) / 0.12D
                    )
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
        List<FaceGeometry.Candidate> semanticCandidates =
                candidates.stream()
                        .filter(candidate ->
                                candidate.role()
                                        == FaceBoneClassifier.Role.FACE
                                || candidate.role()
                                        == FaceBoneClassifier.Role.BLINK)
                        .toList();
        if (semanticCandidates.isEmpty()) {
            return select(candidates, frame);
        }

        FaceGeometry.Selection semanticSelection =
                select(semanticCandidates, frame);
        if (!semanticSelection.isAccepted()) {
            return select(candidates, frame);
        }

        // Keep geometric head candidates as runtime fallbacks when an animated
        // face or blink plane is temporarily hidden or collapsed.
        FaceGeometry.Selection fallbackSelection =
                select(candidates, frame);
        if (!fallbackSelection.isAccepted()) {
            return semanticSelection;
        }

        List<FaceGeometry.RankedCandidate> ranked =
                new ArrayList<>(semanticSelection.ranked());
        for (FaceGeometry.RankedCandidate fallback
                : fallbackSelection.ranked()) {
            boolean alreadyRanked = ranked.stream().anyMatch(
                    candidate -> candidate.candidate().key()
                            .equals(fallback.candidate().key())
            );
            if (!alreadyRanked) {
                ranked.add(fallback);
            }
        }
        return new FaceGeometry.Selection(ranked, null);
    }
}
