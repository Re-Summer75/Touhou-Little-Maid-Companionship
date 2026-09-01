package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Anchor;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel.Stride;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel
        .VoxelPath;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 多边形层的走路方案：面级路线折点翻成一条只含走边的自有路径。
 *
 * <p>与锚点图的分工照 Recast 的经典分法——**多边形管走，合同边管跳**。
 * 折点只落在门上，而门是两块可走面的共享边界，所以这条线天然在可走区域
 * 之内，不必再靠预演、边距、刹停去拦"别走出台沿"。
 *
 * <p>眼下只在锚点图交白卷时接手：柱旁那条 0.075 的中心可行域，点锚看不
 * 见，面看得见——挤缝一族（窄道柱、柱旁车道、崖沿柱）正是死在这上头。
 */
public final class MeshWalk {

    /** 局部网格的半径与竖直带宽：跟随距离量级，够用即弃。 */
    private static final int REACH = 12;
    private static final int BAND = 2;

    private MeshWalk() {
    }

    /** 雕刻缓存：同一只、中心偏移两格内、两秒内复用。雕刻是兜底最贵
     *  的一段（实机卡栏外连发：每次换件都现雕 25×25，单价百毫秒量级，
     *  P 点臂 74 次总账十四秒）；键放两格宽——沿栏蹭步换格就重雕的话
     *  缓存形同虚设，网格半径十二格，中心偏两格覆盖率照旧够。 */
    private static final java.util.Map<Mob, Carved> CARVED =
            new java.util.WeakHashMap<>();

    private record Carved(net.minecraft.core.BlockPos center, long stamp,
            SurfaceMesh mesh) {
    }

    /** 铺一条纯走路的路；铺不出返回 null。 */
    public static VoxelPath plan(Level level, Mob mob, Vec3 goal) {
        var here = mob.blockPosition();
        long now = level.getGameTime();
        Carved kept = CARVED.get(mob);
        boolean fresh = kept == null || kept.center.distSqr(here) > 4.0D
                || now - kept.stamp >= 40L;
        // 现雕要先问预算：雕刻自身不可中断，余额不够就这单不雕，
        // 交给锚图的诚实前沿；下一 tick 预算回满再雕不迟。
        if (fresh && com.laixia.maidintelligence.feature.behavior.tlm.pathing
                .voxel.VoxelAstar.headroom(now) < 5_000_000L) {
            return null;
        }
        SurfaceMesh mesh;
        if (kept != null && kept.center.distSqr(here) <= 4.0D
                && now - kept.stamp < 40L) {
            mesh = kept.mesh;
        } else {
            mesh = SurfaceMesh.around(level, here,
                    REACH, BAND, mob.getBbWidth(), mob.getBbHeight());
            CARVED.put(mob, new Carved(here, now, mesh));
        }
        List<Vec3> line = MeshRoute.plan(mesh, mob.position(), goal);
        if (line.size() < 2) {
            return null;
        }
        List<Anchor> anchors = new ArrayList<>(line.size());
        List<Stride> strides = new ArrayList<>(line.size() - 1);
        for (Vec3 point : line) {
            anchors.add(new Anchor(point, 1.0D, Anchor.Kind.FLOOR,
                    new AABB(point.x - 0.05D, point.y, point.z - 0.05D,
                            point.x + 0.05D, point.y, point.z + 0.05D)));
            if (anchors.size() > 1) {
                strides.add(Stride.WALK_PACE);
            }
        }
        // 面内路线的全部折点打一次：卡住时要看的是"她被指到哪儿去"，
        // 而供词里的下一站只有一个点，看不出整条线的形状。
        StringBuilder shape = new StringBuilder("[mesh-line]");
        for (Vec3 point : line) {
            shape.append(String.format(" (%.2f,%.2f)", point.x, point.z));
        }
        System.out.println(shape);
        return new VoxelPath(anchors, strides, true, true);
    }
}
