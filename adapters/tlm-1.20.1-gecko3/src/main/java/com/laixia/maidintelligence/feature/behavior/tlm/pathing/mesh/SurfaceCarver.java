package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一格的地面**雕**成可走多边形：顶面减去挡路的东西，剩下的才是路。
 *
 * <p>点锚年代问的是"这一格能不能站"，答案是一个点，于是柱占了格心就等
 * 于整格不可走——挤缝要靠特例、贴边要靠采样、走出台沿要靠判据去拦。改
 * 问"这一格**哪些地方**能站"，答案是若干矩形：栅栏柱只占中间四分之一，
 * 减完剩下四条边带，其中够宽的那些天生就是路。
 *
 * <p>只减**挡身位的**东西：顶面之上、身位高度之内有碰撞的那部分。地毯、
 * 半砖这些"踩上去就是新地面"的不算障碍，它们本来就以自己的顶面参与。
 */
public final class SurfaceCarver {

    /**
     * 边带窄到这个数以下就当没有。
     *
     * <p>**注意这里量的是"她中心能落在哪"，不是"缝有多宽"**：障碍占地
     * 已经外扩了半个身位，剩下的矩形本来就是中心的可行域。栅栏柱占
     * 0.375–0.625，外扩后剩两条 0.075 的带子——那正是玩家说的"贴边绕过
     * 去"：中心贴着格边走，身子有一半悬在道外。按 0.15 一刀切会把它减
     * 没（雕刻钉子当场判红，柱旁一条路都不剩）。留一点余量给浮点，别
     * 留成门槛。
     */
    private static final double LEAST_LANE = 0.02D;

    private SurfaceCarver() {
    }

    /**
     * 这一格里所有可走的地面片。
     *
     * @param cell   要雕的格
     * @param width  她的身位宽
     * @param height 她的身位高
     */
    public static List<Surface> carve(BlockGetter level, BlockPos cell,
            double width, double height) {
        List<Surface> out = new ArrayList<>(2);
        for (BlockPos host : new BlockPos[]{cell, cell.below()}) {
            VoxelShape shape = level.getBlockState(host)
                    .getCollisionShape(level, host);
            if (shape.isEmpty()) {
                continue;
            }
            for (AABB box : shape.toAabbs()) {
                AABB placed = box.move(
                        host.getX(), host.getY(), host.getZ());
                double top = placed.maxY;
                // 顶面落在本格的高度带里才归本格——归属律不必背，它是
                // 几何的直接推论。
                if (top < cell.getY() - 1.0E-3D
                        || top >= cell.getY() + 1.0D - 1.0E-6D) {
                    continue;
                }
                carveOne(level, cell, placed, top, width, height, out);
            }
        }
        return out;
    }

    /** 一块顶面减去它上方的障碍，剩下的矩形加进 {@code out}。 */
    private static void carveOne(BlockGetter level, BlockPos cell,
            AABB pad, double top, double width, double height,
            List<Surface> out) {
        List<Surface> pieces = new ArrayList<>(1);
        pieces.add(new Surface(pad.minX, pad.minZ, pad.maxX, pad.maxZ, top));
        for (AABB blocker : blockers(level, cell, top, height)) {
            List<Surface> next = new ArrayList<>(pieces.size() + 2);
            for (Surface piece : pieces) {
                subtract(piece, blocker, width, next);
            }
            pieces = next;
            if (pieces.isEmpty()) {
                return;
            }
        }
        for (Surface piece : pieces) {
            if (piece.breadth() >= LEAST_LANE) {
                out.add(piece);
            }
        }
    }

    /** 这块地面之上、身位高度之内挡路的碰撞盒（含邻格探进来的）。 */
    private static List<AABB> blockers(BlockGetter level, BlockPos cell,
            double top, double height) {
        List<AABB> found = new ArrayList<>();
        AABB band = new AABB(
                cell.getX() - 1.0D, top + 0.02D, cell.getZ() - 1.0D,
                cell.getX() + 2.0D, top + height, cell.getZ() + 2.0D);
        for (BlockPos at : BlockPos.betweenClosed(
                new BlockPos(cell.getX() - 1, (int) Math.floor(top),
                        cell.getZ() - 1),
                new BlockPos(cell.getX() + 1,
                        (int) Math.floor(top + height), cell.getZ() + 1))) {
            VoxelShape shape = level.getBlockState(at)
                    .getCollisionShape(level, at);
            if (shape.isEmpty()) {
                continue;
            }
            BlockPos frozen = at.immutable();
            for (AABB box : shape.toAabbs()) {
                AABB placed = box.move(
                        frozen.getX(), frozen.getY(), frozen.getZ());
                if (placed.maxY > top + 0.02D && placed.minY < top + height
                        && placed.intersects(band)) {
                    found.add(placed);
                }
            }
        }
        return found;
    }

    /**
     * 一块地面减去一个障碍：障碍的**占地**再向外让出半个身位（她有宽
     * 度，贴着障碍站不下），剩下四条边带里够宽的留下。
     */
    private static void subtract(Surface piece, AABB blocker, double width,
            List<Surface> out) {
        double half = width / 2.0D;
        double bx0 = blocker.minX - half;
        double bx1 = blocker.maxX + half;
        double bz0 = blocker.minZ - half;
        double bz1 = blocker.maxZ + half;
        if (bx1 <= piece.minX() || bx0 >= piece.maxX()
                || bz1 <= piece.minZ() || bz0 >= piece.maxZ()) {
            out.add(piece);
            return;
        }
        double top = piece.top();
        if (bx0 > piece.minX()) {
            out.add(new Surface(piece.minX(), piece.minZ(),
                    Math.min(bx0, piece.maxX()), piece.maxZ(), top));
        }
        if (bx1 < piece.maxX()) {
            out.add(new Surface(Math.max(bx1, piece.minX()), piece.minZ(),
                    piece.maxX(), piece.maxZ(), top));
        }
        double innerX0 = Math.max(piece.minX(), bx0);
        double innerX1 = Math.min(piece.maxX(), bx1);
        if (innerX1 > innerX0) {
            if (bz0 > piece.minZ()) {
                out.add(new Surface(innerX0, piece.minZ(), innerX1,
                        Math.min(bz0, piece.maxZ()), top));
            }
            if (bz1 < piece.maxZ()) {
                out.add(new Surface(innerX0, Math.max(bz1, piece.minZ()),
                        innerX1, piece.maxZ(), top));
            }
        }
    }
}
