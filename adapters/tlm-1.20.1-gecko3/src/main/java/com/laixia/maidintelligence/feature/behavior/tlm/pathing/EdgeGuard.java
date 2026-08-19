package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 速度看护：崖边先问再管、下坡入口收速。
 *
 * <p>从 {@code SegmentedPathwalk} 按职责拆出（单文件五百行的布局纪律）：
 * 执行器回答"这一段怎么走"，这里只回答"这股动量现在危不危险"。两条规则都
 * 是读数带上真摔出来的：光收速拦不住移动控制一 tick 一 tick 把人蠕出崖沿
 * （要刹死弃路）；迈下台阶滞空的两三 tick 是全速漂移（要在入口收速）。
 */
final class EdgeGuard {
    /** 崖边收步的速度上限；低于它的移动本来就冲不出去。包内共享。 */
    static final double EDGE_TROT = 0.13D;

    /** 下坡入口的速度上限：滞空漂移正好落在本格内，不替 AI 多走半格。 */
    private static final double DOWNHILL_TROT = 0.2D;

    private final Mob mob;
    private final SureFootedNavigation nav;

    EdgeGuard(Mob mob, SureFootedNavigation nav) {
        this.mob = mob;
        this.nav = nav;
    }

    /**
     * 崖边看护：脚前方将踏进两格以上的落差、身上又带着冲劲——先问这是不是
     * **正走向一次要起的跳**。是助跑就只收速放行，起跳机构马上接手；不是
     * （斜切、终点滑行、转身甩劲、被撞）就刹死并弃路，下一 tick 从站定的
     * 脚下重铺。脚前是地板、要登的立面、或只降一格的台阶都不算崖。
     *
     * @return true 表示这一 tick 已被看护接管（刹停弃路），调用方不必再管。
     */
    boolean watch(boolean hasPath) {
        if (!mob.onGround() || mob.isInWater()) {
            return false;
        }
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed <= EDGE_TROT + 0.02D) {
            return false;
        }
        double aheadX = mob.getX() + motion.x / speed * 0.8D;
        double aheadZ = mob.getZ() + motion.z / speed * 0.8D;
        BlockPos toe = BlockPos.containing(
                aheadX, mob.getY() - 0.5D, aheadZ
        );
        if (FootingRule.coversCenter(mob.level(), toe)
                || FootingRule.coversCenter(
                        mob.level(), toe.above())
                || FootingRule.coversCenter(
                        mob.level(), toe.below())) {
            return false;
        }
        if (hasPath && (walkingAtTheNextNode(motion, speed)
                || runningUpToALeap(motion, speed))) {
            mob.setDeltaMovement(
                    motion.x / speed * EDGE_TROT,
                    motion.y,
                    motion.z / speed * EDGE_TROT
            );
            return false;
        }
        mob.setDeltaMovement(0.0D, motion.y, 0.0D);
        nav.stop();
        return true;
    }

    /** 下坡入口收速：落脚格是尽头收到步速，有续路收到小跑。 */
    void descentEntry() {
        Vec3 motion = mob.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        if (speed <= DOWNHILL_TROT) {
            return;
        }
        double aheadX = mob.getX() + motion.x / speed * 1.8D;
        double aheadZ = mob.getZ() + motion.z / speed * 1.8D;
        BlockPos onward = BlockPos.containing(
                aheadX, mob.getY() - 1.5D, aheadZ
        );
        boolean deadEnd = !FootingRule.coversCenter(
                        mob.level(), onward)
                && !FootingRule.coversCenter(
                        mob.level(), onward.below());
        double cap = deadEnd ? EDGE_TROT : DOWNHILL_TROT;
        if (speed > cap) {
            mob.setDeltaMovement(
                    motion.x / speed * cap, motion.y, motion.z / speed * cap
            );
        }
    }

    /**
     * 这股动量是不是正走向路径的贴身真节点（对角下行的顶台角、缺口柱唇沿
     * 的前一步都算）：目标脚下有真地板、就在两格内、方向大体对。
     */
    private boolean walkingAtTheNextNode(Vec3 motion, double speed) {
        Path path = nav.getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        BlockPos next = path.getNextNodePos();
        int dy = next.getY() - mob.blockPosition().getY();
        if (dy < -1 || dy > 1) {
            return false;
        }
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        if (flat > 2.0D
                || motion.x * toX + motion.z * toZ < 0.5D * speed * flat) {
            return false;
        }
        return FootingRule.coversCenter(
                mob.level(), next.below());
    }

    /** 这股朝崖的动量是不是一次要起的跳的助跑（下个节点是验证过的跳线）。 */
    private boolean runningUpToALeap(Vec3 motion, double speed) {
        Path path = nav.getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        BlockPos next = path.getNextNodePos();
        BlockPos here = mob.blockPosition();
        int dy = next.getY() - here.getY();
        if (dy < -1 || dy > 1) {
            return false;
        }
        double toX = next.getX() + 0.5D - mob.getX();
        double toZ = next.getZ() + 0.5D - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        if (flat < 1.2D
                || motion.x * toX + motion.z * toZ < 0.7D * speed * flat) {
            return false;
        }
        int dx = next.getX() - here.getX();
        int dz = next.getZ() - here.getZ();
        int span = Math.max(Math.abs(dx), Math.abs(dz));
        boolean cardinal = (dx == 0) ^ (dz == 0);
        boolean spanFits = dy == 1
                ? span <= SafeFootingNodeEvaluator.UP_HOP_MAX_REACH
                : span >= 2
                        && span <= SafeFootingNodeEvaluator.MAX_GAP_SPAN + 1;
        if (!cardinal || !spanFits) {
            return false;
        }
        return FootingRule.coversCenter(
                mob.level(), next.below());
    }
}
