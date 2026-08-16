package com.laixia.maidintelligence.feature.perception.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitAttribute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 地上那件东西，她此刻会不会收走。
 *
 * <p>只此一份，因为它被问两次而且必须给同一个答案：扫描时用它挑目标，差事每 tick
 * 用它复验手上这一件还值不值得走。两处各写一遍的话，她会走向一件走到跟前才发现
 * 拿不了的东西——而"到了却没拿到"在清扫里是最难看的一种失败。
 */
public final class TlmLooseDrop {
    /**
     * 经验球落地后多久才收得走。
     *
     * <p>抄的是本体 {@code pickupXPOrb} 里那个门槛（{@code tickCount > 2}），
     * 而它抄的是原版留给经验球互相合并的窗口。
     */
    private static final int ORB_MERGE_TICKS = 2;

    /**
     * 一跳能多够到多高。
     *
     * <p>原版跳跃的顶点约一又四分之一格。算进"够不够得着"里，是因为她**确实**会
     * 跳：脚边高一级的台子上那件东西，寻路会自己带她跳上去。不算的话她会放着这类
     * 东西不管；算过头（比如按两格算）她又会去够永远拿不到的东西，那就是点头。
     */
    private static final double JUMP_REACH = 1.25D;

    /** 拾取半径读不到属性时的兜底，与本体给的默认值一致。 */
    private static final double DEFAULT_PICKUP_RANGE = 0.5D;

    private TlmLooseDrop() {
    }

    /**
     * 便宜的那一半：这**类**东西她原则上会不会捡。
     *
     * <p>拿来做实体扫描的谓词，所以里面不能有贵的判断——扫描要对范围内每个实体
     * 都问一遍。背包放不放得下留给 {@link #collectable}。
     */
    public static boolean couldBeTaken(Entity candidate) {
        if (!candidate.isAlive()) {
            return false;
        }
        if (candidate instanceof ItemEntity item) {
            return !item.hasPickUpDelay();
        }
        if (candidate instanceof ExperienceOrb) {
            // 刚落地的收不走：本体的 `pickupXPOrb` 要求它至少活过两 tick。
            return candidate.tickCount > ORB_MERGE_TICKS;
        }
        return candidate instanceof EntityPowerPoint point
                && point.throwTime == 0;
    }

    /**
     * 完整的那一问，包含贵的那一半。
     *
     * <p>{@code canPickup} 一个问题答完剩下的全部：拾物开关、拾取类型（物品／
     * 经验）、物品过滤名单，以及**模拟一次插入**确认背包真的放得下。整个拾物功能
     * 的开关就在这一句里——关掉拾物，这里恒为假，上层那个意图连"有东西可捡"都
     * 读不到。
     *
     * <p>{@code isTame} 是本体自己那圈自动拾取的条件（见 {@code pushEntities}），
     * 而 {@code canPickup} 单独拿出来时并不查它。两处判据必须一致，否则一只未驯服
     * 的女仆会走过去捡本体不许她碰的东西。
     */
    public static boolean collectable(EntityMaid maid, Entity candidate) {
        return couldBeTaken(candidate)
                && maid.isTame()
                && withinReach(maid, candidate)
                && maid.canPickup(candidate, true);
    }

    /**
     * 她伸手够不够得着——**把跳一下也算进去**。
     *
     * <p>没有这一条时她会去够头顶三格的东西：走到正下方，然后"到了没到"地反复
     * 判断，人看着就是在原地点头。够不着的判断本来就该在挑目标的时候做，而不是
     * 走到跟前再发现。
     *
     * <p>高度按她自己的身板算，不写死：站着能碰到的是**身高加上拾取半径**，
     * 而拾取半径是个属性（本体给的默认值 0.5，模组和指令都能改）。再加一跳的
     * 高度——原版跳跃顶点约一格四分之一，够她把脚边那格台子上的东西捞下来。
     *
     * <p>下方不设限。往下她走得到、掉得下去，而真正下不去的坑由"盯久了就放弃"
     * 兜住；给下方也设一条线，只会让她放着脚边矮一级的东西不管。
     */
    private static boolean withinReach(EntityMaid maid, Entity candidate) {
        double above = candidate.getY() - maid.getY();
        if (above <= 0.0D) {
            return true;
        }
        if (above > standingReach(maid) + JUMP_REACH) {
            return false;
        }
        return !needsAJump(maid, candidate) || canStandUnder(maid, candidate);
    }

    /**
     * 要跳才够得着的，还得能站到它正下方。
     *
     * <p>拾取判定是碰撞箱外扩拾取半径，半宽只有零点八格左右——**跳得再高，也够不到
     * 水平一格外的东西**。所以"高度够得着"只说对了一半：东西摆在两格高的实心柱子
     * 顶上时，她走不进那一格，只能停在旁边，跳起来仍然差着一格。
     *
     * <p>不加这一条的实机表现就是玩家报的那个：她走到柱子边上一通蹦，蹦不到，
     * 五秒后放弃——中间那五秒白费，而那件东西从头到尾就不该被列为目标。
     *
     * <p>只对要跳的那一档问。地面上的、以及一格台阶上的（那一档不用跳，她直接
     * 走上去）都不受影响。
     */
    private static boolean canStandUnder(EntityMaid maid, Entity candidate) {
        BlockPos under = BlockPos.containing(
                candidate.getX(), maid.getY(), candidate.getZ()
        );
        return maid.level()
                .getBlockState(under)
                .getCollisionShape(maid.level(), under)
                .isEmpty();
    }

    /**
     * 站着够不着，但跳一下够得着。
     *
     * <p>这一段高度里的东西要**真的跳**才拿得到。把跳跃算进
     * {@link #withinReach} 只是让她愿意去，而原版只在寻路需要迈台阶时才跳——
     * 悬在两格高处的东西没有路可走，寻路当场报走不到，于是她对着一件跳一下就能
     * 拿到的东西毫无反应。走向途中要据此补一次起跳，见 {@code LooseDropErrand}。
     */
    public static boolean needsAJump(EntityMaid maid, Entity candidate) {
        double above = candidate.getY() - maid.getY();
        // 高过头顶就跳，不等到高过"头顶加拾取半径"。那半格是碰撞箱**擦着**才算
        // 数的一段：正好两格高的那件东西落在它的边界上——站着够不到（相切不算
        // 相交），却又不满足起跳条件，于是她既不跳也拿不到，杵在下面。
        // 玩家报的"刚刚好够得到的两格高位置，她没有反应"就是这一格。
        return above > maid.getBbHeight()
                && above <= standingReach(maid) + JUMP_REACH;
    }

    private static double standingReach(EntityMaid maid) {
        return maid.getBbHeight() + pickupRange(maid);
    }

    private static double pickupRange(EntityMaid maid) {
        var attribute = maid.getAttribute(
                InitAttribute.MAID_PICKUP_RANGE.get()
        );
        return attribute == null ? DEFAULT_PICKUP_RANGE : attribute.getValue();
    }
}
