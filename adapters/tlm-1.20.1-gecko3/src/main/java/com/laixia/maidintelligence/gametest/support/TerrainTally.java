package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 地形四列，每一列回答一个不同的问题。
 *
 * <p>竞技场铺平的时候，距离、间距、够得着的比例这些读数全是直线量，而那正好够用。
 * 地板不平之后不够了：**"她站得远"可能是走位，也可能是根本走不过去**，而这两件事
 * 要改的地方毫无关系。
 *
 * <ul>
 *   <li><b>高度差</b>——她和最近那个差几格。触及是球，这一格是从水平距离里扣掉的，
 *       所以站在坎下等于凭空缩短自己的攻击距离；
 *   <li><b>走不到</b>——{@code CANT_REACH_WALK_TARGET_SINCE} 置位的 tick 数。这是
 *       原版寻路失败后自己写进脑子的结论；
 *   <li><b>卡住</b>——有目的地却几乎没动的 tick 数。与上一列分开：走不到是"算不出
 *       路"，卡住是"算出了路却走不动"；
 *   <li><b>绕路比</b>——剩余路径节点数 ÷ 直线距离。平地是 1.0。所有速度比较（能不能
 *       脱离、追不追得上）都假设走直线，这一列说的正是那个假设错得有多离谱。
 * </ul>
 */
public final class TerrainTally {
    /** 一 tick 走不到这么远就算没动——寻路正常时每 tick 约零点二格。 */
    private static final double STUCK_STEP = 0.05D;

    /** 直线短于一格时比值没有意义，跳过。 */
    private static final double MEANINGFUL_LINE = 1.0D;

    private double height;

    private int heightTicks;

    private int unreachable;

    private int stuck;

    private double detour;

    private int detourTicks;

    private Vec3 previous;

    /** 有目的地却撞在方块面上的 tick 数——上一格之前那一下"撞停"。 */
    private int stalled;

    /** 离地期间的水平速度合计，与它的分母。 */
    private double airSpeed;

    private int airTicks;

    public TerrainTally(EntityMaid maid) {
        this.previous = maid.position();
    }

    /** 采一 tick。{@code nearest} 可以为 null——场上没活口时高度差无从谈起。 */
    public void sample(EntityMaid maid, LivingEntity nearest) {
        if (nearest != null && nearest.isAlive()) {
            height += Math.abs(maid.getY() - nearest.getY());
            heightTicks++;
        }
        if (maid.getBrain().hasMemoryValue(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            unreachable++;
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && maid.position().distanceToSqr(previous)
                        < STUCK_STEP * STUCK_STEP) {
            stuck++;
        }
        if (maid.horizontalCollision
                && maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            stalled++;
        }
        if (!maid.onGround()) {
            airSpeed += Math.hypot(
                    maid.getDeltaMovement().x, maid.getDeltaMovement().z
            );
            airTicks++;
        }
        previous = maid.position();
        Path path = maid.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return;
        }
        double straight = maid.position()
                .distanceTo(Vec3.atCenterOf(path.getTarget()));
        int remaining = path.getNodeCount() - path.getNextNodeIndex();
        if (straight >= MEANINGFUL_LINE && remaining > 0) {
            detour += remaining / straight;
            detourTicks++;
        }
    }

    /** 一行读完。 */
    public String line() {
        return String.format(
                "height=%.2f unreachable=%dt stuck=%dt detour=%.2f "
                        + "stalled=%dt airSpeed=%.3f",
                heightTicks == 0 ? -1.0D : height / heightTicks,
                unreachable,
                stuck,
                detourTicks == 0 ? -1.0D : detour / detourTicks,
                stalled,
                airTicks == 0 ? -1.0D : airSpeed / airTicks
        );
    }
}
