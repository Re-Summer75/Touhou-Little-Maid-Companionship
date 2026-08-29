package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 漏斗算法：在一串门里把绳子拉直。
 *
 * <p>多边形寻路的收尾一步，也是它比格图强的地方所在：A* 给出的是"经过
 * 哪几块面"，真正要走的线由**门的左右两端**夹出来——绳子绷紧时贴着的
 * 那些拐点就是路径的折点，其余全是直线。拐点必然落在门上，而门是两块
 * 可走面共享的那段边界，所以**整条路线天然在可走区域之内**：这正是点
 * 锚年代要靠判据去拦的"别走出台沿"，在这里是构造保证。
 *
 * <p>课本算法（Simple Stupid Funnel），只是把二维换成水平面，高度沿用
 * 门所在面的高度。
 */
public final class Funnel {

    private Funnel() {
    }

    /**
     * @param start  起点（水平坐标有效）
     * @param goal   终点
     * @param gates  沿途的门，按顺序
     * @return 拉直后的折点序列（含起点与终点）
     */
    public static List<Vec3> pull(Vec3 start, Vec3 goal,
            List<Surface.Portal> gates) {
        List<Vec3> out = new ArrayList<>();
        out.add(start);
        if (gates.isEmpty()) {
            out.add(goal);
            return out;
        }
        Vec3 apex = start;
        Vec3 left = new Vec3(gates.get(0).leftX(), start.y,
                gates.get(0).leftZ());
        Vec3 right = new Vec3(gates.get(0).rightX(), start.y,
                gates.get(0).rightZ());
        int leftAt = 0;
        int rightAt = 0;
        int i = 1;
        while (i <= gates.size()) {
            Vec3 nextLeft = i == gates.size() ? goal
                    : new Vec3(gates.get(i).leftX(), start.y,
                            gates.get(i).leftZ());
            Vec3 nextRight = i == gates.size() ? goal
                    : new Vec3(gates.get(i).rightX(), start.y,
                            gates.get(i).rightZ());
            // 右边先收紧
            if (cross(apex, right, nextRight) <= 0.0D) {
                if (same(apex, right) || cross(apex, left, nextRight) > 0.0D) {
                    right = nextRight;
                    rightAt = i;
                } else {
                    // 右越过左：左端点成为新的拐点，从它重新起漏斗
                    out.add(left);
                    apex = left;
                    i = leftAt + 1;
                    left = apex;
                    right = apex;
                    leftAt = i;
                    rightAt = i;
                    continue;
                }
            }
            // 左边收紧
            if (cross(apex, left, nextLeft) >= 0.0D) {
                if (same(apex, left) || cross(apex, right, nextLeft) < 0.0D) {
                    left = nextLeft;
                    leftAt = i;
                } else {
                    out.add(right);
                    apex = right;
                    i = rightAt + 1;
                    left = apex;
                    right = apex;
                    leftAt = i;
                    rightAt = i;
                    continue;
                }
            }
            i++;
        }
        out.add(goal);
        return out;
    }

    /** 水平面上的叉积：判左右。 */
    private static double cross(Vec3 origin, Vec3 a, Vec3 b) {
        return (a.x - origin.x) * (b.z - origin.z)
                - (b.x - origin.x) * (a.z - origin.z);
    }

    private static boolean same(Vec3 a, Vec3 b) {
        return Math.abs(a.x - b.x) < 1.0E-6D
                && Math.abs(a.z - b.z) < 1.0E-6D;
    }
}
