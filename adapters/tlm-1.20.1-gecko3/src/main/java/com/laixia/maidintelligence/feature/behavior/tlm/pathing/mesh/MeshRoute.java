package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 面级 A*：在可走多边形之间走门，再用漏斗把绳子拉直。
 *
 * <p>与锚点图的分工是 Recast 的经典分法：**多边形管走，合同边管跳**。
 * 走路这一半在这里彻底换了表示——节点是面、边是门、代价是门到门的直线
 * 距离，最后交给 {@link Funnel} 拉出折点。折点只落在门上，而门是两块可
 * 走面共享的边界，所以整条线天然在可走区域内：点锚年代要靠预演、边距、
 * 刹停去拦的"别走出台沿"，在这里不必再问。
 */
public final class MeshRoute {

    /** 展开上限：局部网格本就只有几百块面，这个数是几倍余量。 */
    private static final int VISIT_BUDGET = 1024;

    private MeshRoute() {
    }

    /**
     * 从 {@code from} 走到 {@code goal} 的折点序列；走不到返回空表。
     *
     * <p>只回答"纯走路能不能到"。要跳、要爬、要落的路线不归它管——那些
     * 带着初速合同，仍由锚点图给出。
     */
    public static List<Vec3> plan(SurfaceMesh mesh, Vec3 from, Vec3 goal) {
        int start = mesh.locate(from.x, from.y, from.z);
        int end = mesh.locate(goal.x, goal.y, goal.z);
        if (start < 0 || end < 0) {
            return List.of();
        }
        if (start == end) {
            return List.of(from, mesh.pad(end).clamp(goal.x, goal.z));
        }
        record Open(int pad, double f) {
        }
        Map<Integer, Double> cost = new HashMap<>();
        Map<Integer, Integer> cameFrom = new HashMap<>();
        Map<Integer, Surface.Portal> through = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>(
                (a, b) -> Double.compare(a.f, b.f));
        cost.put(start, 0.0D);
        open.add(new Open(start, 0.0D));
        int visited = 0;
        boolean reached = false;
        while (!open.isEmpty() && visited < VISIT_BUDGET) {
            int here = open.poll().pad;
            visited++;
            if (here == end) {
                reached = true;
                break;
            }
            Double gHere = cost.get(here);
            if (gHere == null) {
                continue;
            }
            for (SurfaceMesh.Link link : mesh.from(here)) {
                double step = Math.hypot(
                        mesh.pad(link.to()).centerX()
                                - mesh.pad(here).centerX(),
                        mesh.pad(link.to()).centerZ()
                                - mesh.pad(here).centerZ());
                double g = gHere + step;
                Double known = cost.get(link.to());
                if (known != null && known <= g) {
                    continue;
                }
                cost.put(link.to(), g);
                cameFrom.put(link.to(), here);
                through.put(link.to(), link.portal());
                open.add(new Open(link.to(), g + heuristic(
                        mesh.pad(link.to()), goal)));
            }
        }
        if (!reached) {
            return List.of();
        }
        List<Surface.Portal> gates = new ArrayList<>();
        for (int at = end; at != start; ) {
            Surface.Portal gate = through.get(at);
            Integer up = cameFrom.get(at);
            if (gate == null || up == null) {
                return List.of();
            }
            gates.add(0, orient(gate, mesh.pad(up), mesh.pad(at)));
            at = up;
        }
        // **每道门都留一个必经点**，不做拉直。
        //
        // 漏斗把绳子拉直，理论上折点落在门上、路线全在面内；可实测她仍
        // 从格心斜切进柱子（挤缝三案：path=2/5，那一腿在 x=221.375 处
        // z≈261.24，半个身子压在柱上）——绳子拉得过直，穿过了门却没贴
        // 住门。车道只有 0.075 宽，容不得这点误差。先要正确：逐门取点，
        // 每一步都从门里过；路会稍长，平滑留给以后，且要有钉子看着。
        List<Vec3> line = new ArrayList<>();
        line.add(from);
        Vec3 last = from;
        for (Surface.Portal gate : gates) {
            Vec3 step = nearestOn(gate, last);
            line.add(step);
            last = step;
        }
        line.add(mesh.pad(end).clamp(goal.x, goal.z));
        return line;
    }

    /**
     * 把门的两端**按行进方向摆正**：左在左、右在右。
     *
     * <p>漏斗算法全靠左右夹角收紧，端点一旦颠倒，夹角判断就失效——路径
     * 不再在门口打弯，而是从起点直奔下一站，斜着切进本该绕开的柱子（挤
     * 缝实测：她从格心直奔车道点，中途撞柱，卡在第二腿）。求门时不知道
     * 会从哪边过，摆正只能在用它的时候做。
     */
    private static Surface.Portal orient(Surface.Portal gate, Surface from,
            Surface to) {
        double dirX = to.centerX() - from.centerX();
        double dirZ = to.centerZ() - from.centerZ();
        double spanX = gate.rightX() - gate.leftX();
        double spanZ = gate.rightZ() - gate.leftZ();
        // 叉积为负说明"右"在行进方向的左侧，掉个个儿。
        if (dirX * spanZ - dirZ * spanX < 0.0D) {
            return new Surface.Portal(gate.rightX(), gate.rightZ(),
                    gate.leftX(), gate.leftZ());
        }
        return gate;
    }

    /** 门段上离给定点最近的位置：既在门里，又不绕远。 */
    private static Vec3 nearestOn(Surface.Portal gate, Vec3 from) {
        double ax = gate.leftX();
        double az = gate.leftZ();
        double bx = gate.rightX() - ax;
        double bz = gate.rightZ() - az;
        double len2 = bx * bx + bz * bz;
        if (len2 < 1.0E-9D) {
            return new Vec3(ax, from.y, az);
        }
        double t = ((from.x - ax) * bx + (from.z - az) * bz) / len2;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return new Vec3(ax + bx * t, from.y, az + bz * t);
    }

    private static double heuristic(Surface pad, Vec3 goal) {
        return Math.hypot(pad.centerX() - goal.x, pad.centerZ() - goal.z);
    }
}
