package com.laixia.maidintelligence.feature.behavior.domain.combat;

import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 一支已经出膛的箭会不会打中她，以及来不来得及让开。
 *
 * <p>玩家说的"预判"就是这件事：不是被射中之后才后退，而是在飞行途中判断落点，
 * 往旁边挪半步让它擦过去。所需要的全部东西都在飞行物身上——位置与速度——所以
 * 这里是纯几何，不需要知道对面是骷髅还是别的什么。
 *
 * <h2>为什么是最近点，不是距离</h2>
 *
 * <p>"箭离她多近"没有意义，一支从旁边飞过的箭可以贴得很近却永远打不中她。有意义
 * 的是**最近接近点**：把她相对箭的位移沿箭的速度方向投影，得到还有几 tick 到达
 * 最近点，以及那一刻两者相隔多远。前者是她的反应窗口，后者决定要不要反应。
 *
 * <p>只算水平面。箭有重力也有阻力，竖直方向的落点本来就估不准；而她能做的事只有
 * 水平移动，估准了也用不上。
 *
 * <h2>为什么会有躲不掉的箭</h2>
 *
 * <p>{@link #MINIMUM_WARNING_TICKS} 是一条**反应时间**下限，不是体能下限——这点
 * 量过之后才敢写清楚。她的移动速度属性是 0.7，而 MC 的地面移动里速度既进缩放系数
 * 又进输入向量，位移随速度**平方**走，满速折出来接近每 tick 一格：让开半个身位
 * 她只要两 tick。所以"贴脸那一箭躲不掉"在物理上是不成立的，不设这道门她会躲掉
 * 一切，那才是超人。
 *
 * <p>门设在六 tick（骷髅箭约十格），因为一个人得先**看见**那一箭。她躲得掉远处的
 * 冷箭、躲不掉近处的攒射，正是一个熟练玩家的样子。
 *
 * <h2>让多远，而不是让多久</h2>
 *
 * <p>闪避的量由 {@link #clearanceNeeded} 表达成**距离**，让够了就收势。第一版按
 * 飞行时间保持，结果是她以满速横移了整整八格、冲出房间——同一个数在她慢走和满速
 * 两种情形下差着一个数量级，而"让开半个身位"在两种情形下都是同一件事。凡是能用
 * 不变量表达的就不要用时长配平。
 */
public final class DodgePolicy {
    /**
     * 少于这么多 tick 的预警就不躲了。
     *
     * <p>骷髅的箭是每 tick 一点六格，六 tick 约合十格。她要横向挪开的是自己半个
     * 身位，而地面加速要三四 tick 才接近满速——再短的窗口里她挪不出那半格。
     */
    public static final int MINIMUM_WARNING_TICKS = 6;

    /**
     * 多于这么多 tick 就先不管。
     *
     * <p>直线外推在远处是不成立的：箭每 tick 乘 0.99 的阻力，再加上重力下坠，
     * 三十 tick 之后算出来的落点已经和实际差出好几格。为一支多半会掉在她脚前的箭
     * 提前一秒半开始横移，是把预判做成了迷信。
     */
    public static final int LATEST_WARNING_TICKS = 30;

    /**
     * 判定时飞行物把目标的碰撞箱向外撑开多少。
     *
     * <p>原版 {@code ProjectileUtil#getEntityHitResult} 的数值。命中判据要和游戏
     * 用的那一个一致，否则她会去躲一支本来就会擦过去的箭，或者对一支会打中的箭
     * 视而不见。
     */
    public static final double PROJECTILE_REACH = 0.3D;

    /**
     * 越过命中判据之后再多让的一点。
     *
     * <p>直线外推有误差，箭本身也带散布，停在恰好擦边的位置等于没让。
     */
    public static final double CLEARANCE_MARGIN = 0.25D;

    /**
     * 时间兜底比预测落点多留的那几 tick。
     *
     * <p>收势的主判据是 {@link #clearanceNeeded}——让够了就停，时间只是兜底：贴着
     * 墙让不动的时候总得有个头。多留两 tick 是因为直线外推总略微低估飞行时间
     * （阻力使箭越飞越慢），卡在预测点收势有可能刚好收回到弹道上。
     */
    public static final int LEAN_PAST_IMPACT_TICKS = 2;

    /**
     * 她会留意多远之内的飞行物。
     *
     * <p>与其它一切射程同源。这条同时决定了预警窗口的上限：十六格外射来的箭进入
     * 视野时还剩十 tick，正好在 {@link #MINIMUM_WARNING_TICKS} 之上；八格处射出的
     * 那一支进入视野时只剩五 tick，落在下面，于是她不动——这不是两条规则，是同一条。
     */
    public static final double NOTICE_RANGE = PerceptionRange.BLOCKS;

    /** 偏移量短于这个长度时，它的方向纯是噪声。 */
    private static final double DEAD_ON = 0.05D;

    public static final DodgePolicy INSTANCE = new DodgePolicy();

    private DodgePolicy() {
    }

    /**
     * 还有几 tick 到最近接近点。
     *
     * <p>负数表示最近点已经过去了——那支箭正在离开，不必理会。
     *
     * @param rx 她相对飞行物的位移，东向
     * @param rz 同上，南向
     * @param vx 飞行物每 tick 的位移，东向
     * @param vz 同上，南向
     * @return tick 数；飞行物几乎不动时为 {@link Double#NaN}
     */
    public double ticksToClosest(double rx, double rz, double vx, double vz) {
        double speedSquared = vx * vx + vz * vz;
        if (!(speedSquared > 1.0E-6D)) {
            return Double.NaN;
        }
        return (rx * vx + rz * vz) / speedSquared;
    }

    /** 那一刻两者相隔多远。 */
    public double missDistance(
            double rx, double rz, double vx, double vz, double ticks
    ) {
        double mx = rx - vx * ticks;
        double mz = rz - vz * ticks;
        return Math.sqrt(mx * mx + mz * mz);
    }

    /**
     * 这个间距算不算会打中她。
     *
     * @param halfWidth 她的碰撞箱半宽
     */
    public boolean wouldHit(double miss, double halfWidth) {
        return miss <= halfWidth + PROJECTILE_REACH;
    }

    /**
     * 让开多远才算让开了。
     *
     * <p>刚好越过命中判据，再加一点余量——她不该停在"差一点就中"的位置上。
     * 让够了就收势，所以这个数同时是闪避的**全部幅度**：一步，不是一段冲刺。
     *
     * @param halfWidth 她的碰撞箱半宽
     */
    public double clearanceNeeded(double halfWidth) {
        return halfWidth + PROJECTILE_REACH + CLEARANCE_MARGIN;
    }

    /** 这个预警窗口够不够她让开。 */
    public boolean worthDodging(double ticks) {
        return Double.isFinite(ticks)
                && ticks >= MINIMUM_WARNING_TICKS
                && ticks <= LATEST_WARNING_TICKS;
    }

    /**
     * 该往哪个方向让。
     *
     * <p>沿**最近点的偏移方向**让，也就是她本来就偏出去的那一侧——那是离开弹道
     * 最短的一条路。正中弹道时这个方向退化，才需要在两侧里挑一个。
     *
     * @param towardsLeft 正中弹道时挑哪一侧。这个选择必须由调用方**记住**：每
     *                    tick 重掷一次的话她会原地左右横跳，而那正是这套东西
     *                    一直在避免的那种抽搐
     * @return 世界偏航角，度
     */
    public float sidestepBearing(
            double rx, double rz,
            double vx, double vz,
            double ticks,
            boolean towardsLeft
    ) {
        double mx = rx - vx * ticks;
        double mz = rz - vz * ticks;
        if (mx * mx + mz * mz < DEAD_ON * DEAD_ON) {
            double speed = Math.sqrt(vx * vx + vz * vz);
            if (!(speed > 1.0E-6D)) {
                return 0.0F;
            }
            mx = (towardsLeft ? -vz : vz) / speed;
            mz = (towardsLeft ? vx : -vx) / speed;
        }
        return GuardFacingPolicy.INSTANCE.yawToward(mx, mz);
    }

    /**
     * 举着的盾挡不挡得住这一支。
     *
     * <p>判据抄的是原版 {@code isDamageSourceBlocked}：从伤害来源指向她的向量与她
     * 的**视线**向量点积为负，即她正对着来向，才算挡住。挡得住就不必躲——躲开反而
     * 会把身体转开，把那面盾一起转掉。
     *
     * @param fromShotToMaidDotView 上述点积，两个向量都已归一化
     */
    public boolean shieldCovers(double fromShotToMaidDotView) {
        return fromShotToMaidDotView < 0.0D;
    }

    /**
     * 闪避方向与原本行进方向合成之后的朝向。
     *
     * <p>闪一下不该让她忘了原来要去哪。两个方向各取单位向量相加，得到的是"边走边
     * 让"——玩家做的正是这个动作，而不是停下来横移一步再继续。
     *
     * <p>两者恰好相反时和向量退化，此时闪避优先：让开弹道是这一刻唯一要紧的事。
     */
    public float lean(float travelYaw, float sidestepYaw) {
        double travel = Math.toRadians(travelYaw);
        double sidestep = Math.toRadians(sidestepYaw);
        // MC 的偏航角：朝向 = (-sin, cos)。
        double x = -Math.sin(travel) - Math.sin(sidestep);
        double z = Math.cos(travel) + Math.cos(sidestep);
        if (x * x + z * z < DEAD_ON * DEAD_ON) {
            return sidestepYaw;
        }
        return GuardFacingPolicy.INSTANCE.yawToward(x, z);
    }
}
