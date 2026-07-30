package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.application.FaceCandidateSelector;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeckoFaceGeometryAdapter {
    private static final double THIN_RATIO = 0.015D;
    private static final int MIRROR_MASK = 0b1000000;
    private static final FaceSelectionApi FACE_SELECTOR =
            new FaceCandidateSelector();
    private static final ThreadLocal<PoseStack> SCRATCH_POSE =
            ThreadLocal.withInitial(PoseStack::new);
    private static final ThreadLocal<Vector3f> SCRATCH_CORNER =
            ThreadLocal.withInitial(Vector3f::new);
    // Corner selection per face, mirroring face(): bit 1 adds dx, bit 2 adds
    // dy, bit 4 adds dz on top of the cube origin.
    private static final int[][] FACE_CORNER_MASKS = {
            {5, 4, 0, 1},
            {3, 2, 6, 7},
            {1, 0, 2, 3},
            {4, 5, 7, 6},
            {0, 4, 6, 2},
            {5, 1, 3, 7}
    };

    private GeckoFaceGeometryAdapter() {
    }

    static Result resolve(
            AnimatedGeoModel model,
            PoseStack basePose,
            String variantKey
    ) {
        Plan plan = FaceTrackingGeometryCache.getOrCompute(
                model,
                variantKey,
                Plan.class,
                () -> discover(model, basePose)
        );
        if (plan.failureReason() != null) {
            if (plan.failureReason()
                    == FaceGeometry.FailureReason.INVALID_FRAME) {
                FaceTrackingGeometryCache.invalidatePlan(model);
            }
            return Result.failure(plan.failureReason());
        }

        FaceGeometry.Frame frame = createFrame(
                basePose,
                plan.anchorHierarchy()
        ).orElse(null);
        if (frame == null) {
            FaceTrackingGeometryCache.invalidatePlan(model);
            return Result.failure(FaceGeometry.FailureReason.INVALID_FRAME);
        }
        boolean stalePlan = false;
        for (RankedHandle ranked : plan.rankedHandles()) {
            Handle handle = ranked.handle();
            if (!isVisible(handle.ownerHierarchy(), handle.owner())) {
                continue;
            }
            List<Vec3d> facePositions = captureFacePositions(handle, basePose);
            if (facePositions == null) {
                stalePlan = true;
                continue;
            }
            MaidFacePlane plane = MaidFacePlane
                    .fromVertices(facePositions, frame)
                    .orElse(null);
            if (plane != null) {
                return Result.success(
                        plane,
                        ranked.confidence(),
                        new FaceGeometry.Key(
                                FaceGeometry.Source.GECKO,
                                handle.path(),
                                handle.cubeIndex(),
                                handle.faceIndex()
                        )
                );
            }
        }
        if (stalePlan) {
            FaceTrackingGeometryCache.invalidatePlan(model);
        }
        return Result.failure(FaceGeometry.FailureReason.NO_GEOMETRY);
    }

    private static Plan discover(AnimatedGeoModel model, PoseStack basePose) {
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
            return Plan.failure(FaceGeometry.FailureReason.NO_HEAD_ANCHOR);
        }

        FaceGeometry.FailureReason lastFailure =
                FaceGeometry.FailureReason.NO_GEOMETRY;
        for (AnimatedGeoBone anchor : anchors) {
            List<AnimatedGeoBone> anchorHierarchy = hierarchy(model, anchor);
            FaceGeometry.Frame frame = createFrame(
                    basePose,
                    anchorHierarchy
            ).orElse(null);
            if (frame == null) {
                lastFailure = FaceGeometry.FailureReason.INVALID_FRAME;
                continue;
            }
            Map<FaceGeometry.Key, Handle> handles = new LinkedHashMap<>();
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
            List<RankedHandle> rankedHandles = selection.ranked()
                    .stream()
                    .map(ranked -> new RankedHandle(
                            handles.get(ranked.candidate().key()),
                            ranked.confidence()
                    ))
                    .filter(ranked -> ranked.handle() != null)
                    .toList();
            if (!rankedHandles.isEmpty()) {
                return new Plan(anchorHierarchy, rankedHandles, null);
            }
        }
        return Plan.failure(lastFailure);
    }

    private static void collect(
            AnimatedGeoBone bone,
            String path,
            FaceBoneClassifier.Role role,
            List<AnimatedGeoBone> ownerHierarchy,
            PoseStack basePose,
            FaceGeometry.Frame frame,
            Map<FaceGeometry.Key, Handle> handles,
            List<FaceGeometry.Candidate> candidates
    ) {
        if (role == FaceBoneClassifier.Role.EXCLUDED) {
            return;
        }

        PoseStack ownerPose = poseFor(basePose, ownerHierarchy);
        GeoMesh mesh = bone.geoBone().cubes();
        for (int cubeIndex = 0; cubeIndex < mesh.getCubeCount(); cubeIndex++) {
            List<FaceGeometry.Candidate> cubeCandidates = captureCube(
                    mesh,
                    path,
                    role,
                    cubeIndex,
                    ownerPose,
                    frame
            );
            for (FaceGeometry.Candidate candidate : cubeCandidates) {
                Handle handle = new Handle(
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

    /**
     * Per-frame path: transforms only the four corners of the planned face
     * instead of rebuilding every candidate of the cube. Returns {@code null}
     * when the cube or face is no longer present in the mesh.
     */
    private static List<Vec3d> captureFacePositions(
            Handle handle,
            PoseStack basePose
    ) {
        GeoMesh mesh = handle.owner().geoBone().cubes();
        int cubeIndex = handle.cubeIndex();
        if (cubeIndex >= mesh.getCubeCount()) {
            return null;
        }
        int faces = mesh.faces(cubeIndex) & ~MIRROR_MASK;
        if ((faces & (1 << handle.faceIndex())) == 0) {
            return null;
        }

        PoseStack ownerPose = scratchPoseFor(basePose, handle.ownerHierarchy());
        Matrix4f pose = ownerPose.last().pose();
        Vector3f position = mesh.position(cubeIndex);
        Vector3f dx = mesh.dx(cubeIndex);
        Vector3f dy = mesh.dy(cubeIndex);
        Vector3f dz = mesh.dz(cubeIndex);
        int[] cornerMasks = FACE_CORNER_MASKS[handle.faceIndex()];
        return List.of(
                corner(pose, position, dx, dy, dz, cornerMasks[0]),
                corner(pose, position, dx, dy, dz, cornerMasks[1]),
                corner(pose, position, dx, dy, dz, cornerMasks[2]),
                corner(pose, position, dx, dy, dz, cornerMasks[3])
        );
    }

    private static Vec3d corner(
            Matrix4f pose,
            Vector3f position,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz,
            int cornerMask
    ) {
        Vector3f corner = SCRATCH_CORNER.get().set(position);
        if ((cornerMask & 1) != 0) {
            corner.add(dx);
        }
        if ((cornerMask & 2) != 0) {
            corner.add(dy);
        }
        if ((cornerMask & 4) != 0) {
            corner.add(dz);
        }
        corner.mulPosition(pose);
        return new Vec3d(corner.x(), corner.y(), corner.z());
    }

    private static List<FaceGeometry.Candidate> captureCube(
            GeoMesh mesh,
            String path,
            FaceBoneClassifier.Role role,
            int cubeIndex,
            PoseStack ownerPose,
            FaceGeometry.Frame frame
    ) {
        Vector3f position = new Vector3f(mesh.position(cubeIndex));
        Vector3f dx = new Vector3f(mesh.dx(cubeIndex));
        Vector3f dy = new Vector3f(mesh.dy(cubeIndex));
        Vector3f dz = new Vector3f(mesh.dz(cubeIndex));
        List<Vec3d> corners = transformedCorners(
                ownerPose,
                position,
                dx,
                dy,
                dz
        );
        Vec3d groupCenter = FaceGeometry.average(corners);
        double groupWidth = projectionRange(corners, frame.right());
        double groupHeight = projectionRange(corners, frame.up());
        double groupDepth = projectionRange(corners, frame.forward());
        double maximumAxis = Math.max(groupWidth, Math.max(groupHeight, groupDepth));
        double minimumAxis = Math.min(groupWidth, Math.min(groupHeight, groupDepth));
        boolean thin = maximumAxis > 1.0E-5D
                && minimumAxis / maximumAxis <= THIN_RATIO;

        int faces = mesh.faces(cubeIndex) & ~MIRROR_MASK;
        List<FaceGeometry.Candidate> candidates = new ArrayList<>();
        for (int faceIndex = 0; faceIndex < GeoMesh.FACE_COUNT; faceIndex++) {
            if ((faces & (1 << faceIndex)) == 0) {
                continue;
            }
            List<Vec3d> face = face(corners, faceIndex);
            Vec3d outward = FaceGeometry.average(face)
                    .subtract(groupCenter);
            candidates.add(new FaceGeometry.Candidate(
                    new FaceGeometry.Key(
                            FaceGeometry.Source.GECKO,
                            path,
                            cubeIndex,
                            faceIndex
                    ),
                    role,
                    face,
                    outward,
                    groupCenter,
                    Math.max(groupWidth, 1.0E-4D),
                    Math.max(groupHeight, 1.0E-4D),
                    Math.max(groupDepth, 1.0E-4D),
                    thin
            ));
        }
        return candidates;
    }

    private static List<Vec3d> transformedCorners(
            PoseStack pose,
            Vector3f position,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        Vector3f p000 = new Vector3f(position);
        Vector3f p100 = new Vector3f(position).add(dx);
        Vector3f p010 = new Vector3f(position).add(dy);
        Vector3f p001 = new Vector3f(position).add(dz);
        Vector3f p110 = new Vector3f(position).add(dx).add(dy);
        Vector3f p101 = new Vector3f(position).add(dx).add(dz);
        Vector3f p011 = new Vector3f(position).add(dy).add(dz);
        Vector3f p111 = new Vector3f(position).add(dx).add(dy).add(dz);
        return List.of(
                toRender(pose, p000),
                toRender(pose, p100),
                toRender(pose, p010),
                toRender(pose, p001),
                toRender(pose, p110),
                toRender(pose, p101),
                toRender(pose, p011),
                toRender(pose, p111)
        );
    }

    private static List<Vec3d> face(
            List<Vec3d> corners,
            int faceIndex
    ) {
        Vec3d p000 = corners.get(0);
        Vec3d p100 = corners.get(1);
        Vec3d p010 = corners.get(2);
        Vec3d p001 = corners.get(3);
        Vec3d p110 = corners.get(4);
        Vec3d p101 = corners.get(5);
        Vec3d p011 = corners.get(6);
        Vec3d p111 = corners.get(7);
        return switch (faceIndex) {
            case 0 -> List.of(p101, p001, p000, p100);
            case 1 -> List.of(p110, p010, p011, p111);
            case 2 -> List.of(p100, p000, p010, p110);
            case 3 -> List.of(p001, p101, p111, p011);
            case 4 -> List.of(p000, p001, p011, p010);
            case 5 -> List.of(p101, p100, p110, p111);
            default -> throw new IllegalArgumentException("Unknown Gecko face " + faceIndex);
        };
    }

    private static Optional<FaceGeometry.Frame> createFrame(
            PoseStack basePose,
            List<AnimatedGeoBone> anchorHierarchy
    ) {
        PoseStack pose = scratchPoseFor(basePose, anchorHierarchy);
        return FaceGeometry.Frame.tryCreate(
                transformedPosition(pose, 0.0F, 0.0F, 0.0F),
                transformedDirection(pose, -1.0F, 0.0F, 0.0F),
                transformedDirection(pose, 0.0F, 1.0F, 0.0F),
                transformedDirection(pose, 0.0F, 0.0F, -1.0F)
        );
    }

    /**
     * Reuses one never-pushed PoseStack per thread. Callers must extract what
     * they need before the next scratch call on the same thread.
     */
    private static PoseStack scratchPoseFor(
            PoseStack basePose,
            List<AnimatedGeoBone> hierarchy
    ) {
        PoseStack pose = SCRATCH_POSE.get();
        pose.last().pose().set(basePose.last().pose());
        pose.last().normal().set(basePose.last().normal());
        for (AnimatedGeoBone bone : hierarchy) {
            RenderUtils.prepMatrixForBone(pose, bone);
        }
        return pose;
    }

    private static List<AnimatedGeoBone> hierarchy(
            AnimatedGeoModel model,
            AnimatedGeoBone bone
    ) {
        List<AnimatedGeoBone> hierarchy = new ArrayList<>();
        AnimatedGeoBone current = bone;
        while (current != null) {
            hierarchy.add(0, current);
            if (current.geoBone().parent() == null) {
                break;
            }
            current = model.bones().get(current.geoBone().parent().name());
        }
        return List.copyOf(hierarchy);
    }

    private static PoseStack poseFor(
            PoseStack basePose,
            List<AnimatedGeoBone> hierarchy
    ) {
        PoseStack pose = copyPose(basePose);
        hierarchy.forEach(bone -> RenderUtils.prepMatrixForBone(pose, bone));
        return pose;
    }

    private static boolean isVisible(
            List<AnimatedGeoBone> hierarchy,
            AnimatedGeoBone owner
    ) {
        for (int index = 0; index < hierarchy.size(); index++) {
            AnimatedGeoBone bone = hierarchy.get(index);
            int nonZeroAxes = (bone.getScaleX() == 0.0F ? 0 : 1)
                    + (bone.getScaleY() == 0.0F ? 0 : 1)
                    + (bone.getScaleZ() == 0.0F ? 0 : 1);
            if (nonZeroAxes < 2) {
                return false;
            }
            if (index < hierarchy.size() - 1 && bone.childBonesAreHiddenToo()) {
                return false;
            }
        }
        return !owner.isHidden() && !owner.cubesAreHidden();
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Vec3d toRender(
            PoseStack pose,
            Vector3f localPosition
    ) {
        Vector3f transformed = new Vector3f(localPosition)
                .mulPosition(pose.last().pose());
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }

    private static Vec3d transformedPosition(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        return toRender(pose, new Vector3f(x, y, z));
    }

    private static Vec3d transformedDirection(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(pose.last().pose())
                .normalize();
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }

    private static double projectionRange(
            List<Vec3d> vertices,
            Vec3d axis
    ) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (Vec3d vertex : vertices) {
            double projection = vertex.dot(axis);
            minimum = Math.min(minimum, projection);
            maximum = Math.max(maximum, projection);
        }
        return maximum - minimum;
    }

    record Result(
            Optional<MaidFacePlane> plane,
            double confidence,
            FaceGeometry.Key key,
            FaceGeometry.FailureReason failureReason
    ) {
        private static Result success(
                MaidFacePlane plane,
                double confidence,
                FaceGeometry.Key key
        ) {
            return new Result(Optional.of(plane), confidence, key, null);
        }

        private static Result failure(FaceGeometry.FailureReason reason) {
            return new Result(Optional.empty(), 0.0D, null, reason);
        }
    }

    private record Plan(
            List<AnimatedGeoBone> anchorHierarchy,
            List<RankedHandle> rankedHandles,
            FaceGeometry.FailureReason failureReason
    ) {
        private static Plan failure(FaceGeometry.FailureReason reason) {
            return new Plan(List.of(), List.of(), reason);
        }
    }

    private record RankedHandle(Handle handle, double confidence) {
    }

    private record Handle(
            AnimatedGeoBone owner,
            List<AnimatedGeoBone> ownerHierarchy,
            FaceBoneClassifier.Role role,
            String path,
            int cubeIndex,
            int faceIndex
    ) {
    }
}
