package com.laixia.maidintelligence.feature.physics.layout;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import org.joml.Vector3f;

/**
 * Minimal OBB/SAT helper used only while partitioning authored cubes.
 */
record OrientedCubeBounds(
        Vector3f center,
        Vector3f axisX,
        Vector3f axisY,
        Vector3f axisZ,
        float halfX,
        float halfY,
        float halfZ
) {
    private static final float EPSILON = 1.0E-6F;

    static OrientedCubeBounds from(BoneModelSnapshot.Mesh mesh, int cube) {
        Vector3f origin = mesh.position(cube);
        Vector3f dx = mesh.dx(cube);
        Vector3f dy = mesh.dy(cube);
        Vector3f dz = mesh.dz(cube);
        Vector3f[] axes = orthonormalAxes(dx, dy, dz);
        return new OrientedCubeBounds(
                new Vector3f(origin).fma(0.5F, dx)
                        .fma(0.5F, dy)
                        .fma(0.5F, dz),
                axes[0],
                axes[1],
                axes[2],
                dx.length() * 0.5F,
                dy.length() * 0.5F,
                dz.length() * 0.5F
        );
    }

    boolean touches(OrientedCubeBounds other, float margin) {
        Vector3f[] first = {axisX, axisY, axisZ};
        Vector3f[] second = {other.axisX, other.axisY, other.axisZ};
        float[] firstHalf = {
                halfX + margin * 0.5F,
                halfY + margin * 0.5F,
                halfZ + margin * 0.5F
        };
        float[] secondHalf = {
                other.halfX + margin * 0.5F,
                other.halfY + margin * 0.5F,
                other.halfZ + margin * 0.5F
        };
        float[][] rotation = new float[3][3];
        float[][] absolute = new float[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                rotation[row][column] =
                        first[row].dot(second[column]);
                absolute[row][column] =
                        Math.abs(rotation[row][column]) + EPSILON;
            }
        }
        Vector3f delta = new Vector3f(other.center).sub(center);
        float[] translated = {
                delta.dot(first[0]),
                delta.dot(first[1]),
                delta.dot(first[2])
        };
        for (int row = 0; row < 3; row++) {
            float projected = secondHalf[0] * absolute[row][0]
                    + secondHalf[1] * absolute[row][1]
                    + secondHalf[2] * absolute[row][2];
            if (Math.abs(translated[row]) > firstHalf[row] + projected) {
                return false;
            }
        }
        for (int column = 0; column < 3; column++) {
            float projected = Math.abs(
                    translated[0] * rotation[0][column]
                            + translated[1] * rotation[1][column]
                            + translated[2] * rotation[2][column]
            );
            float radius = secondHalf[column]
                    + firstHalf[0] * absolute[0][column]
                    + firstHalf[1] * absolute[1][column]
                    + firstHalf[2] * absolute[2][column];
            if (projected > radius) {
                return false;
            }
        }
        for (int row = 0; row < 3; row++) {
            int nextRow = (row + 1) % 3;
            int lastRow = (row + 2) % 3;
            for (int column = 0; column < 3; column++) {
                int nextColumn = (column + 1) % 3;
                int lastColumn = (column + 2) % 3;
                float projected = Math.abs(
                        translated[lastRow]
                                * rotation[nextRow][column]
                                - translated[nextRow]
                                * rotation[lastRow][column]
                );
                float firstRadius =
                        firstHalf[nextRow]
                                * absolute[lastRow][column]
                                + firstHalf[lastRow]
                                * absolute[nextRow][column];
                float secondRadius =
                        secondHalf[nextColumn]
                                * absolute[row][lastColumn]
                                + secondHalf[lastColumn]
                                * absolute[row][nextColumn];
                if (projected > firstRadius + secondRadius) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean hasVisibleSurface() {
        int rank = halfX > EPSILON ? 1 : 0;
        if (halfY > EPSILON) {
            rank++;
        }
        if (halfZ > EPSILON) {
            rank++;
        }
        return rank >= 2;
    }

    private static Vector3f[] orthonormalAxes(
            Vector3f dx,
            Vector3f dy,
            Vector3f dz
    ) {
        Vector3f x = normalized(dx);
        Vector3f y = normalized(dy);
        Vector3f z = normalized(dz);
        if (x == null) {
            x = y != null && z != null
                    ? new Vector3f(y).cross(z).normalize()
                    : perpendicular(y != null ? y : z);
        }
        if (y == null) {
            y = z != null
                    ? new Vector3f(z).cross(x).normalize()
                    : perpendicular(x);
        }
        y.fma(-x.dot(y), x);
        if (y.lengthSquared() <= EPSILON) {
            y.set(perpendicular(x));
        } else {
            y.normalize();
        }
        z = new Vector3f(x).cross(y).normalize();
        return new Vector3f[]{x, y, z};
    }

    private static Vector3f normalized(Vector3f value) {
        return value.lengthSquared() <= EPSILON
                ? null
                : new Vector3f(value).normalize();
    }

    private static Vector3f perpendicular(Vector3f axis) {
        if (axis == null) {
            return new Vector3f(1.0F, 0.0F, 0.0F);
        }
        return Math.abs(axis.y) < 0.90F
                ? new Vector3f(0.0F, 1.0F, 0.0F).cross(axis).normalize()
                : new Vector3f(1.0F, 0.0F, 0.0F).cross(axis).normalize();
    }
}
