package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.List;

record GeckoGeometryPlan(
        List<AnimatedGeoBone> anchorHierarchy,
        List<GeckoRankedHandle> rankedHandles,
        FaceGeometry.FailureReason failureReason
) {
    static GeckoGeometryPlan failure(
            FaceGeometry.FailureReason reason
    ) {
        return new GeckoGeometryPlan(List.of(), List.of(), reason);
    }
}

record GeckoRankedHandle(
        GeckoGeometryHandle handle,
        double confidence
) {
}

record GeckoGeometryHandle(
        AnimatedGeoBone owner,
        List<AnimatedGeoBone> ownerHierarchy,
        FaceBoneClassifier.Role role,
        String path,
        int cubeIndex,
        int faceIndex
) {
}
