package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 车道展开：把带侧向让开量的跳边，摊成路径上**两个真实站位**。
 *
 * <p>柱压在起跳格或落点格上时，格心到格心的那条弧线正撞柱心，可柱旁的
 * 侧缝三维里是空的——玩家自己就是侧一步跳过去的。图侧（{@code
 * SweptAcceptance.laneFor}）用扫掠仿真扫出可行区间、取中点，答出这条边
 * 该走哪条车道；这里把答案落成路径：
 *
 * <pre>
 *   出发面 ──侧移──▶ 车道起跳点 ──跳──▶ 车道落点 ──归中──▶ 落点面
 * </pre>
 *
 * <p>于是执行器一行都不必改：她照常走、照常跳，"先对齐缝再起跳，滞空锁
 * 沿车道的落点，落到柱后再走回中线"整套动作是路径本身长出来的，不是执
 * 行侧临场的又一条特判。
 *
 * <p>贴边锚第一版把"缝里能站人"直接塞进锚点图，结果她挤进墙缝再也出不
 * 来（栅栏圈整场只下过七次单）。当时的结论是"缺的不是约束，是结构：缝
 * 该成对出现、并且必须连通两侧的正经站位"——跳边天生就有这个结构，两端
 * 都是正经格心锚点，车道只活在这一跳里，落地就归中。
 */
public final class LeapLanes {

    /**
     * 车道站位的口径宽度：判到与回锚按它收。
     *
     * <p>0.2 让执行侧的判到圈收到 0.10（{@code standMargin} 的最紧一
     * 档）——车道点若按满面口径判到，她站在格心原地就算"已经到车道
     * 上"，侧移这一步根本不会发生。这也正是图侧要求车道至少 0.1875 宽
     * 的来处：两边说的是同一个精度。
     */
    private static final double LANE_BREADTH = 0.2D;

    private LeapLanes() {
    }

    /** 把路径里的车道跳展开；没有车道跳就原样奉还（常态，零开销）。 */
    public static VoxelPath spread(VoxelPath path) {
        if (path == null) {
            return null;
        }
        boolean any = false;
        for (int i = 0; i + 1 < path.length(); i++) {
            Stride st = path.strideAt(i);
            if (st.move() == Stride.Move.LEAP && st.lane() != 0.0D) {
                any = true;
                break;
            }
        }
        if (!any) {
            return path;
        }
        List<Anchor> anchors = new ArrayList<>();
        List<Stride> strides = new ArrayList<>();
        anchors.add(path.anchorAt(0));
        for (int i = 0; i + 1 < path.length(); i++) {
            Anchor from = path.anchorAt(i);
            Anchor to = path.anchorAt(i + 1);
            Stride st = path.strideAt(i);
            double dx = to.at().x - from.at().x;
            double dz = to.at().z - from.at().z;
            double flat = Math.hypot(dx, dz);
            if (st.move() != Stride.Move.LEAP || st.lane() == 0.0D
                    || flat < 1.0E-6D) {
                anchors.add(to);
                strides.add(st);
                continue;
            }
            // 左手侧法向乘让开量：起跳点与落点**同时**平移，整条弧线搬
            // 进侧缝——与图侧验收时用的是同一个平移。
            double offX = -dz / flat * st.lane();
            double offZ = dx / flat * st.lane();
            boolean alongX = Math.abs(dx) >= Math.abs(dz);
            System.out.println("[voxel-lane] " + String.format("%.3f",
                    st.lane()) + " from=" + from.at() + " to=" + to.at());
            anchors.add(onLane(from, offX, offZ, alongX));
            strides.add(Stride.WALK_PACE);
            anchors.add(onLane(to, offX, offZ, alongX));
            strides.add(st);
            anchors.add(to);
            strides.add(Stride.WALK_PACE);
        }
        return new VoxelPath(anchors, strides, path.reaches());
    }

    /**
     * 面上的车道站位：代表点侧移，支撑面收成**一条车道带**——垂直于跳向
     * 那一轴收到零宽（判到就问"上没上道"），沿跳向保留原面（贴沿前挪不
     * 算走丢，那正是起跳该做的事）。
     *
     * <p>带按主轴收：跳向斜着走时轴对齐的盒子摆不出真正的斜带，取分量大
     * 的那一轴近似——斜跳的车道本就少见，近似偏保守（判到更严）。
     */
    private static Anchor onLane(Anchor face, double offX, double offZ,
            boolean alongX) {
        Vec3 at = new Vec3(face.at().x + offX, face.at().y,
                face.at().z + offZ);
        AABB seat = face.stand();
        AABB band = alongX
                ? new AABB(seat.minX, seat.minY, at.z,
                        seat.maxX, seat.maxY, at.z)
                : new AABB(at.x, seat.minY, seat.minZ,
                        at.x, seat.maxY, seat.maxZ);
        // 索引格沿用原面：车道点可能侧移进邻格，账本记的却该是这条边
        // 本来的那两格。
        return new Anchor(at, LANE_BREADTH, Anchor.Kind.EDGE, band,
                face.cell());
    }
}
