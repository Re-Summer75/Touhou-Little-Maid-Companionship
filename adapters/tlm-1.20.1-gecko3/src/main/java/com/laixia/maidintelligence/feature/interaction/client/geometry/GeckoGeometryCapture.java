package com.laixia.maidintelligence.feature.interaction.client.geometry;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.interaction.domain.FaceBoneClassifier;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class GeckoGeometryCapture {
    private static final double THIN_RATIO = 0.015D;
    private static final int MIRROR_MASK = 0b1000000;
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

    private GeckoGeometryCapture() {
    }

    /**
     * Per-frame path: transforms only the four corners of the planned face
     * instead of rebuilding every candidate of the cube. Returns {@code null}
     * when the cube or face is no longer present in the mesh.
     */
    static List<Vec3d> captureFacePositions(
            GeckoGeometryHandle handle,
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

        PoseStack ownerPose = GeckoGeometryTransforms.scratchPoseFor(
                basePose,
                handle.ownerHierarchy()
        );
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

    static List<FaceGeometry.Candidate> captureCube(
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
        double groupWidth = GeckoGeometryTransforms.projectionRange(
                corners,
                frame.right()
        );
        double groupHeight = GeckoGeometryTransforms.projectionRange(
                corners,
                frame.up()
        );
        double groupDepth = GeckoGeometryTransforms.projectionRange(
                corners,
                frame.forward()
        );
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
                GeckoGeometryTransforms.toRender(pose, p000),
                GeckoGeometryTransforms.toRender(pose, p100),
                GeckoGeometryTransforms.toRender(pose, p010),
                GeckoGeometryTransforms.toRender(pose, p001),
                GeckoGeometryTransforms.toRender(pose, p110),
                GeckoGeometryTransforms.toRender(pose, p101),
                GeckoGeometryTransforms.toRender(pose, p011),
                GeckoGeometryTransforms.toRender(pose, p111)
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
}
