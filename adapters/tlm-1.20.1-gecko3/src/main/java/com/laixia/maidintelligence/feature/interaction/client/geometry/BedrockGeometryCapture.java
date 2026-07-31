package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockCube;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BedrockGeometryCapture {
    private static final double THIN_RATIO = 0.015D;
    private static final ThreadLocal<FaceWindowConsumer> FACE_WINDOW =
            ThreadLocal.withInitial(FaceWindowConsumer::new);
    // compile() only forwards normal values to the consumer, and the window
    // consumer discards them, so the per-frame path shares one constant array.
    private static final Vector3f[] UNUSED_NORMALS = createUnusedNormals();

    private BedrockGeometryCapture() {
    }

    /**
     * Per-frame path: recompiles the cached cube but keeps only the four
     * positions of the planned face window, with no per-vertex boxing and no
     * sibling-face candidates. Returns {@code null} when the cube no longer
     * emits that window.
     */
    static List<Vec3d> captureFacePositions(
            BedrockGeometryHandle handle,
            PoseStack basePose
    ) {
        PoseStack ownerPose = BedrockGeometryTransforms.scratchPoseFor(
                basePose,
                handle.ownerHierarchy()
        );
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

    static List<FaceGeometry.Candidate> captureCube(
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
                BedrockGeometryTransforms.createNormals(
                        ownerPose.last().normal()
                ),
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

        List<Vec3d> positions = vertices.stream()
                .map(CapturedVertex::position)
                .toList();
        Vec3d groupCenter = FaceGeometry.average(positions);
        double groupWidth = BedrockGeometryTransforms.projectionRange(
                positions,
                frame.right()
        );
        double groupHeight = BedrockGeometryTransforms.projectionRange(
                positions,
                frame.up()
        );
        double groupDepth = BedrockGeometryTransforms.projectionRange(
                positions,
                frame.forward()
        );
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
            List<Vec3d> facePositions = face.stream()
                    .map(CapturedVertex::position)
                    .toList();
            Vec3d geometricOutward = FaceGeometry.average(
                    facePositions
            )
                    .subtract(groupCenter);
            Vec3d outward = averageNormal(face);
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

    private static Vector3f[] createUnusedNormals() {
        Vector3f[] normals = new Vector3f[BedrockCube.NUM_CUBE_FACES];
        Arrays.fill(normals, new Vector3f(0.0F, 0.0F, 1.0F));
        return normals;
    }

    private static Vec3d averageNormal(
            List<CapturedVertex> vertices
    ) {
        Vec3d normal = Vec3d.ZERO;
        for (CapturedVertex vertex : vertices) {
            normal = normal.add(vertex.normal());
        }
        return normal.lengthSqr() <= 1.0E-10D
                ? Vec3d.ZERO
                : normal.normalize();
    }

    private record CapturedVertex(
            Vec3d position,
            Vec3d normal
    ) {
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

        private List<Vec3d> finish() {
            if (vertexIndex < windowStart + 4) {
                return null;
            }
            return List.of(
                    new Vec3d(
                            positions[0],
                            positions[1],
                            positions[2]
                    ),
                    new Vec3d(
                            positions[3],
                            positions[4],
                            positions[5]
                    ),
                    new Vec3d(
                            positions[6],
                            positions[7],
                            positions[8]
                    ),
                    new Vec3d(
                            positions[9],
                            positions[10],
                            positions[11]
                    )
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
        private Vec3d pendingPosition = Vec3d.ZERO;
        private Vec3d pendingNormal = Vec3d.ZERO;

        private List<CapturedVertex> vertices() {
            return List.copyOf(vertices);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            pendingPosition = new Vec3d(x, y, z);
            pendingNormal = Vec3d.ZERO;
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
            pendingNormal = new Vec3d(x, y, z);
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
