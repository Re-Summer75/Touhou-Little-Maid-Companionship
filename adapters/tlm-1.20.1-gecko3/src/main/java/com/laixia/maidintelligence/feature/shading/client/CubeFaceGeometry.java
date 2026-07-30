package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import org.joml.Vector3f;

/**
 * 从单个 cube 的实际角点计算六个严格平面外法线，并记录需要翻转的面绕序。
 */
final class CubeFaceGeometry {
    private static final float EPSILON_SQUARED = 1.0E-12F;

    private final Vector3f[] faceNormals =
            new Vector3f[GeoCubeFaceTable.FACE_COUNT];
    private int reversedWindingMask;

    CubeFaceGeometry(GeoMesh mesh, int cube) {
        Vector3f origin = new Vector3f(mesh.position(cube));
        Vector3f dx = new Vector3f(mesh.dx(cube));
        Vector3f dy = new Vector3f(mesh.dy(cube));
        Vector3f dz = new Vector3f(mesh.dz(cube));
        Vector3f center = new Vector3f(origin)
                .fma(0.5F, dx)
                .fma(0.5F, dy)
                .fma(0.5F, dz);
        Vector3f[] corners = corners(origin, dx, dy, dz);

        for (int face = 0; face < GeoCubeFaceTable.FACE_COUNT; face++) {
            Vector3f first = corners[GeoCubeFaceTable.corner(face, 0)];
            Vector3f normal = new Vector3f(
                    corners[GeoCubeFaceTable.corner(face, 1)]
            ).sub(first).cross(new Vector3f(
                    corners[GeoCubeFaceTable.corner(face, 2)]
            ).sub(first));
            Vector3f outward = new Vector3f(
                    corners[GeoCubeFaceTable.diagonalStart(face)]
            ).add(corners[GeoCubeFaceTable.diagonalEnd(face)])
                    .mul(0.5F)
                    .sub(center);

            if (normal.lengthSquared() <= EPSILON_SQUARED) {
                normal = fallbackNormal(face, dx, dy, dz, outward);
            } else {
                if (normal.dot(outward) < 0.0F) {
                    normal.negate();
                    reversedWindingMask |= 1 << face;
                }
                normal.normalize();
            }
            faceNormals[face] = normal;
        }
    }

    Vector3f faceNormal(int face) {
        return faceNormals[face];
    }

    int reversedWindingMask() {
        return reversedWindingMask;
    }

    private static Vector3f fallbackNormal(
            int face,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz,
            Vector3f outward
    ) {
        Vector3f normal = switch (GeoCubeFaceTable.axis(face)) {
            case 0 -> new Vector3f(dy).cross(dz);
            case 1 -> new Vector3f(dz).cross(dx);
            default -> new Vector3f(dx).cross(dy);
        };
        if (normal.lengthSquared() > EPSILON_SQUARED) {
            if (normal.dot(outward) < 0.0F) {
                normal.negate();
            }
            return normal.normalize();
        }
        if (outward.lengthSquared() > EPSILON_SQUARED) {
            return outward.normalize();
        }
        return switch (GeoCubeFaceTable.axis(face)) {
            case 0 -> new Vector3f(GeoCubeFaceTable.sign(face), 0.0F, 0.0F);
            case 1 -> new Vector3f(0.0F, GeoCubeFaceTable.sign(face), 0.0F);
            default -> new Vector3f(0.0F, 0.0F, GeoCubeFaceTable.sign(face));
        };
    }

    private static Vector3f[] corners(
            Vector3f origin,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        return new Vector3f[]{
                origin,
                new Vector3f(origin).add(dx),
                new Vector3f(origin).add(dx).add(dy),
                new Vector3f(origin).add(dy),
                new Vector3f(origin).add(dz),
                new Vector3f(origin).add(dx).add(dz),
                new Vector3f(origin).add(dx).add(dy).add(dz),
                new Vector3f(origin).add(dy).add(dz)
        };
    }
}
