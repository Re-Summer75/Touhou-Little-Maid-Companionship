package com.laixia.maidintelligence.feature.shading.client;

import org.joml.Vector3f;

/**
 * 缓存每个 cube 的六个平面外法线及绕序修正位，不反向持有源网格。
 */
final class GeoMeshNormalTemplate {
    private static final int FACE_STRIDE = 3;
    private static final int CUBE_STRIDE =
            GeoCubeFaceTable.FACE_COUNT * FACE_STRIDE;

    private final float[] faceNormals;
    private final int[] reversedWindingMasks;
    private final boolean hasWindingCorrections;

    GeoMeshNormalTemplate(
            float[] faceNormals,
            int[] reversedWindingMasks
    ) {
        this.faceNormals = faceNormals;
        this.reversedWindingMasks = reversedWindingMasks;
        this.hasWindingCorrections = containsCorrection(
                reversedWindingMasks
        );
    }

    void setNormal(int cube, int face, Vector3f output) {
        int offset = faceOffset(cube, face);
        output.set(
                faceNormals[offset],
                faceNormals[offset + 1],
                faceNormals[offset + 2]
        );
    }

    float normalX(int cube, int face) {
        return faceNormals[faceOffset(cube, face)];
    }

    float normalY(int cube, int face) {
        return faceNormals[faceOffset(cube, face) + 1];
    }

    float normalZ(int cube, int face) {
        return faceNormals[faceOffset(cube, face) + 2];
    }

    boolean shouldReverseWinding(int cube, int face) {
        return (reversedWindingMasks[cube] & (1 << face)) != 0;
    }

    boolean hasWindingCorrections() {
        return hasWindingCorrections;
    }

    static int faceOffset(int cube, int face) {
        return cube * CUBE_STRIDE + face * FACE_STRIDE;
    }

    private static boolean containsCorrection(int[] masks) {
        for (int mask : masks) {
            if (mask != 0) {
                return true;
            }
        }
        return false;
    }
}
