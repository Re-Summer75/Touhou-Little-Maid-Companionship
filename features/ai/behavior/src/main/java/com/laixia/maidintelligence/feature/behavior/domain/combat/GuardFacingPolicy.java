package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * 举盾后退时，脸转多快、这个姿态守多久、脚往哪儿迈。
 *
 * <p>"面对敌人后退"这件事本身是对的，做法上却踩了这个项目已经踩过好几次的同一个
 * 坑：**每 tick 重新决定，且瞬时生效**。四道门（盾举着、知道防谁、行进方向背离它、
 * 它确实在追）里有三道会逐 tick 抖——挥刀那一下盾会放下再举起，接近速率是个在零
 * 附近来回穿的符号判据，路径拐弯时点积也会穿零。门一灭，宿主的
 * {@code MaidMoveControl} 立刻把偏航角扭回行进方向；门一亮，又被一把扳回敌人方向。
 * 玩家看到的就是身体抽搐。
 *
 * <p>所以这里给两样东西：
 *
 * <ul>
 *   <li><b>回差</b>——姿态一旦成立就守 {@link #HOLD_TICKS}，三道软门中途熄灭不算
 *       退出。只有"盾真的没了"或"敌人真的没了"这种硬事实才立刻退出。</li>
 *   <li><b>转速上限</b>——一次转不超过 {@link #TURN_DEGREES_PER_TICK}，从**自己记着
 *       的**那个角度往目标角度走，而不是从当下的 {@code yRot} 走。后者每 tick 会被
 *       宿主先扳一次，从它出发永远收敛不了。</li>
 * </ul>
 *
 * <p>守住姿态之所以安全，靠的是下面这组分解：世界里的移动方向由
 * {@link #forwardShare} 与 {@link #strafeShare} 一起还原成行进方向，**与她的脸朝哪
 * 无关**。于是这个姿态只影响朝向，不影响她走去哪；多守几 tick 最坏也只是多面对
 * 敌人半秒，不会把她往任何她不想去的地方带。
 */
public final class GuardFacingPolicy {
    /**
     * 姿态成立后至少守多久。
     *
     * <p>一秒。要跨过的抖动里最长的是接近速率的符号噪声——两个速度相近的东西在
     * 追逐时，那个符号几 tick 就穿一次零；挥刀造成的举盾空档只有一 tick。取一秒
     * 是因为守住的代价近乎为零（见类注释），而守不住的代价是玩家看得见的抽搐。
     */
    public static final int HOLD_TICKS = 20;

    /**
     * 每 tick 最多转多少度。
     *
     * <p>转满一百八十度用六 tick，约三分之一秒——一个人回身的速度。原版
     * {@code MoveControl} 给移动朝向的上限是九十度每 tick，那对"跟着路径拐弯"够用，
     * 对"回身面对追兵"就成了瞬移。
     */
    public static final float TURN_DEGREES_PER_TICK = 30.0F;

    /**
     * 行进向量短于这个长度就不算"在走"。
     *
     * <p>倒着走要先知道往哪走。路径点几乎踩在脚下时那个向量的方向纯粹是噪声，
     * 按它分解只会让她原地打转。
     */
    public static final double SHORTEST_TRAVEL = 0.1D;

    public static final GuardFacingPolicy INSTANCE = new GuardFacingPolicy();

    private GuardFacingPolicy() {
    }

    /** 朝向 (dx, dz) 这个方向的偏航角，度。 */
    public float yawToward(double dx, double dz) {
        return wrap((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D));
    }

    /**
     * 把 {@code current} 朝 {@code wanted} 转一步。
     *
     * <p>差值先绕回 ±180 再钳制，否则从 179 度转到 -179 度会被算成走三百五十八度
     * 的远路——那正是"抽搐"里最刺眼的一种。
     */
    public float stepToward(float current, float wanted) {
        float delta = wrap(wanted - current);
        float step = Math.max(
                -TURN_DEGREES_PER_TICK,
                Math.min(TURN_DEGREES_PER_TICK, delta)
        );
        return wrap(current + step);
    }

    /**
     * 前进量该取速度的几倍。
     *
     * <p>与 {@link #strafeShare} 合起来，把"沿 {@code travelYaw} 走"这件事在她当下
     * 的朝向 {@code yaw} 下还原出来：MC 的 {@code moveRelative} 是按 {@code yRot} 把
     * (横移, 前进) 旋进世界的，逆着旋回去就得到这两个分量。脸正对行进方向时是
     * (0, +1)，背对时是 (0, -1)——后者正是"倒着走"，而中间那些角度是转身过程中那
     * 几 tick，她照样沿原方向平移。
     */
    public double forwardShare(float yaw, float travelYaw) {
        return Math.cos(Math.toRadians(wrap(yaw - travelYaw)));
    }

    /** 横移量该取速度的几倍，与 {@link #forwardShare} 配套。 */
    public double strafeShare(float yaw, float travelYaw) {
        return Math.sin(Math.toRadians(wrap(yaw - travelYaw)));
    }

    /** 把角度绕回 (-180, 180]。 */
    private static float wrap(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0F;
        }
        float wrapped = degrees % 360.0F;
        if (wrapped > 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped <= -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
