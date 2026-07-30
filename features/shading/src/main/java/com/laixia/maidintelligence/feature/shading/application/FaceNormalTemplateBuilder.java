package com.laixia.maidintelligence.feature.shading.application;

import com.laixia.maidintelligence.feature.shading.api.FaceNormalTemplate;
import com.laixia.maidintelligence.feature.shading.domain.CubeFaceTopology;
import com.laixia.maidintelligence.feature.shading.port.CubeGeometrySource;
import com.laixia.maidintelligence.shared.geometry.Vec3d;

import java.util.Objects;

/**
 * Builds immutable outside-normal templates from adapter-provided cube axes.
 */
public final class FaceNormalTemplateBuilder {
    private static final double EPSILON_SQUARED = 1.0E-12D;

    private FaceNormalTemplateBuilder() {
    }

    public static <M> FaceNormalTemplate build(
            M mesh,
            CubeGeometrySource<? super M> source
    ) {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(source, "source");
        int cubeCount = source.cubeCount(mesh);
        float[] normals = new float[
                cubeCount * CubeFaceTopology.FACE_COUNT * 3
                ];
        int[] reversedWindingMasks = new int[cubeCount];

        for (int cube = 0; cube < cubeCount; cube++) {
            buildCube(
                    mesh,
                    source,
                    cube,
                    normals,
                    reversedWindingMasks
            );
        }
        return new FaceNormalTemplate(normals, reversedWindingMasks);
    }

    private static <M> void buildCube(
            M mesh,
            CubeGeometrySource<? super M> source,
            int cube,
            float[] normals,
            int[] reversedWindingMasks
    ) {
        Vec3d origin = source.origin(mesh, cube);
        Vec3d dx = source.xEdge(mesh, cube);
        Vec3d dy = source.yEdge(mesh, cube);
        Vec3d dz = source.zEdge(mesh, cube);
        Vec3d center = origin
                .add(dx.scale(0.5D))
                .add(dy.scale(0.5D))
                .add(dz.scale(0.5D));
        Vec3d[] corners = corners(origin, dx, dy, dz);
        int reversedMask = 0;

        for (int face = 0; face < CubeFaceTopology.FACE_COUNT; face++) {
            Vec3d first = corners[CubeFaceTopology.corner(face, 0)];
            Vec3d normal = corners[CubeFaceTopology.corner(face, 1)]
                    .subtract(first)
                    .cross(corners[CubeFaceTopology.corner(face, 2)]
                            .subtract(first));
            Vec3d outward = corners[CubeFaceTopology.diagonalStart(face)]
                    .add(corners[CubeFaceTopology.diagonalEnd(face)])
                    .scale(0.5D)
                    .subtract(center);

            if (normal.lengthSqr() <= EPSILON_SQUARED) {
                normal = fallbackNormal(face, dx, dy, dz, outward);
            } else {
                if (normal.dot(outward) < 0.0D) {
                    normal = normal.scale(-1.0D);
                    reversedMask |= 1 << face;
                }
                normal = normalized(normal);
            }
            int offset = FaceNormalTemplate.faceOffset(cube, face);
            normals[offset] = (float) normal.x;
            normals[offset + 1] = (float) normal.y;
            normals[offset + 2] = (float) normal.z;
        }
        reversedWindingMasks[cube] = reversedMask;
    }

    private static Vec3d fallbackNormal(
            int face,
            Vec3d dx,
            Vec3d dy,
            Vec3d dz,
            Vec3d outward
    ) {
        Vec3d normal = switch (CubeFaceTopology.axis(face)) {
            case 0 -> dy.cross(dz);
            case 1 -> dz.cross(dx);
            default -> dx.cross(dy);
        };
        if (normal.lengthSqr() > EPSILON_SQUARED) {
            if (normal.dot(outward) < 0.0D) {
                normal = normal.scale(-1.0D);
            }
            return normalized(normal);
        }
        if (outward.lengthSqr() > EPSILON_SQUARED) {
            return normalized(outward);
        }
        return switch (CubeFaceTopology.axis(face)) {
            case 0 -> new Vec3d(CubeFaceTopology.sign(face), 0.0D, 0.0D);
            case 1 -> new Vec3d(0.0D, CubeFaceTopology.sign(face), 0.0D);
            default -> new Vec3d(0.0D, 0.0D, CubeFaceTopology.sign(face));
        };
    }

    private static Vec3d normalized(Vec3d vector) {
        return vector.scale(1.0D / Math.sqrt(vector.lengthSqr()));
    }

    private static Vec3d[] corners(
            Vec3d origin,
            Vec3d dx,
            Vec3d dy,
            Vec3d dz
    ) {
        return new Vec3d[]{
                origin,
                origin.add(dx),
                origin.add(dx).add(dy),
                origin.add(dy),
                origin.add(dz),
                origin.add(dx).add(dz),
                origin.add(dx).add(dy).add(dz),
                origin.add(dy).add(dz)
        };
    }
}
