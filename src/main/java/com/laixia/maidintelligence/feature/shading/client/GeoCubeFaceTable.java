package com.laixia.maidintelligence.feature.shading.client;

/**
 * Gecko 立方体六个面的拓扑常量。面的排列顺序与 {@code GeoMesh} 的存在位
 * （{@code 1 << face}）和 UV 槽位完全一致：DOWN、UP、NORTH、SOUTH、WEST、EAST。
 *
 * <p>角点编号沿用 TLM 渲染器的命名，下标含义为 {@code C<x><y><z>}：
 * {@code 0=C000 1=C100 2=C110 3=C010 4=C001 5=C101 6=C111 7=C011}。
 *
 * <p>这里的数值逐项抄自 {@code IGeoRenderer#renderCubesOfBone} 展开的六段顶点调用，
 * 表驱动改写必须与其字节级一致，否则会改变绕序与贴图朝向。
 */
public final class GeoCubeFaceTable {
    public static final int FACE_COUNT = 6;

    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    /** 每个面按原版发射顺序排列的四个角点。 */
    private static final int[][] CORNERS = {
            {5, 4, 0, 1},
            {2, 3, 7, 6},
            {1, 0, 3, 2},
            {4, 5, 6, 7},
            {0, 4, 7, 3},
            {5, 1, 2, 6},
    };

    /** 每个面取一条对角线的两个端点，两端点均值即实际面中心。 */
    private static final int[][] DIAGONAL = {
            {5, 0},
            {2, 7},
            {1, 3},
            {4, 6},
            {0, 7},
            {5, 2},
    };

    /** 面法线所在的立方体轴，0=nx、1=ny、2=nz。 */
    private static final int[] AXIS = {1, 1, 2, 2, 0, 0};

    /** 面法线相对该轴的符号。 */
    private static final float[] SIGN = {-1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F};

    private GeoCubeFaceTable() {
    }

    public static int corner(int face, int vertex) {
        return CORNERS[face][vertex];
    }

    public static int diagonalStart(int face) {
        return DIAGONAL[face][0];
    }

    public static int diagonalEnd(int face) {
        return DIAGONAL[face][1];
    }

    public static int axis(int face) {
        return AXIS[face];
    }

    public static float sign(int face) {
        return SIGN[face];
    }
}
