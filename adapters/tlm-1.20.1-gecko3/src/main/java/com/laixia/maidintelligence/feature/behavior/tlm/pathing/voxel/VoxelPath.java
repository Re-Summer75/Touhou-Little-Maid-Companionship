package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import java.util.List;

/**
 * 自有路径：锚点序列＋每步的边合同，带一个消费游标。
 *
 * <p>与原版 {@code Path} 的差别就是自有寻路的全部主张：节点是**锚点**
 * （亚格站位）而非格心，步与步之间带**动作类型与初速合同**而非让执行器
 * 反猜。到达语义（判到、销账）由执行器按锚点的支撑口径收，这里只管序列
 * 与游标。
 */
public final class VoxelPath {
    private final List<Anchor> anchors;
    private final List<Stride> strides;
    private final boolean reaches;

    /** 这条路是不是**面内约束**的（多边形层给的）。面内的路已由几何保
     *  证不出界，执行侧不必再逐步预演"会不会掉"——车道上她本就有半个
     *  身子悬在道外，每步都判会掉，人就只能按最慢档爬（挤缝实测：路铺
     *  对了却走不完时限）。 */
    private final boolean constrained;

    private int cursor;

    /**
     * @param anchors 站位序列（含起点）
     * @param strides 第 i 条合同描述 anchors[i]→anchors[i+1] 那一步，
     *                长度 = anchors.size() − 1
     * @param reaches 是否真到目标（false = 部分路径，前沿要诚实上报）
     */
    public VoxelPath(List<Anchor> anchors, List<Stride> strides,
            boolean reaches) {
        this(anchors, strides, reaches, false);
    }

    public VoxelPath(List<Anchor> anchors, List<Stride> strides,
            boolean reaches, boolean constrained) {
        this.anchors = List.copyOf(anchors);
        this.strides = List.copyOf(strides);
        this.reaches = reaches;
        this.constrained = constrained;
        this.cursor = 1;
    }

    /** 面内约束（多边形层给的路）。 */
    public boolean constrained() {
        return constrained;
    }

    /** 还有没走完的站。 */
    public boolean alive() {
        return cursor < anchors.size();
    }

    /** 当前要去的站。 */
    public Anchor next() {
        return anchors.get(cursor);
    }

    /** 去当前站这一步的合同。 */
    public Stride stride() {
        return strides.get(cursor - 1);
    }

    /** 上一站（这一步的出发地）。 */
    public Anchor from() {
        return anchors.get(cursor - 1);
    }

    /** 到站销账。 */
    public void advance() {
        cursor++;
    }

    /** 终点站。 */
    public Anchor end() {
        return anchors.get(anchors.size() - 1);
    }

    /** 真到目标，还是只到可达前沿。 */
    public boolean reaches() {
        return reaches;
    }

    public int length() {
        return anchors.size();
    }

    /** 第 {@code i} 站（门面投影用）。 */
    public Anchor anchorAt(int i) {
        return anchors.get(i);
    }

    /** 第 {@code i} 步的合同（拉直的读口）。 */
    public Stride strideAt(int i) {
        return strides.get(i);
    }

    public int cursor() {
        return cursor;
    }
}
