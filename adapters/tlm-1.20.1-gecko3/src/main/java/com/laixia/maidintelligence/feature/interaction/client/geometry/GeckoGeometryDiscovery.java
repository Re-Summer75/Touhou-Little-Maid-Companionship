package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.application.FaceCandidateSelector;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeckoGeometryDiscovery {
    private static final FaceSelectionApi FACE_SELECTOR =
            new FaceCandidateSelector();

    private GeckoGeometryDiscovery() {
    }

    static GeckoGeometryPlan discover(
            AnimatedGeoModel model,
            PoseStack basePose
    ) {
        List<AnimatedGeoBone> anchors = model.bones()
                .values()
                .stream()
                .filter(bone -> FaceBoneClassifier.anchorPriority(bone.getName()) >= 0)
                .sorted(Comparator
                        .comparingInt((AnimatedGeoBone bone) ->
                                FaceBoneClassifier.anchorPriority(bone.getName()))
                        .reversed()
                        .thenComparing(AnimatedGeoBone::getName))
                .toList();
        if (anchors.isEmpty()) {
            return GeckoGeometryPlan.failure(
                    FaceGeometry.FailureReason.NO_HEAD_ANCHOR
            );
        }

        FaceGeometry.FailureReason lastFailure =
                FaceGeometry.FailureReason.NO_GEOMETRY;
        for (AnimatedGeoBone anchor : anchors) {
            List<AnimatedGeoBone> anchorHierarchy =
                    GeckoGeometryTransforms.hierarchy(model, anchor);
            FaceGeometry.Frame frame = GeckoGeometryTransforms.createFrame(
                    basePose,
                    anchorHierarchy
            ).orElse(null);
            if (frame == null) {
                lastFailure = FaceGeometry.FailureReason.INVALID_FRAME;
                continue;
            }
            Map<FaceGeometry.Key, GeckoGeometryHandle> handles =
                    new LinkedHashMap<>();
            List<FaceGeometry.Candidate> candidates = new ArrayList<>();
            collect(
                    anchor,
                    anchor.getName(),
                    FaceBoneClassifier.classify(
                            anchor.getName(),
                            FaceBoneClassifier.Role.HEAD
                    ),
                    anchorHierarchy,
                    basePose,
                    frame,
                    handles,
                    candidates
            );

            FaceGeometry.Selection selection = FACE_SELECTOR.select(
                    candidates,
                    frame
            );
            if (!selection.isAccepted()) {
                lastFailure = selection.failureReason();
                continue;
            }
            List<GeckoRankedHandle> rankedHandles = selection.ranked()
                    .stream()
                    .map(ranked -> new GeckoRankedHandle(
                            handles.get(ranked.candidate().key()),
                            ranked.confidence()
                    ))
                    .filter(ranked -> ranked.handle() != null)
                    .toList();
            if (!rankedHandles.isEmpty()) {
                return new GeckoGeometryPlan(
                        anchorHierarchy,
                        rankedHandles,
                        null
                );
            }
        }
        return GeckoGeometryPlan.failure(lastFailure);
    }

    private static void collect(
            AnimatedGeoBone bone,
            String path,
            FaceBoneClassifier.Role role,
            List<AnimatedGeoBone> ownerHierarchy,
            PoseStack basePose,
            FaceGeometry.Frame frame,
            Map<FaceGeometry.Key, GeckoGeometryHandle> handles,
            List<FaceGeometry.Candidate> candidates
    ) {
        if (role == FaceBoneClassifier.Role.EXCLUDED) {
            return;
        }

        PoseStack ownerPose = GeckoGeometryTransforms.poseFor(
                basePose,
                ownerHierarchy
        );
        GeoMesh mesh = bone.geoBone().cubes();
        for (int cubeIndex = 0; cubeIndex < mesh.getCubeCount(); cubeIndex++) {
            List<FaceGeometry.Candidate> cubeCandidates =
                    GeckoGeometryCapture.captureCube(
                            mesh,
                            path,
                            role,
                            cubeIndex,
                            ownerPose,
                            frame
                    );
            for (FaceGeometry.Candidate candidate : cubeCandidates) {
                GeckoGeometryHandle handle = new GeckoGeometryHandle(
                        bone,
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

        for (AnimatedGeoBone child : bone.children()) {
            FaceBoneClassifier.Role childRole = FaceBoneClassifier.classify(
                    child.getName(),
                    role
            );
            if (childRole == FaceBoneClassifier.Role.EXCLUDED) {
                continue;
            }
            List<AnimatedGeoBone> childHierarchy = new ArrayList<>(ownerHierarchy);
            childHierarchy.add(child);
            collect(
                    child,
                    path + "/" + child.getName(),
                    childRole,
                    List.copyOf(childHierarchy),
                    basePose,
                    frame,
                    handles,
                    candidates
            );
        }
    }
}
