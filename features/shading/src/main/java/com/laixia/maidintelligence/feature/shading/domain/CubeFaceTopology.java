package com.laixia.maidintelligence.feature.shading.domain;

/**
 * Canonical cube-face topology used by model adapters.
 *
 * <p>Face order is DOWN, UP, NORTH, SOUTH, WEST, EAST. Corner indices use
 * {@code Cxyz}: {@code 0=C000 1=C100 2=C110 3=C010
 * 4=C001 5=C101 6=C111 7=C011}.
 */
public final class CubeFaceTopology {
    public static final int FACE_COUNT = 6;

    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    /** Four corners in the established vertex emission order. */
    private static final int[][] CORNERS = {
            {5, 4, 0, 1},
            {2, 3, 7, 6},
            {1, 0, 3, 2},
            {4, 5, 6, 7},
            {0, 4, 7, 3},
            {5, 1, 2, 6},
    };

    /** Opposite face corners whose midpoint is the face center. */
    private static final int[][] DIAGONALS = {
            {5, 0},
            {2, 7},
            {1, 3},
            {4, 6},
            {0, 7},
            {5, 2},
    };

    /** Normal axis: 0=x, 1=y, 2=z. */
    private static final int[] AXES = {1, 1, 2, 2, 0, 0};
    private static final double[] SIGNS = {
            -1.0D, 1.0D, -1.0D, 1.0D, -1.0D, 1.0D
    };

    private CubeFaceTopology() {
    }

    public static int corner(int face, int vertex) {
        return CORNERS[face][vertex];
    }

    public static int diagonalStart(int face) {
        return DIAGONALS[face][0];
    }

    public static int diagonalEnd(int face) {
        return DIAGONALS[face][1];
    }

    public static int axis(int face) {
        return AXES[face];
    }

    public static double sign(int face) {
        return SIGNS[face];
    }
}
