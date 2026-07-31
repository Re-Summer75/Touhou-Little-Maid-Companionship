package com.laixia.maidintelligence.feature.interaction.application;

import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.List;

public final class FaceCandidateSelector implements FaceSelectionApi {
    @Override
    public FaceGeometry.Selection select(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    ) {
        return FaceSelectionOrchestrator.select(candidates, frame);
    }

    @Override
    public FaceGeometry.Selection selectPrioritizingSemanticSurface(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    ) {
        return FaceSelectionOrchestrator.selectPrioritizingSemanticSurface(
                candidates,
                frame
        );
    }
}
