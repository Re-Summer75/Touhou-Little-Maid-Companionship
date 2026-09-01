package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import java.util.List;

import net.minecraft.world.phys.Vec3;

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
    /** 零位移看门的账本：上次盘点的位置与原地冻结的 tick 数。 */
    private double watchX = Double.NaN;
    private double watchZ;
    private int frozenTicks;

    /**
     * 这条路是不是走不动了：贴地执行中连续三十 tick 没挪半步（怼着上
     * 不去的高差、卡在角上、被人群挤死）。走丢量的是离线多远，量不到
     * "人贴着线原地怼"；换单节流句句复用旧答卷——上层全瞎（沉门板案
     * t7..t153：起点在空中解错、随后一百四十七 tick 无人发现她在怼壁，
     * 差事耐心到点才解围）。路自己数位移最诚实；三十 tick 比起跳酝酿
     * 的拉锯长，不误伤。
     */
    public boolean dragging(double x, double z) {
        if (Double.isNaN(watchX)
                || Math.hypot(x - watchX, z - watchZ) >= 0.02D) {
            watchX = x;
            watchZ = z;
            frozenTicks = 0;
            return false;
        }
        return ++frozenTicks >= 30;
    }

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

    /**
     * 下一站要拐多少度（没有下一段、或下一段不是走，就是零）。
     *
     * <p>放在路径上而不是执行器里：问的全是路径自己的数据（游标、前后两
     * 条腿的方向），执行器只是消费者。
     */
    public double turnAt() {
        if (!alive() || cursor + 1 >= anchors.size()
                || strides.get(cursor).move() != Stride.Move.WALK) {
            return 0.0D;
        }
        Anchor here = from();
        Anchor corner = anchors.get(cursor);
        Anchor after = anchors.get(cursor + 1);
        double ax = corner.at().x - here.at().x;
        double az = corner.at().z - here.at().z;
        double bx = after.at().x - corner.at().x;
        double bz = after.at().z - corner.at().z;
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        if (la < 1.0E-4D || lb < 1.0E-4D) {
            return 0.0D;
        }
        double cos = (ax * bx + az * bz) / (la * lb);
        return Math.toDegrees(Math.acos(
                Math.max(-1.0D, Math.min(1.0D, cos))));
    }

    /** 这一步两端支撑的窄边——步速的几何上限按它收。 */
    public double seatWidth() {
        return alive() ? Math.min(from().breadth(), next().breadth()) : 1.0D;
    }

    /** 她偏离当前这条腿多远（点到线段的水平距离）。 */
    public double offLeg(double px, double pz) {
        if (!alive()) {
            return 0.0D;
        }
        Vec3 a = from().at();
        Vec3 b = next().at();
        double abx = b.x - a.x;
        double abz = b.z - a.z;
        double len2 = abx * abx + abz * abz;
        if (len2 < 1.0E-8D) {
            return Math.hypot(px - a.x, pz - a.z);
        }
        double t = ((px - a.x) * abx + (pz - a.z) * abz) / len2;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (a.x + abx * t), pz - (a.z + abz * t));
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
