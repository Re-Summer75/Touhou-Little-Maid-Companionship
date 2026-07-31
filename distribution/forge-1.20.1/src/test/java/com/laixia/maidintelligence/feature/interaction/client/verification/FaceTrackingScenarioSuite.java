package com.laixia.maidintelligence.feature.interaction.client.verification;

public final class FaceTrackingScenarioSuite {
    private FaceTrackingScenarioSuite() {
    }

    public static void verifyAll() {
        FaceGeometryScenarios.verifiesInvalidFramesAreRejected();
        FaceTrackingCacheScenarios.verifiesVariantAwarePlanCache();
        FaceGeometryScenarios.verifiesBoneAliasesAndExclusions();
        FaceGeometryScenarios.verifiesStableQuadOrdering();
        FaceSelectionScenarios.verifiesFrontFaceSelection();
        FaceSelectionScenarios.verifiesOuterHairShellPenalty();
        FaceSelectionScenarios.verifiesThinFaceFallback();
        FaceSelectionScenarios.verifiesSemanticFacePlanePrecedesHeadCube();
        FaceSelectionScenarios.verifiesAmbiguousCandidatesAreRejected();
        FaceGeometryScenarios.verifiesRotatedMirroredOrdering();
        FaceGeometryScenarios.verifiesSkewedPlaneCoordinates();
        FaceSelectionScenarios.verifiesPrecomputedQuadSelectionMatches();
    }
}
