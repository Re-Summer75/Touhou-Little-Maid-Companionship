package com.laixia.maidintelligence.mixin.tlm.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.control.MaidMoveControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.GuardFacingPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ProjectileDodge;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 打起来之后，脚下那点宿主管不到的调整：面对敌人后退，以及让开射来的箭。
 *
 * <p>两件事共用这一个注入点，因为它们要的是同一样宿主不肯给的东西——**在
 * {@link MaidMoveControl#tick()} 之后**改朝向和移动输入。那个方法每 tick 把
 * {@code yRot} 扭向行进方向、把前进量设成移动速度，而它跑在 brain 之后；在它之前
 * 写的任何东西都会被它覆盖。这也是这个 Mixin 存在的唯一理由。
 *
 * <p>两者都不改速度、不改寻路、不给她任何新的机动力：走多快、走去哪都还是宿主
 * 算出来的，这里只把同一份速度在朝向下重新分解。
 *
 * <h2>面对敌人后退</h2>
 *
 * <p>原版只挡正面来的伤害：{@code isDamageSourceBlocked} 拿入射方向与
 * {@code getViewVector} 做点积，而那个向量来自 {@code getYRot}——**身体**。一个
 * 玩家会转过身来倒退着挡，女仆此前做不到，举盾撤退于是等于举着盾把后背交出去。
 *
 * <p>只钉朝向是不够的，而且会更糟：MC 的怪物**沿 {@code yRot} 前进**
 * （{@code Mob.setSpeed} 会把 {@code zza} 设成同值，{@code travel} 再按
 * {@code yRot} 把它转成世界位移）。只把脸转过去，她就会朝敌人走过去。所以这里
 * 做的是玩家做的那件事：转身面对，脚下仍沿原方向平移。
 *
 * <h2>姿态，不是逐 tick 的判断</h2>
 *
 * <p>第一版是四道门直接决定这一 tick 转不转身，玩家报的"身体抽搐"就出在那里：
 * 四道门里有三道逐 tick 抖——挥刀那一下 {@code lowerForSwing} 会把盾放下、下一
 * tick 再举起；{@link ShieldGuard#pursuedBy} 是个在零附近来回穿的符号判据；路径
 * 拐弯时行进向量与敌人向量的点积也会穿零。门一灭，宿主立刻把偏航角扳回行进方向；
 * 门一亮，这里又一把扳回敌人方向。两个相差近一百八十度的值逐 tick 交替，就是抽搐。
 *
 * <p>所以判断的产物改成一个**姿态**：软门成立时刷新一个保持窗口，窗口内软门熄灭
 * 不算退出，转身也按 {@link GuardFacingPolicy#TURN_DEGREES_PER_TICK} 一步步转。
 * 硬事实——敌人没了、盾没了、根本没在走——仍然立刻退出，不吃回差。
 *
 * <p>保持窗口之所以敢开这么长，是因为姿态**只影响脸**：前进量与横移量由
 * {@link GuardFacingPolicy#forwardShare} 与 {@link GuardFacingPolicy#strafeShare}
 * 一起还原成行进方向，与她朝哪儿看无关。多守半秒最坏只是多面对敌人半秒，不会把她
 * 往任何地方带偏，转身那几 tick 里她也照样沿原路平移而不是画弧。
 */
@Mixin(value = MaidMoveControl.class, remap = false)
public abstract class MaidMoveControlGuardFacingMixin extends MoveControl {
    /** 后退姿态保持到哪一 tick 为止。 */
    @Unique
    private int maidIntelligence$holdUntil;

    /**
     * 后退姿态自己记着的朝向。
     *
     * <p>转身必须从**这个**角度往目标角度走，而不是从 {@code maid.getYRot()} 走：
     * 后者每 tick 都被宿主先朝行进方向扳一次（原版上限九十度），从它出发的话每
     * tick 转的三十度都会被扳回去，永远收敛不了。
     */
    @Unique
    private float maidIntelligence$stanceYaw;

    /** 后退姿态是否成立。 */
    @Unique
    private boolean maidIntelligence$backing;

    /**
     * 上一 tick 的横移量是不是这里写的。
     *
     * <p>写了就得还：{@code LivingEntity} 每 tick 只把横移量乘 0.98，宿主的
     * {@code setSpeed} 也只写前进量，留着不管等于让她之后一直斜着飘。
     */
    @Unique
    private boolean maidIntelligence$steering;

    protected MaidMoveControlGuardFacingMixin(net.minecraft.world.entity.Mob mob) {
        super(mob);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void maidIntelligence$steerUnderFire(CallbackInfo callback) {
        if (!(this.mob instanceof EntityMaid maid)) {
            return;
        }
        double travelX = this.getWantedX() - maid.getX();
        double travelZ = this.getWantedZ() - maid.getZ();
        // 站着不动时行进方向纯是噪声。站着不动那一档由 ShieldGuard#faceThreat
        // 负责——它在 brain 里写，那种时候没有谁会再覆盖它。
        boolean travelling = travelX * travelX + travelZ * travelZ
                >= GuardFacingPolicy.SHORTEST_TRAVEL
                        * GuardFacingPolicy.SHORTEST_TRAVEL;
        float travelYaw = travelling
                ? GuardFacingPolicy.INSTANCE.yawToward(travelX, travelZ)
                : 0.0F;

        // 后退优先于闪避：正在举盾后退时她已经面对着威胁，那个方向来的箭本来就
        // 挡得住（ProjectileDodge 会自己跳过），而为别处飞来的一支把脚横过去，
        // 有可能正好横进追着她的那一只怀里。
        boolean steered =
                maidIntelligence$backAway(maid, travelling, travelX, travelZ)
                        || ProjectileDodge.apply(maid, travelYaw, travelling);
        if (!steered && maidIntelligence$steering) {
            maid.setXxa(0.0F);
        }
        maidIntelligence$steering = steered;
    }

    /** 举盾面对敌人后退；返回这一 tick 的移动是不是被它接管了。 */
    @Unique
    private boolean maidIntelligence$backAway(
            EntityMaid maid,
            boolean travelling,
            double travelX,
            double travelZ
    ) {
        // 撤退路径上 getTarget() 已被清空（脱离本来就要放掉目标），所以问
        // ShieldGuard 记的那一份。它没了，或者盾根本用不了了（被斧子敲掉、
        // 副手空着），姿态就没有任何意义，立刻作废。
        LivingEntity threat = ShieldGuard.guardedAgainst(maid);
        if (threat == null || !travelling || !ShieldGuard.available(maid)) {
            maidIntelligence$backing = false;
            return false;
        }
        double toThreatX = threat.getX() - maid.getX();
        double toThreatZ = threat.getZ() - maid.getZ();
        if (toThreatX * toThreatX + toThreatZ * toThreatZ < 1.0E-4D) {
            maidIntelligence$backing = false;
            return false;
        }
        int now = maid.tickCount;
        if (maidIntelligence$wantsToBackAway(
                maid, threat, toThreatX, toThreatZ, travelX, travelZ
        )) {
            if (!maidIntelligence$backing) {
                maidIntelligence$backing = true;
                maidIntelligence$stanceYaw = maid.getYRot();
            }
            maidIntelligence$holdUntil = now + GuardFacingPolicy.HOLD_TICKS;
        } else if (!maidIntelligence$backing
                || now >= maidIntelligence$holdUntil) {
            maidIntelligence$backing = false;
            return false;
        }

        GuardFacingPolicy policy = GuardFacingPolicy.INSTANCE;
        maidIntelligence$stanceYaw = policy.stepToward(
                maidIntelligence$stanceYaw,
                policy.yawToward(toThreatX, toThreatZ)
        );
        float yaw = maidIntelligence$stanceYaw;
        maid.setYRot(yaw);
        maid.yBodyRot = yaw;
        maid.yHeadRot = yaw;

        float travelYaw = policy.yawToward(travelX, travelZ);
        float speed = maid.getSpeed();
        maid.setZza((float) (speed * policy.forwardShare(yaw, travelYaw)));
        maid.setXxa((float) (speed * policy.strafeShare(yaw, travelYaw)));
        return true;
    }

    /**
     * 软门：这一 tick 有没有理由面对着敌人后退。
     *
     * <p>三条都会逐 tick 抖，所以它们只用来**刷新**保持窗口，不用来终止姿态。
     */
    @Unique
    private boolean maidIntelligence$wantsToBackAway(
            EntityMaid maid,
            LivingEntity threat,
            double toThreatX,
            double toThreatZ,
            double travelX,
            double travelZ
    ) {
        if (!ShieldGuard.raised(maid)) {
            return false;
        }
        // 正在朝它去，或者横着绕——两种都不是"后退"。
        if (travelX * toThreatX + travelZ * toThreatZ >= 0.0D) {
            return false;
        }
        // 只在**真的甩不掉**的时候倒着走。
        //
        // 倒退不是免费的：导航仍在沿路径把她往前带，而这里把朝向反过来，两者
        // 相互别着。甩得掉的对手根本不需要付这笔钱——转身跑开就好，反正它追不上。
        //
        // 实测支持这条收窄：不加限制的版本让僵尸局从 5.88/6 掉到 5.00/6
        // （那一局她对僵尸有明显速度优势，倒退是纯亏），而卫道士局落在噪声里。
        return ShieldGuard.pursuedBy(maid, threat);
    }
}
