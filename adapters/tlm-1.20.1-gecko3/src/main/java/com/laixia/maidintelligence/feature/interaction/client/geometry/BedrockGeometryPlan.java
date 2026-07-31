package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockCube;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.List;

record BedrockGeometryPlan(
        List<BedrockPart> anchorHierarchy,
        List<BedrockRankedHandle> rankedHandles,
        FaceGeometry.FailureReason failureReason
) {
    static BedrockGeometryPlan failure(
            FaceGeometry.FailureReason reason
    ) {
        return new BedrockGeometryPlan(List.of(), List.of(), reason);
    }
}

record BedrockRankedHandle(
        BedrockGeometryHandle handle,
        double confidence
) {
}

record BedrockGeometryHandle(
        BedrockCube cube,
        List<BedrockPart> ownerHierarchy,
        FaceBoneClassifier.Role role,
        String path,
        int cubeIndex,
        int faceOrdinal
) {
}
