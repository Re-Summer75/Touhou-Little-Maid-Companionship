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

    /** 铺一条纯走路的路；铺不出返回 null。 */
    public static VoxelPath plan(Level level, Mob mob, Vec3 goal) {
        SurfaceMesh mesh = SurfaceMesh.around(level, mob.blockPosition(),
                REACH, BAND, mob.getBbWidth(), mob.getBbHeight());
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
