package com.laixia.maidintelligence.feature.shading.api;

import com.laixia.maidintelligence.feature.shading.domain.CubeFaceTopology;

import java.util.Objects;

/**
 * Compact per-cube face normals and winding-correction masks.
 */
public final class FaceNormalTemplate {
    private static final int FACE_STRIDE = 3;
    private static final int CUBE_STRIDE =
            CubeFaceTopology.FACE_COUNT * FACE_STRIDE;

    private final float[] faceNormals;
    private final int[] reversedWindingMasks;
    private final boolean hasWindingCorrections;

    public FaceNormalTemplate(
            float[] faceNormals,
            int[] reversedWindingMasks
    ) {
        this.faceNormals = Objects.requireNonNull(
                faceNormals,
                "faceNormals"
        );
        this.reversedWindingMasks = Objects.requireNonNull(
                reversedWindingMasks,
                "reversedWindingMasks"
        );
        if (faceNormals.length != reversedWindingMasks.length * CUBE_STRIDE) {
            throw new IllegalArgumentException(
                    "normal and winding arrays describe different cube counts"
            );
        }
        this.hasWindingCorrections = containsCorrection(
                reversedWindingMasks
        );
    }

    public float normalX(int cube, int face) {
        return faceNormals[faceOffset(cube, face)];
    }

    public float normalY(int cube, int face) {
        return faceNormals[faceOffset(cube, face) + 1];
    }

    public float normalZ(int cube, int face) {
        return faceNormals[faceOffset(cube, face) + 2];
    }

    public boolean shouldReverseWinding(int cube, int face) {
        return (reversedWindingMasks[cube] & (1 << face)) != 0;
    }

    public boolean hasWindingCorrections() {
        return hasWindingCorrections;
    }

    public int cubeCount() {
        return reversedWindingMasks.length;
    }

    public static int faceOffset(int cube, int face) {
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
