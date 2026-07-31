package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockCube;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.application.FaceCandidateSelector;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BedrockGeometryDiscovery {
    private static final FaceSelectionApi FACE_SELECTOR =
            new FaceCandidateSelector();

    private BedrockGeometryDiscovery() {
    }

    static BedrockGeometryPlan discover(
            BedrockModel<Mob> model,
            PoseStack basePose
    ) {
        IdentityHashMap<BedrockPart, String> names = new IdentityHashMap<>();
        model.getModelMap().forEach((name, part) -> names.put(part, name));
        List<Map.Entry<String, BedrockPart>> anchors = model.getModelMap()
                .entrySet()
                .stream()
                .filter(entry -> FaceBoneClassifier.anchorPriority(entry.getKey()) >= 0)
                .sorted(Comparator
                        .<Map.Entry<String, BedrockPart>>comparingInt(
                                entry -> FaceBoneClassifier.anchorPriority(entry.getKey())
                        )
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        if (anchors.isEmpty()) {
            return BedrockGeometryPlan.failure(
                    FaceGeometry.FailureReason.NO_HEAD_ANCHOR
            );
        }

        FaceGeometry.FailureReason lastFailure =
                FaceGeometry.FailureReason.NO_GEOMETRY;
        for (Map.Entry<String, BedrockPart> anchorEntry : anchors) {
            BedrockPart anchor = anchorEntry.getValue();
            List<BedrockPart> anchorHierarchy =
                    BedrockGeometryTransforms.hierarchy(anchor);
            FaceGeometry.Frame frame = BedrockGeometryTransforms.createFrame(
                    basePose,
                    anchorHierarchy
            ).orElse(null);
            if (frame == null) {
                lastFailure = FaceGeometry.FailureReason.INVALID_FRAME;
                continue;
            }
            Map<FaceGeometry.Key, BedrockGeometryHandle> handles =
                    new LinkedHashMap<>();
            List<FaceGeometry.Candidate> candidates = new ArrayList<>();
            FaceBoneClassifier.Role anchorRole = FaceBoneClassifier.classify(
                    anchorEntry.getKey(),
                    FaceBoneClassifier.Role.HEAD
            );
            collect(
                    anchor,
                    anchorEntry.getKey(),
                    anchorRole,
                    anchorHierarchy,
                    names,
                    basePose,
                    frame,
                    handles,
                    candidates
            );

            FaceGeometry.Selection selection =
                    FACE_SELECTOR.selectPrioritizingSemanticSurface(
                            candidates,
                            frame
                    );
            if (!selection.isAccepted()) {
                lastFailure = selection.failureReason();
                continue;
            }
            List<BedrockRankedHandle> rankedHandles = selection.ranked()
                    .stream()
                    .map(ranked -> new BedrockRankedHandle(
                            handles.get(ranked.candidate().key()),
                            ranked.confidence()
                    ))
                    .filter(ranked -> ranked.handle() != null)
                    .toList();
            if (!rankedHandles.isEmpty()) {
                return new BedrockGeometryPlan(
                        anchorHierarchy,
                        rankedHandles,
                        null
                );
            }
        }
        return BedrockGeometryPlan.failure(lastFailure);
    }

    private static void collect(
            BedrockPart part,
            String path,
            FaceBoneClassifier.Role role,
            List<BedrockPart> ownerHierarchy,
            IdentityHashMap<BedrockPart, String> names,
            PoseStack basePose,
            FaceGeometry.Frame frame,
            Map<FaceGeometry.Key, BedrockGeometryHandle> handles,
            List<FaceGeometry.Candidate> candidates
    ) {
        if (role == FaceBoneClassifier.Role.EXCLUDED) {
            return;
        }

        PoseStack ownerPose = BedrockGeometryTransforms.poseFor(
                basePose,
                ownerHierarchy
        );
        for (int cubeIndex = 0; cubeIndex < part.cubes.size(); cubeIndex++) {
            BedrockCube cube = part.cubes.get(cubeIndex);
            List<FaceGeometry.Candidate> cubeCandidates =
                    BedrockGeometryCapture.captureCube(
                            cube,
                            path,
                            role,
                            cubeIndex,
                            ownerPose,
                            frame
                    );
            for (FaceGeometry.Candidate candidate : cubeCandidates) {
                BedrockGeometryHandle handle = new BedrockGeometryHandle(
                        cube,
                        ownerHierarchy,
                        role,
                        path,
                        cubeIndex,
                        candidate.key().faceIndex()
                );
                handles.put(candidate.key(), handle);
                candidates.add(candidate);
            }
        }

        for (int childIndex = 0; childIndex < part.children.size(); childIndex++) {
            BedrockPart child = part.children.get(childIndex);
            String childName = names.get(child);
            String pathPart = childName == null ? "#" + childIndex : childName;
            FaceBoneClassifier.Role childRole = FaceBoneClassifier.classify(
                    childName,
                    role
            );
            if (childRole == FaceBoneClassifier.Role.EXCLUDED) {
                continue;
            }
            List<BedrockPart> childHierarchy = new ArrayList<>(ownerHierarchy);
            childHierarchy.add(child);
            collect(
                    child,
                    path + "/" + pathPart,
                    childRole,
                    List.copyOf(childHierarchy),
                    names,
                    basePose,
                    frame,
                    handles,
                    candidates
            );
        }
    }
}
