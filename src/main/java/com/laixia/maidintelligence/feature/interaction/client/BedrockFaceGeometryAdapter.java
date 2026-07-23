package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockCube;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BedrockFaceGeometryAdapter {
    private static final double THIN_RATIO = 0.015D;
    private static final ThreadLocal<PoseStack> SCRATCH_POSE =
            ThreadLocal.withInitial(PoseStack::new);
    private static final ThreadLocal<FaceWindowConsumer> FACE_WINDOW =
            ThreadLocal.withInitial(FaceWindowConsumer::new);
    // compile() only forwards normal values to the consumer, and the window
    // consumer discards them, so the per-frame path shares one constant array.
    private static final Vector3f[] UNUSED_NORMALS = createUnusedNormals();

    private BedrockFaceGeometryAdapter() {
    }

    static Result resolve(BedrockModel<Mob> model, PoseStack basePose) {
        Plan plan = FaceTrackingGeometryCache.getOrCompute(
                model,
                Plan.class,
                () -> discover(model, basePose)
        );
        if (plan.failureReason() != null) {
            return Result.failure(plan.failureReason());
        }

        FaceGeometry.Frame frame = createFrame(basePose, plan.anchorHierarchy());
        for (RankedHandle ranked : plan.rankedHandles()) {
            Handle handle = ranked.handle();
            boolean semanticSurface = handle.role() == FaceBoneClassifier.Role.FACE
                    || handle.role() == FaceBoneClassifier.Role.BLINK;
            if (!isUsable(handle.ownerHierarchy(), semanticSurface)) {
                continue;
            }
            List<Vec3> facePositions = captureFacePositions(handle, basePose);
            if (facePositions == null) {
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
                                FaceGeometry.Source.BEDROCK,
                                handle.path(),
                                handle.cubeIndex(),
                                handle.faceOrdinal()
                        )
                );
            }
        }
        return Result.failure(FaceGeometry.FailureReason.NO_GEOMETRY);
    }

    private static Plan discover(BedrockModel<Mob> model, PoseStack basePose) {
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
            return Plan.failure(FaceGeometry.FailureReason.NO_HEAD_ANCHOR);
        }

        FaceGeometry.FailureReason lastFailure =
                FaceGeometry.FailureReason.NO_GEOMETRY;
        for (Map.Entry<String, BedrockPart> anchorEntry : anchors) {
            BedrockPart anchor = anchorEntry.getValue();
            List<BedrockPart> anchorHierarchy = hierarchy(anchor);
            FaceGeometry.Frame frame = createFrame(basePose, anchorHierarchy);
            Map<FaceGeometry.Key, Handle> handles = new LinkedHashMap<>();
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
                    FaceCandidateSelector.selectPrioritizingSemanticSurface(
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
            BedrockPart part,
            String path,
            FaceBoneClassifier.Role role,
            List<BedrockPart> ownerHierarchy,
            IdentityHashMap<BedrockPart, String> names,
            PoseStack basePose,
            FaceGeometry.Frame frame,
            Map<FaceGeometry.Key, Handle> handles,
            List<FaceGeometry.Candidate> candidates
    ) {
        if (role == FaceBoneClassifier.Role.EXCLUDED) {
            return;
        }

        PoseStack ownerPose = poseFor(basePose, ownerHierarchy);
        for (int cubeIndex = 0; cubeIndex < part.cubes.size(); cubeIndex++) {
            BedrockCube cube = part.cubes.get(cubeIndex);
            List<FaceGeometry.Candidate> cubeCandidates = captureCube(
                    cube,
                    path,
                    role,
                    cubeIndex,
                    ownerPose,
                    frame
            );
            for (FaceGeometry.Candidate candidate : cubeCandidates) {
                Handle handle = new Handle(
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

    /**
     * Per-frame path: recompiles the cached cube but keeps only the four
     * positions of the planned face window, with no per-vertex boxing and no
     * sibling-face candidates. Returns {@code null} when the cube no longer
     * emits that window.
     */
    private static List<Vec3> captureFacePositions(
            Handle handle,
            PoseStack basePose
    ) {
        PoseStack ownerPose = scratchPoseFor(basePose, handle.ownerHierarchy());
        FaceWindowConsumer consumer = FACE_WINDOW.get();
        consumer.begin(handle.faceOrdinal() * 4);
        handle.cube().compile(
                ownerPose.last(),
                UNUSED_NORMALS,
                consumer,
                0,
                0,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        return consumer.finish();
    }

    private static List<FaceGeometry.Candidate> captureCube(
            BedrockCube cube,
            String path,
            FaceBoneClassifier.Role role,
            int cubeIndex,
            PoseStack ownerPose,
            FaceGeometry.Frame frame
    ) {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        cube.compile(
                ownerPose.last(),
                createNormals(ownerPose.last().normal()),
                consumer,
                0,
                0,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        List<CapturedVertex> vertices = consumer.vertices();
        if (vertices.size() < 4) {
            return List.of();
        }

        List<Vec3> positions = vertices.stream()
                .map(CapturedVertex::position)
                .toList();
        Vec3 groupCenter = FaceGeometry.average(positions);
        double groupWidth = projectionRange(positions, frame.right());
        double groupHeight = projectionRange(positions, frame.up());
        double groupDepth = projectionRange(positions, frame.forward());
        double maximumAxis = Math.max(groupWidth, Math.max(groupHeight, groupDepth));
        double minimumAxis = Math.min(groupWidth, Math.min(groupHeight, groupDepth));
        boolean thin = maximumAxis > 1.0E-5D
                && minimumAxis / maximumAxis <= THIN_RATIO;

        List<FaceGeometry.Candidate> candidates = new ArrayList<>();
        int completeFaces = vertices.size() / 4;
        for (int faceOrdinal = 0; faceOrdinal < completeFaces; faceOrdinal++) {
            List<CapturedVertex> face = vertices.subList(
                    faceOrdinal * 4,
                    faceOrdinal * 4 + 4
            );
            List<Vec3> facePositions = face.stream()
                    .map(CapturedVertex::position)
                    .toList();
            Vec3 geometricOutward = FaceGeometry.average(facePositions)
                    .subtract(groupCenter);
            Vec3 outward = averageNormal(face);
            if (!thin
                    && completeFaces == BedrockCube.NUM_CUBE_FACES
                    && geometricOutward.lengthSqr() > 1.0E-10D) {
                // Full solid cubes provide an exact geometric outward direction;
                // this avoids version-specific normal-table face ordering.
                outward = geometricOutward.normalize();
            } else if (outward.lengthSqr() <= 1.0E-10D) {
                outward = geometricOutward;
            }
            candidates.add(new FaceGeometry.Candidate(
                    new FaceGeometry.Key(
                            FaceGeometry.Source.BEDROCK,
                            path,
                            cubeIndex,
                            faceOrdinal
                    ),
                    role,
                    facePositions,
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

    private static FaceGeometry.Frame createFrame(
            PoseStack basePose,
            List<BedrockPart> anchorHierarchy
    ) {
        PoseStack pose = scratchPoseFor(basePose, anchorHierarchy);
        return new FaceGeometry.Frame(
                transformedPosition(pose, 0.0F, 0.0F, 0.0F),
                transformedDirection(pose, 1.0F, 0.0F, 0.0F),
                transformedDirection(pose, 0.0F, -1.0F, 0.0F),
                transformedDirection(pose, 0.0F, 0.0F, -1.0F)
        );
    }

    private static PoseStack poseFor(
            PoseStack basePose,
            List<BedrockPart> hierarchy
    ) {
        PoseStack pose = copyPose(basePose);
        hierarchy.forEach(part -> part.translateAndRotateAndScale(pose));
        return pose;
    }

    /**
     * Reuses one never-pushed PoseStack per thread. Callers must extract what
     * they need before the next scratch call on the same thread.
     */
    private static PoseStack scratchPoseFor(
            PoseStack basePose,
            List<BedrockPart> hierarchy
    ) {
        PoseStack pose = SCRATCH_POSE.get();
        pose.last().pose().set(basePose.last().pose());
        pose.last().normal().set(basePose.last().normal());
        for (BedrockPart part : hierarchy) {
            part.translateAndRotateAndScale(pose);
        }
        return pose;
    }

    private static List<BedrockPart> hierarchy(BedrockPart part) {
        List<BedrockPart> hierarchy = new ArrayList<>();
        BedrockPart current = part;
        while (current != null) {
            hierarchy.add(0, current);
            current = current.getParent();
        }
        return List.copyOf(hierarchy);
    }

    private static boolean isUsable(
            List<BedrockPart> hierarchy,
            boolean allowHiddenSurface
    ) {
        for (int index = 0; index < hierarchy.size(); index++) {
            BedrockPart part = hierarchy.get(index);
            boolean hiddenSemanticLeaf = allowHiddenSurface
                    && index == hierarchy.size() - 1;
            // Blink/face overlays are commonly hidden outside their animation,
            // but their unchanged geometry remains the best facial locator.
            if (!part.visible && !hiddenSemanticLeaf) {
                return false;
            }
            boolean xZero = Math.abs(part.xScale) <= 1.0E-5F;
            boolean yZero = Math.abs(part.yScale) <= 1.0E-5F;
            boolean zZero = Math.abs(part.zScale) <= 1.0E-5F;
            if ((xZero && yZero) || (xZero && zZero) || (yZero && zZero)) {
                return false;
            }
        }
        return true;
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Vec3 transformedPosition(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulPosition(pose.last().pose());
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static Vec3 transformedDirection(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(pose.last().pose())
                .normalize();
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static Vector3f[] createNormals(Matrix3f normal) {
        return new Vector3f[]{
                new Vector3f(-normal.m10, -normal.m11, -normal.m12).normalize(),
                new Vector3f(normal.m10, normal.m11, normal.m12).normalize(),
                new Vector3f(-normal.m20, -normal.m21, -normal.m22).normalize(),
                new Vector3f(normal.m20, normal.m21, normal.m22).normalize(),
                new Vector3f(-normal.m00, -normal.m01, -normal.m02).normalize(),
                new Vector3f(normal.m00, normal.m01, normal.m02).normalize()
        };
    }

    private static Vector3f[] createUnusedNormals() {
        Vector3f[] normals = new Vector3f[BedrockCube.NUM_CUBE_FACES];
        Arrays.fill(normals, new Vector3f(0.0F, 0.0F, 1.0F));
        return normals;
    }

    private static Vec3 averageNormal(List<CapturedVertex> vertices) {
        Vec3 normal = Vec3.ZERO;
        for (CapturedVertex vertex : vertices) {
            normal = normal.add(vertex.normal());
        }
        return normal.lengthSqr() <= 1.0E-10D
                ? Vec3.ZERO
                : normal.normalize();
    }

    private static double projectionRange(List<Vec3> vertices, Vec3 axis) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : vertices) {
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
            List<BedrockPart> anchorHierarchy,
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
            BedrockCube cube,
            List<BedrockPart> ownerHierarchy,
            FaceBoneClassifier.Role role,
            String path,
            int cubeIndex,
            int faceOrdinal
    ) {
    }

    private record CapturedVertex(Vec3 position, Vec3 normal) {
    }

    /**
     * Reusable consumer that keeps only the positions of one four-vertex face
     * window out of a cube compilation, dropping everything else unboxed.
     */
    private static final class FaceWindowConsumer implements VertexConsumer {
        private final double[] positions = new double[12];
        private int windowStart;
        private int vertexIndex;
        private double pendingX;
        private double pendingY;
        private double pendingZ;

        private void begin(int windowStart) {
            this.windowStart = windowStart;
            this.vertexIndex = 0;
        }

        private List<Vec3> finish() {
            if (vertexIndex < windowStart + 4) {
                return null;
            }
            return List.of(
                    new Vec3(positions[0], positions[1], positions[2]),
                    new Vec3(positions[3], positions[4], positions[5]),
                    new Vec3(positions[6], positions[7], positions[8]),
                    new Vec3(positions[9], positions[10], positions[11])
            );
        }

        private void store(double x, double y, double z) {
            int offset = vertexIndex - windowStart;
            if (offset >= 0 && offset < 4) {
                positions[offset * 3] = x;
                positions[offset * 3 + 1] = y;
                positions[offset * 3 + 2] = z;
            }
            vertexIndex++;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            pendingX = x;
            pendingY = y;
            pendingZ = z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            store(pendingX, pendingY, pendingZ);
        }

        // Matches the bulk overload BedrockCube.compile actually calls, so the
        // seven-call interface default never runs.
        @Override
        public void vertex(
                float x,
                float y,
                float z,
                float red,
                float green,
                float blue,
                float alpha,
                float u,
                float v,
                int overlay,
                int light,
                float normalX,
                float normalY,
                float normalZ
        ) {
            store(x, y, z);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<CapturedVertex> vertices = new ArrayList<>();
        private Vec3 pendingPosition = Vec3.ZERO;
        private Vec3 pendingNormal = Vec3.ZERO;

        private List<CapturedVertex> vertices() {
            return List.copyOf(vertices);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            pendingPosition = new Vec3(x, y, z);
            pendingNormal = Vec3.ZERO;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            pendingNormal = new Vec3(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            vertices.add(new CapturedVertex(pendingPosition, pendingNormal));
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
