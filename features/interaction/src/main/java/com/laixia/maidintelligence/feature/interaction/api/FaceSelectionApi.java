package com.laixia.maidintelligence.feature.interaction.api;

import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.List;

/**
 * Selects a stable face surface from adapter-captured model geometry.
 */
public interface FaceSelectionApi {
    FaceGeometry.Selection select(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    );

    FaceGeometry.Selection selectPrioritizingSemanticSurface(
            List<FaceGeometry.Candidate> candidates,
            FaceGeometry.Frame frame
    );
}
