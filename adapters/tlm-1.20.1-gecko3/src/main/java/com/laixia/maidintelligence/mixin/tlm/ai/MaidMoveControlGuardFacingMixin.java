package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.control.MaidMoveControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 举着盾后退时，让她倒着走而不是转身走。
 *
 * <p>原版只挡正面来的伤害：{@code isDamageSourceBlocked} 拿入射方向与
 * {@code getViewVector} 做点积，而那个向量来自 {@code getYRot}——**身体**。一个
 * 玩家会转过身来倒退着挡，女仆此前做不到，因为宿主的 {@link MaidMoveControl}
 * 每 tick 把 {@code yRot} 扭向行进方向，而它跑在 brain 之后。举盾撤退于是等于
 * 举着盾把后背交出去。
 *
 * <p>只钉朝向是不够的，而且会更糟：MC 的怪物**沿 {@code yRot} 前进**
 * （{@code Mob.setSpeed} 会把 {@code zza} 设成同值，{@code travel} 再按
 * {@code yRot} 把它转成世界位移）。只把脸转过去，她就会朝敌人走过去。所以这里
 * 做的是玩家做的那件事：**转身面对，并把前进量取负**——倒着走。
 *
 * <p>注入在 {@code TAIL}，因为 {@code tick()} 正是最后一个写朝向和速度的地方；
 * 在它之前做任何事都会被它覆盖，这也正是这个 Mixin 存在的唯一理由。
 *
 * <p>三道门，都不是配平：
 *
 * <ul>
 *   <li><b>盾必须已经举起来</b>——没举盾时转身没有任何收益，只会让她走得别扭；</li>
 *   <li><b>必须知道在防谁</b>——撤退路径上 {@code getTarget()} 已被清空，所以问
 *       {@link ShieldGuard#guardedAgainst}；</li>
 *   <li><b>行进方向必须大体背离那一只</b>。倒着走只有在"往后退"时才成立；需要
 *       绕行、侧移或追击时她照常转身。判据是行进向量与敌人向量的点积为负，没有
 *       角度常量。</li>
 * </ul>
 *
 * <p>不改速度、不改寻路、不给她任何新的机动力：走多快、走去哪都还是上面那段
 * 宿主代码算出来的，这里只改朝向与前进量的符号。
 */
@Mixin(value = MaidMoveControl.class, remap = false)
public abstract class MaidMoveControlGuardFacingMixin extends MoveControl {
    protected MaidMoveControlGuardFacingMixin(net.minecraft.world.entity.Mob mob) {
        super(mob);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void maidIntelligence$guardWhileBackingAway(CallbackInfo callback) {
        if (!(this.mob instanceof EntityMaid maid)) {
            return;
        }
        if (!ShieldGuard.raised(maid)) {
            return;
        }
        LivingEntity threat = ShieldGuard.guardedAgainst(maid);
        if (threat == null) {
            return;
        }
        double toThreatX = threat.getX() - maid.getX();
        double toThreatZ = threat.getZ() - maid.getZ();
        if (toThreatX * toThreatX + toThreatZ * toThreatZ < 1.0E-4D) {
            return;
        }
        double travelX = this.getWantedX() - maid.getX();
        double travelZ = this.getWantedZ() - maid.getZ();
        // 正在朝它去，或者横着绕——两种都不是"后退"，让宿主照常转身。
        if (travelX * toThreatX + travelZ * toThreatZ >= 0.0D) {
            return;
        }
        // 只在**真的甩不掉**的时候倒着走。
        //
        // 倒退不是免费的：导航仍在沿路径把她往前带，而这里把朝向与前进量一起
        // 反过来，两者相互别着。甩得掉的对手根本不需要付这笔钱——转身跑开就
        // 好，反正它追不上，挡不挡都一样。
        //
        // 实测支持这条收窄：不加限制的版本让僵尸局从 5.88/6 掉到 5.00/6
        // （那一局她对僵尸有明显速度优势，倒退是纯亏），而卫道士局落在噪声里。
        // 判据与 {@code Withdrawal} 用的是同一个——接近速率为正，即她已经在退
        // 而距离仍在缩短。
        if (!ShieldGuard.pursuedBy(maid, threat)) {
            return;
        }
        float facing = (float) (
                Math.toDegrees(Math.atan2(toThreatZ, toThreatX)) - 90.0D
        );
        maid.setYRot(facing);
        maid.yBodyRot = facing;
        maid.yHeadRot = facing;
        // 前进量取负：脸朝敌人，脚往后。速度大小仍是宿主算的那个。
        maid.setZza(-maid.getSpeed());
    }
}
