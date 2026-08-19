package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * 一次跳跃的飞行控制：起跳那一刻上锁，落地那一刻清账。
 *
 * <p>锁着的每一帧只做三件事：转向指回锁定落点（路标与走目标半路怎么变都拽
 * 不动这一跳）、按弧线合同对账（起跳 tick 的地面摩擦吞一半冲量，谁偷都补回
 * 来）、落地把余速收到走路量级（带着跳劲冲线就是从桥尾另一头冲下去）。
 */
final class LeapFlight {
    /** 滞空每 tick 的自然衰减（原版空气阻力），弧线合同按它算。 */
    private static final double AIR_DRAG = 0.91D;

    /** 起跳后一直没离地就放弃锁定的时限（卡在什么东西上了）。 */
    private static final int GIVE_UP_TICKS = 30;

    /** 落地那一 tick 把水平余速收到这个量级——跳是跳，走是走。 */
    private static final double LANDING_TROT = 0.15D;

    private final Mob mob;
    private final SureFootedNavigation nav;

    /** 这一跳锁死的落点；空中转向只认它。null = 没在跳。 */
    private Vec3 aim;
    private double speed;
    private double dirX;
    private double dirZ;
    private boolean aloft;
    private int since;

    LeapFlight(Mob mob, SureFootedNavigation nav) {
        this.mob = mob;
        this.nav = nav;
    }

    boolean locked() {
        return aim != null;
    }

    /** 上落点锁：滞空转向从此只认这一个点，落地才解。 */
    void lock(BlockPos landing, double speed, double dirX, double dirZ) {
        lock(new Vec3(landing.getX() + 0.5D, landing.getY(),
                landing.getZ() + 0.5D), speed, dirX, dirZ);
    }

    /** 亚格瞄点版：穿缝的跳落在车道坐标上，不落格心。 */
    void lock(Vec3 landing, double speed, double dirX, double dirZ) {
        this.aim = landing;
        this.speed = speed;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.aloft = false;
        this.since = mob.tickCount;
    }

    /** 滞空期间落点锁死；落地（或落水——水接住也算到）收腿后解锁。 */
    void steer() {
        if (!mob.onGround() && !mob.isInWater()) {
            aloft = true;
            restoreTheArc();
        } else if (aloft || mob.tickCount - since > GIVE_UP_TICKS) {
            Vec3 landed = mob.getDeltaMovement();
            double trot = Math.hypot(landed.x, landed.z);
            if (trot > LANDING_TROT) {
                mob.setDeltaMovement(
                        landed.x / trot * LANDING_TROT,
                        landed.y,
                        landed.z / trot * LANDING_TROT
                );
            }
            aim = null;
            return;
        }
        mob.getMoveControl().setWantedPosition(
                aim.x, aim.y, aim.z, nav.pace()
        );
    }

    /**
     * 弧线是合同：滞空第 t tick 的水平速度就该是初速乘 0.91 的 t 次方。
     * 只补不削；落点到了头顶或已在身后就停手交给重力。
     */
    private void restoreTheArc() {
        int t = mob.tickCount - since;
        double meant = speed * Math.pow(AIR_DRAG, t);
        Vec3 velocity = mob.getDeltaMovement();
        double horizontal = Math.hypot(velocity.x, velocity.z);
        if (horizontal >= meant - 1.0E-3D) {
            return;
        }
        double aimX = aim.x - mob.getX();
        double aimZ = aim.z - mob.getZ();
        double flat = Math.hypot(aimX, aimZ);
        if (flat < 0.4D || aimX * dirX + aimZ * dirZ <= 0.0D) {
            return;
        }
        mob.setDeltaMovement(
                aimX / flat * meant, velocity.y, aimZ / flat * meant
        );
    }
}
