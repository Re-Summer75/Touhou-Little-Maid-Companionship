package com.laixia.maidintelligence.feature.interaction.client;

import com.laixia.maidintelligence.feature.interaction.client.verification.FaceTrackingScenarioSuite;

public final class FaceTrackingVerification {
    private FaceTrackingVerification() {
    }

    public static void main(String[] args) {
        FaceTrackingScenarioSuite.verifyAll();
        System.out.println("Face tracking verification passed.");
    }
}
