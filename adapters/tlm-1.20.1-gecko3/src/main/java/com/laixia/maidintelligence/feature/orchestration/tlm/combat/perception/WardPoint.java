package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 她在守的那个点。
 *
 * <p>感知此前只有一个中心——她自己。那对"她要不要打这一架"是够的，对"她是来
 * 保护谁的"不够：站在主人身边十六格内的东西她看得见，可主人走开十格之后，围着
 * 主人的那一圈就落在她的球之外了，而那正是她该管的事。
 *
 * <p>于是感知变成**两个球的并集**：她自己一个，她守的点一个，半径同为
 * {@code PerceptionRange.BLOCKS}——两个数不该分开调，"她能注意到多远"是一件事。
 *
 * <p>守的是哪个点由工作状态决定，而不是由一张清单：
 *
 * <ul>
 *   <li><b>家园模式</b>——限制中心。她被安置在那里，那里就是她要守的东西，主人
 *       此刻在不在都一样。</li>
 *   <li><b>其它模式</b>——主人。她跟着谁，就守着谁。</li>
 * </ul>
 *
 * <p>没有主人、也没有家的女仆没有要守的点，这时并集退化成原来那一个球，
 * 所有既有行为原样不变。
 */
public final class WardPoint {
    private WardPoint() {
    }

    /**
     * 她这一刻在守的位置，没有则为 {@code null}。
     *
     * <p>家园优先于主人，因为家园模式的含义就是"我把她留在这里"——那种情况下
     * 跟着主人跑开正是玩家不想要的。
     */
    public static Vec3 of(EntityMaid maid) {
        if (maid.isHomeModeEnable()) {
            BlockPos home = maid.getRestrictCenter();
            // 未设过家的女仆，限制中心是 ORIGIN；那不是一个"家"，是一个默认值。
            if (!BlockPos.ZERO.equals(home)) {
                return Vec3.atCenterOf(home);
            }
        }
        LivingEntity owner = maid.getOwner();
        return owner == null ? null : owner.position();
    }

    /**
     * 这一只离她守的点有多远，没有守的点时为正无穷。
     *
     * <p>正无穷而不是零：这个数只被用来排序，而"没有要守的东西"应当让每一只都
     * 一样地不占优先，不是让每一只都排在最前。
     */
    public static double distance(Vec3 ward, LivingEntity hostile) {
        return ward == null
                ? Double.POSITIVE_INFINITY
                : ward.distanceTo(hostile.position());
    }
}
