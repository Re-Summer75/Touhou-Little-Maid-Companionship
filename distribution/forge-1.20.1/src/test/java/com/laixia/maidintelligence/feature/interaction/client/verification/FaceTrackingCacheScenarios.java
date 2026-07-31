package com.laixia.maidintelligence.feature.interaction.client.verification;

import com.laixia.maidintelligence.feature.interaction.client.tracking.FaceTrackingGeometryCache;

final class FaceTrackingCacheScenarios {
    private FaceTrackingCacheScenarios() {
    }

    static void verifiesVariantAwarePlanCache() {
        FaceTrackingGeometryCache.clear();
        Object model = new Object();
        int[] builds = {0};
        String first = FaceTrackingGeometryCache.getOrCompute(
                model,
                "form_a",
                String.class,
                () -> "plan_" + ++builds[0]
        );
        String reused = FaceTrackingGeometryCache.getOrCompute(
                model,
                "form_a",
                String.class,
                () -> "plan_" + ++builds[0]
        );
        String switched = FaceTrackingGeometryCache.getOrCompute(
                model,
                "form_b",
                String.class,
                () -> "plan_" + ++builds[0]
        );
        FaceTrackingFixtures.require(
                first.equals(reused) && !first.equals(switched),
                "Form change reused a stale face geometry plan"
        );
        FaceTrackingGeometryCache.invalidatePlan(model);
        String rebuilt = FaceTrackingGeometryCache.getOrCompute(
                model,
                "form_b",
                String.class,
                () -> "plan_" + ++builds[0]
        );
        FaceTrackingFixtures.require(
                !switched.equals(rebuilt) && builds[0] == 3,
                "Explicit plan invalidation did not rebuild geometry"
        );
        FaceTrackingGeometryCache.clear();
    }
}
