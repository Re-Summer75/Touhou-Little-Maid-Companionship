package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可走多边形的**局部网格**：一片区域里所有能站的面，以及面与面之间的门。
 *
 * <p>点锚年代的图是"格→格"的邻接，可走与否要靠一串判据去逼近；这里是
 * "面→面"，门就是两块面共享边界上**真正走得过去的那一段**（够身位宽）。
 * 路径随后由漏斗算法在门序列里拉直，全程落在面内——"走出台沿"在构造上
 * 就不成立。
 *
 * <p>只建**局部**：一次规划要用多大就建多大（跟随距离量级在十几格），
 * 建好即用完即弃。方块一变，下次建的自然是新的，不必维护增量更新。
 */
public final class SurfaceMesh {

    private final List<Surface> pads = new ArrayList<>();
    private final Map<Integer, List<Link>> links = new HashMap<>();
    private final double bodyWidth;

    /** 一条通行关系：去哪块面、从哪道门过去。 */
    public record Link(int to, Surface.Portal portal) {
    }

    private SurfaceMesh(double bodyWidth) {
        this.bodyWidth = bodyWidth;
    }

    /**
     * 把 {@code around} 为中心、边长 {@code reach} 的一块地雕成网格。
     *
     * @param band 竖直方向上下各看多少格（台阶、矮坎要收进来）
     */
    public static SurfaceMesh around(BlockGetter level, BlockPos around,
            int reach, int band, double width, double height) {
        SurfaceMesh mesh = new SurfaceMesh(width);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -band; dy <= band; dy++) {
                    mesh.pads.addAll(SurfaceCarver.carve(level,
                            around.offset(dx, dy, dz), width, height));
                }
            }
        }
        mesh.stitch();
        return mesh;
    }

    /** 两两求门。局部网格规模在几百块，平方级足够便宜。 */
    private void stitch() {
        for (int i = 0; i < pads.size(); i++) {
            for (int j = i + 1; j < pads.size(); j++) {
                Surface.Portal gate = pads.get(i)
                        .portalTo(pads.get(j), bodyWidth);
                if (gate == null) {
                    continue;
                }
                links.computeIfAbsent(i, k -> new ArrayList<>())
                        .add(new Link(j, gate));
                links.computeIfAbsent(j, k -> new ArrayList<>())
                        .add(new Link(i, gate));
            }
        }
    }

    public Surface pad(int index) {
        return pads.get(index);
    }

    public int size() {
        return pads.size();
    }

    /** 从这块面出发能去哪儿。 */
    public List<Link> from(int index) {
        return links.getOrDefault(index, List.of());
    }

    /**
     * 这个位置踩在哪块面上：先找站得住的（点在面内且高度对得上），没有
     * 就退而求最近的一块。返回 −1 表示这片地里没有可走面。
     */
    public int locate(double x, double y, double z) {
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < pads.size(); i++) {
            Surface pad = pads.get(i);
            double drop = Math.abs(pad.top() - y);
            if (drop > 1.2D) {
                continue;
            }
            double score = pad.distance(x, z) + drop * 0.5D;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }
}
