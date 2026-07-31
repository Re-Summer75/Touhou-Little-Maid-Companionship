package com.laixia.maidintelligence.feature.interaction.client.capture;

import java.util.Arrays;

/**
 * Flat primitive capture buffer: positions keep the emitted double
 * precision, normals are always emitted as floats. One entry per vertex.
 */
final class YsmCapturedStream {
    private static final int MAX_VERTICES_PER_STREAM = 200_000;
    private static final int INITIAL_STREAM_CAPACITY = 64;

    private double[] positions = new double[INITIAL_STREAM_CAPACITY * 3];
    private float[] normals = new float[INITIAL_STREAM_CAPACITY * 3];
    private int vertexCount;

    void add(
            double x,
            double y,
            double z,
            float normalX,
            float normalY,
            float normalZ
    ) {
        if (vertexCount >= MAX_VERTICES_PER_STREAM) {
            return;
        }
        int base = vertexCount * 3;
        if (base == positions.length) {
            int grownVertices = Math.min(
                    vertexCount * 2,
                    MAX_VERTICES_PER_STREAM
            );
            positions = Arrays.copyOf(positions, grownVertices * 3);
            normals = Arrays.copyOf(normals, grownVertices * 3);
        }
        positions[base] = x;
        positions[base + 1] = y;
        positions[base + 2] = z;
        normals[base] = normalX;
        normals[base + 1] = normalY;
        normals[base + 2] = normalZ;
        vertexCount++;
    }

    double[] positions() {
        return positions;
    }

    float[] normals() {
        return normals;
    }

    int vertexCount() {
        return vertexCount;
    }
}
