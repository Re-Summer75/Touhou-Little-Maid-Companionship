package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * 拥挤转向：ORCA 的轻量版——玩家会养几十只女仆，避障要按人群设计。
 *
 * <p>朴素的分离斥力在高密度下抖动、对推、门口死锁；互惠速度障碍
 * （ORCA）给每个邻居立一个半平面约束（都是女仆就各让一半，玩家与动物
 * 我们全让），期望速度顺序投影到约束边界上——几十上百个体的工业验证形
 * 态（DetourCrowd 同源）。这里取它的顺序投影简化：约束只收最近几个邻
 * 居，投影不做严格线性规划——密度再高一个量级前，这一版的稳定性够用。
 *
 * <p>邻居列表两 tick 缓存一次：几十只互相查询是 O(n²)，转向对半 tick
 * 的陈旧毫不敏感。
 */
public final class CrowdSteer {
    /** 只理会最近这几位邻居：ORCA 的约束数上限。 */
    private static final int NEIGHBORS = 5;

    /** 察觉半径（水平）。 */
    private static final double RANGE = 1.2D;

    /** 身位半径之和：两只 0.3 的实体贴脸的距离。 */
    private static final double CLEARANCE = 0.62D;

    /** 邻居缓存的保鲜期（tick）。 */
    private static final int CACHE_TICKS = 2;

    private final Mob mob;

    private List<LivingEntity> cached = List.of();
    private int cachedAt = -100;

    public CrowdSteer(Mob mob) {
        this.mob = mob;
    }

    /**
     * 把期望速度按人群修正：对每位近邻立一条"互惠让行"约束，顺序投影。
     * 返回长度 2 的数组 {vx, vz}；没邻居时原样返回。
     */
    public double[] steer(double vx, double vz) {
        List<LivingEntity> crowd = neighbors();
        if (crowd.isEmpty()) {
            return new double[]{vx, vz};
        }
        double outX = vx;
        double outZ = vz;
        for (LivingEntity other : crowd) {
            double px = other.getX() - mob.getX();
            double pz = other.getZ() - mob.getZ();
            double dist = Math.hypot(px, pz);
            if (dist < 1.0E-4D || dist > RANGE) {
                continue;
            }
            // 相对速度落在碰撞锥里才需要让：正对着挤过去的那部分速度，
            // 沿"离开对方"的法向剪掉一半（同伴互惠）或全部（外人）。
            double nx = px / dist;
            double nz = pz / dist;
            double closing = outX * nx + outZ * nz
                    - (other.getDeltaMovement().x * nx
                            + other.getDeltaMovement().z * nz);
            if (closing <= 0.0D) {
                continue;
            }
            double gap = dist - CLEARANCE;
            // 还很宽敞就只削凑近的劲，贴上了就往外让。
            double give = other instanceof EntityMaid ? 0.5D : 1.0D;
            if (gap < 0.0D) {
                outX += -nx * 0.06D * give - closing * nx * give;
                outZ += -nz * 0.06D * give - closing * nz * give;
            } else if (gap < 0.4D) {
                double cut = closing * (1.0D - gap / 0.4D) * give;
                outX -= cut * nx;
                outZ -= cut * nz;
            }
        }
        return new double[]{outX, outZ};
    }

    private List<LivingEntity> neighbors() {
        if (mob.tickCount - cachedAt < CACHE_TICKS) {
            return cached;
        }
        cachedAt = mob.tickCount;
        // 取**最近的**几位，不是遍历撞见的头几位：约束数封顶是 ORCA 的
        // 惯例，可挑谁进这几个名额才是它管用的原因——人堆里最该让的永
        // 远是贴得最近的那位，撞见的顺序说明不了任何事。
        //
        // （这里不建 KD 树：实体已由世界按区块 section 分格索引、随移动
        // 增量维护，固定半径查询只碰一两个格；RVO2 每步重建一棵树是因
        // 为它没有别的索引可用，我们有。）
        List<LivingEntity> found = new ArrayList<>();
        for (var other : mob.level().getEntities(mob,
                mob.getBoundingBox().inflate(RANGE, 0.5D, RANGE),
                e -> e instanceof LivingEntity)) {
            found.add((LivingEntity) other);
        }
        if (found.size() > NEIGHBORS) {
            found.sort((a, b) -> Double.compare(
                    flatSqr(a), flatSqr(b)));
            found = new ArrayList<>(found.subList(0, NEIGHBORS));
        }
        cached = found;
        return cached;
    }

    /** 到她的水平距离平方（挑名额用，不开方）。 */
    private double flatSqr(LivingEntity other) {
        double dx = other.getX() - mob.getX();
        double dz = other.getZ() - mob.getZ();
        return dx * dx + dz * dz;
    }
}
