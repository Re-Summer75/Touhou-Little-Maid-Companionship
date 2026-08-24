package com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.DodgePolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.GuardFacingPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 让开一支正朝她飞来的箭。
 *
 * <p>与 {@link ShieldGuard} 是同一件事的两种做法——不挨那一下——所以放在一起。
 * 盾是拿手换的，挡得住就不必躲；躲是拿脚换的，没盾、盾被敲掉、或者箭从她背后来的
 * 时候只剩这一条。
 *
 * <p>判断在 {@link DodgePolicy}，纯几何。这里只做三件宿主才能做的事：**看见**飞行
 * 物、**记住**这一次要往哪边让、把那个方向**落到脚上**。
 *
 * <h2>为什么要记住</h2>
 *
 * <p>这一条和游走落点、环顾方向、举盾姿态是同一个教训：每 tick 重新决定就是抽搐。
 * 闪避尤其禁不起——她相对箭的偏移在闪的过程中一直在变，重算出来的"该往哪边让"会
 * 在两侧之间反复，表现是原地左右横跳，而那支箭从中间穿过去。所以方向一旦挑定就
 * 记到落点之后，中途不改主意，也不再扫描。
 *
 * <h2>它不是新的机动力</h2>
 *
 * <p>用的是她本来就有的移动速度，只把这一份速度在朝向下重新分解一次；不改寻路、
 * 不加冲刺、不动位移。窗口不够时她干脆不动（见
 * {@link DodgePolicy#MINIMUM_WARNING_TICKS}），所以近处射来的箭照样打中她。
 */
public final class ProjectileDodge {
    /**
     * 这一次往哪边让、从哪里起步、最迟让到哪一 tick。
     *
     * <p>收势的主判据是**让够了没有**（起步点加上
     * {@link DodgePolicy#clearanceNeeded}），不是时间。时间只是兜底：贴着墙让不动
     * 的时候总得有个头，否则她会一直顶着墙推到那一箭到达。
     *
     * @param bearing 世界偏航角
     * @param fromX   起步时她在哪儿，用来量让开了多少
     * @param fromZ   同上
     * @param until   她的 {@code tickCount} 到这个数就收势
     */
    private record Sidestep(float bearing, double fromX, double fromZ, int until) {
    }

    private static final Map<EntityMaid, Sidestep> STEPS = new WeakHashMap<>();

    /**
     * 这一次闪避的供词：看见了没有、落到脚上几 tick、让出去多少。
     *
     * <p>"她没让开"是三种完全不同的病共用的一句话——没看见（预警窗口不够、
     * 或者根本没扫描）、看见了但没落到脚上（每 tick 都被别的写入者抢走）、
     * 落到脚上了却让不动（贴着墙、或者速度被谁按住）。三者的修法南辕北辙，
     * 而断言只报得出一个横移距离。实测的长射那条红了九轮里的五轮，横移量
     * 稳定在 0.352 格——五位有效数字相同说明那是一段确定性运动，可到底是
     * 哪一种，光凭这个数字我讲不出。
     *
     * <p>只在真有一支箭要让的时候才记，平时不分配。
     */
    private static final Map<EntityMaid, Ledger> LEDGERS = new WeakHashMap<>();

    /** 闪避的行车记录，测试断言她没让开时当供词打出来。 */
    public static String diary(EntityMaid maid) {
        Ledger ledger = LEDGERS.get(maid);
        return ledger == null
                ? "dodge=从未看见一支该让的箭"
                : ledger.toString();
    }

    /** 一次闪避从看见到收势的全过程。 */
    private static final class Ledger {
        private int armedAt;
        private double warning;
        private float bearing;
        private int until;
        private int applied;
        private double cleared;
        private double needed;
        private float pace;
        private String left = "还在让";

        @Override
        public String toString() {
            return String.format(
                    "dodge 看见@t=%d 预警=%.1ft 朝向=%.0f° 让到t=%d；"
                            + "落到脚上 %d tick，让出 %.3f/%.2f 格，"
                            + "步速 %.3f；收势=%s",
                    armedAt, warning, bearing, until,
                    applied, cleared, needed, pace, left);
        }
    }

    private ProjectileDodge() {
    }

    /**
     * 每 tick 看一眼有没有该让的箭。
     *
     * <p>由 ambient 钩子驱动而不是交战动作：被冷箭射中不需要她先决定"我在打架"，
     * 而射她的那一个可能根本不在她视野里——她看见的是箭。
     */
    public static void consider(EntityMaid maid) {
        if (!maid.isAlive() || maid.isSleeping() || maid.isPassenger()) {
            STEPS.remove(maid);
            return;
        }
        // 已经在让了就别改主意，连扫描都省了。
        Sidestep held = STEPS.get(maid);
        if (held != null && maid.tickCount < held.until()) {
            return;
        }
        STEPS.remove(maid);

        DodgePolicy policy = DodgePolicy.INSTANCE;
        Projectile soonest = null;
        double soonestTicks = Double.POSITIVE_INFINITY;
        for (Projectile shot : maid.level().getEntitiesOfClass(
                Projectile.class,
                maid.getBoundingBox().inflate(DodgePolicy.NOTICE_RANGE),
                shot -> notOurOwn(maid, shot)
        )) {
            double ticks = timeToClosest(maid, shot, policy);
            if (Double.isNaN(ticks) || ticks >= soonestTicks) {
                continue;
            }
            soonest = shot;
            soonestTicks = ticks;
        }
        if (soonest == null) {
            return;
        }
        if (ShieldGuard.raised(maid) && covered(maid, soonest, policy)) {
            // 挡得住。转开身体只会把那面盾一起转掉。
            return;
        }
        Vec3 offset = maid.position().subtract(soonest.position());
        Vec3 flight = soonest.getDeltaMovement();
        float bearing = policy.sidestepBearing(
                offset.x, offset.z,
                flight.x, flight.z,
                soonestTicks,
                maid.getRandom().nextBoolean()
        );
        int until = maid.tickCount
                + (int) Math.ceil(soonestTicks)
                + DodgePolicy.LEAN_PAST_IMPACT_TICKS;
        STEPS.put(maid, new Sidestep(
                bearing, maid.getX(), maid.getZ(), until
        ));
        Ledger ledger = new Ledger();
        ledger.armedAt = maid.tickCount;
        ledger.warning = soonestTicks;
        ledger.bearing = bearing;
        ledger.until = until;
        LEDGERS.put(maid, ledger);
    }

    /**
     * 把记下的方向落到脚上，返回她这一 tick 的移动是不是被这里接管了。
     *
     * <p>只能在宿主的 {@code MaidMoveControl#tick} 之后调用：前进量每 tick 都被
     * {@code setSpeed} 重写，在它之前写等于没写。
     *
     * @param travelYaw  她本来要去的方向，用于合成
     * @param travelling 那个方向这一 tick 是否有意义
     */
    public static boolean apply(
            EntityMaid maid, float travelYaw, boolean travelling
    ) {
        Sidestep step = STEPS.get(maid);
        Ledger ledger = LEDGERS.get(maid);
        if (step == null || maid.tickCount >= step.until()) {
            if (ledger != null && step != null) {
                ledger.left = "时间到";
            }
            STEPS.remove(maid);
            return false;
        }
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double cleared = cleared(maid, step);
        double needed = policy.clearanceNeeded(maid.getBbWidth() / 2.0D);
        if (ledger != null) {
            ledger.cleared = cleared;
            ledger.needed = needed;
        }
        if (cleared >= needed) {
            // 让够了。再让下去她会一路横穿房间——量过一次，八格。
            if (ledger != null) {
                ledger.left = "让够了";
            }
            STEPS.remove(maid);
            return false;
        }
        float pace = pace(maid);
        if (!(pace > 0.0F)) {
            if (ledger != null) {
                ledger.left = "步速为零";
            }
            return false;
        }
        if (ledger != null) {
            ledger.applied++;
            ledger.pace = pace;
        }
        float bearing = travelling
                ? policy.lean(travelYaw, step.bearing())
                : step.bearing();
        GuardFacingPolicy facing = GuardFacingPolicy.INSTANCE;
        float yaw = maid.getYRot();
        // 速度必须先写。移动输入只是**方向**：{@code LivingEntity#travel} 把它
        // 交给 {@code moveRelative}，而那里的缩放系数是 {@code getSpeed()}——她
        // 站着不动时那个字段是零，于是方向乘零，她纹丝不动。实测过：xxa/zza 十二
        // tick 都写着，她横移了 0.02 格，然后被那一箭射中。
        maid.setSpeed(pace);
        maid.setZza((float) (pace * facing.forwardShare(yaw, bearing)));
        maid.setXxa((float) (pace * facing.strafeShare(yaw, bearing)));
        return true;
    }

    /**
     * 她已经沿这一次让开的方向挪出去多远。
     *
     * <p>投影而不是直线距离：她可能同时还在走别的路（{@code lean} 会把两个方向合
     * 起来），而只有垂直于弹道的那一份换来了间距。
     */
    private static double cleared(EntityMaid maid, Sidestep step) {
        double radians = Math.toRadians(step.bearing());
        return (maid.getX() - step.fromX()) * -Math.sin(radians)
                + (maid.getZ() - step.fromZ()) * Math.cos(radians);
    }

    /**
     * 她此刻能走多快。
     *
     * <p>宿主正在带着她走时用宿主那个值（里面已经含了计划给的速度倍率）；站着不动
     * 时退回她的移动速度属性，也就是倍率一的满速——闪避正是该用满速的那一刻。
     */
    private static float pace(EntityMaid maid) {
        float moving = maid.getSpeed();
        if (moving > 0.0F) {
            return moving;
        }
        return (float) maid.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** 这一支还有几 tick 打到她；不会打中或已经飞过去时为 NaN。 */
    private static double timeToClosest(
            EntityMaid maid, Projectile shot, DodgePolicy policy
    ) {
        Vec3 offset = maid.position().subtract(shot.position());
        Vec3 flight = shot.getDeltaMovement();
        double ticks = policy.ticksToClosest(
                offset.x, offset.z, flight.x, flight.z
        );
        if (!policy.worthDodging(ticks)) {
            return Double.NaN;
        }
        double miss = policy.missDistance(
                offset.x, offset.z, flight.x, flight.z, ticks
        );
        if (!policy.wouldHit(miss, maid.getBbWidth() / 2.0D)) {
            return Double.NaN;
        }
        return ticks;
    }

    /** 举着的盾正对着它。 */
    private static boolean covered(
            EntityMaid maid, Projectile shot, DodgePolicy policy
    ) {
        Vec3 towards = maid.position().subtract(shot.position());
        if (towards.lengthSqr() < 1.0E-6D) {
            return true;
        }
        return policy.shieldCovers(
                towards.normalize().dot(maid.getViewVector(1.0F))
        );
    }

    /**
     * 不是她自己或她主人射出去的。
     *
     * <p>她替主人挡箭是另一件事，但绝不该躲开主人射向她敌人的那一支——那一支
     * 本来就从她身边过去，躲它只会让她走进别的什么东西的怀里。
     */
    private static boolean notOurOwn(EntityMaid maid, Projectile shot) {
        if (!shot.isAlive()) {
            return false;
        }
        Entity shooter = shot.getOwner();
        return shooter != maid && shooter != maid.getOwner();
    }
}
