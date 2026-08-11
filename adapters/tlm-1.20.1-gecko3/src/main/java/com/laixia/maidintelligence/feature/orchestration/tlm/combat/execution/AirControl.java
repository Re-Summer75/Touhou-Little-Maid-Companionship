package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.phys.Vec3;

/**
 * Steering while her feet are off the ground.
 *
 * <p>A player who jumps keeps steering — that is the whole reason jumping into a
 * fight is a technique rather than a stunt. A mob does not, and the reason is one
 * line of vanilla: {@code PathNavigation#createPath} returns {@code null} unless
 * the mob is standing, swimming or riding. So any tick that needs a fresh path —
 * and a fight needs one constantly, because the target moves — hands back
 * nothing, {@code MoveToTargetSink} fails to start, and it erases
 * {@code WALK_TARGET} on the way out. She leaves the ground with an intent and
 * lands with none.
 *
 * <p>From outside that looks like the jump costing her the approach: she hops
 * straight up, the zombie walks a block, and she comes down having given away a
 * step for nothing. It is not a jump that is badly aimed, it is a jump she was
 * not allowed to fly.
 *
 * <p>What is restored here is exactly what vanilla takes away and no more.
 * {@code LivingEntity#travel} accelerates an airborne body by
 * {@code flyingSpeed} — 0.02 per tick — and then applies 0.91 air friction, so
 * holding a direction settles at about 0.2 blocks per tick, which is walking
 * pace. Adding that same 0.02 by hand reproduces a player's air control to the
 * decimal, and needs no cap: the friction that already runs every tick is the
 * cap. A number of my own choosing here would have been a maid who steers in
 * mid-air better than the person she is following.
 */
public final class AirControl {
    /**
     * Vanilla's airborne acceleration, per tick.
     *
     * <p>{@code LivingEntity.flyingSpeed}, which is what a player's held
     * direction is worth while falling. Copied rather than tuned — see above.
     */
    private static final double AIR_ACCELERATION = 0.02D;

    /** Air drag per tick, which is what turns that acceleration into a speed. */
    private static final double AIR_DRAG = 0.91D;

    /**
     * The fastest steering alone can carry her: about 0.2 blocks a tick.
     *
     * <p>Derived, not chosen — it is where {@link #AIR_ACCELERATION} and
     * {@link #AIR_DRAG} balance, so it is exactly the speed a player reaches by
     * holding a direction after a standing jump. It is a floor as much as a
     * ceiling: someone who leaves the ground faster than this keeps what they
     * had, because air control has never been able to slow a jump down either.
     */
    private static final double STEERING_PACE =
            AIR_ACCELERATION * AIR_DRAG / (1.0D - AIR_DRAG);

    /** Below this there is no direction to steer in, only rounding noise. */
    private static final double NEGLIGIBLE = 1.0E-4D;

    private AirControl() {
    }

    /**
     * 上一格要赔进去多少格的领先。
     *
     * <p>推出来的，不是拍的：一次跳跃十一 tick，那十一 tick 里她只能靠空中转向挪
     * {@link #driftOver} 那么远（约 0.99 格），而平地上追她的东西同样十一 tick 走
     * 的是走路速度乘十一（约 2.2 格）。差额就是这一格的代价。
     *
     * <p>还没算撞停那一下——原版要她贴着方块面才发出跳跃，而碰撞会把水平速度清零。
     * 所以这个数是**下限**。
     */
    public static double climbCost() {
        int airborne = JumpStrike.landsAt();
        return Math.max(
                0.0D, STEERING_PACE * airborne - driftOver(airborne)
        );
    }

    /**
     * 一次滞空里，光靠空中转向她能横向挪多远。
     *
     * <p>就是把上面那两个常数积起来：每 tick 先加 {@link #AIR_ACCELERATION}、位移、
     * 再乘 {@link #AIR_DRAG}。十一 tick（一次完整跳跃）约 0.99 格。
     *
     * <p>{@link JumpStrike} 拿它当"跳跃预判圈"的宽度。这样那个圈是**推出来的**而不
     * 是拍出来的：一跳能挪多远，跳跃判据就只敢看多远。之前没有这个界，判据靠对方
     * 的冲刺速度外推，于是她会朝四格外正在冲过来的东西起跳、落进包围里——实测持剑
     * 83t 掉到 4t。界必须来自**她自己**能走多远，不能来自对方冲多快。
     */
    public static double driftOver(int ticks) {
        double speed = 0.0D;
        double travelled = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            speed += AIR_ACCELERATION;
            travelled += speed;
            speed *= AIR_DRAG;
        }
        return travelled;
    }

    /**
     * Push her a little further along the way she already wanted to go.
     *
     * <p>Silent when she is standing: on the ground the navigation is steering
     * and this would be a second helping of acceleration on top of it. In the
     * air it is deliberately <em>not</em> conditioned on whether the navigation
     * still holds a path, because that question cannot be answered usefully —
     * a path can survive the leap and still be the retreat she wanted a tick
     * ago, and {@code createPath} returning null does not clear the old one. So
     * the guard is on the outcome instead of on the cause: the speed cap below
     * means that steering which duplicates the navigation's own costs nothing,
     * and steering which replaces a stale path is free to act.
     *
     * <p>Water, ladders and riding are left alone for the same reason they are
     * left alone in {@link JumpStrike}: each has its own movement rules and none
     * of them is the one being repaired.
     *
     * @param destination where she was headed before she left the ground
     * @param stopWithin  how close counts as arrived, in blocks
     */
    public static void steer(
            EntityMaid maid,
            Vec3 destination,
            double stopWithin
    ) {
        if (maid.onGround()
                || maid.isInWater()
                || maid.onClimbable()
                || maid.isPassenger()
                || maid.isFallFlying()) {
            return;
        }
        double dx = destination.x - maid.getX();
        double dz = destination.z - maid.getZ();
        double gap = Math.sqrt(dx * dx + dz * dz);
        if (gap < NEGLIGIBLE || gap <= stopWithin) {
            return;
        }
        Vec3 momentum = maid.getDeltaMovement();
        double vx = momentum.x + dx / gap * AIR_ACCELERATION;
        double vz = momentum.z + dz / gap * AIR_ACCELERATION;
        // Never faster than she already was, and never slower than steering
        // alone would manage. Both halves are what a player gets: a running
        // jump keeps its run, a standing one accelerates to a walk, and neither
        // can be steered into something quicker than either.
        double carried = Math.hypot(momentum.x, momentum.z);
        double ceiling = Math.max(carried, STEERING_PACE);
        double asked = Math.hypot(vx, vz);
        if (asked > ceiling) {
            vx = vx / asked * ceiling;
            vz = vz / asked * ceiling;
        }
        maid.setDeltaMovement(vx, momentum.y, vz);
        maid.hasImpulse = true;
    }
}
