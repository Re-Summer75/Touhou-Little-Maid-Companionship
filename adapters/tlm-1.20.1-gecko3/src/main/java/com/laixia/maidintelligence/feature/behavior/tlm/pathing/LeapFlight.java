package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import com.laixia.maidintelligence.feature.behavior.domain.motion.BallisticArc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 一次跳跃的飞行控制：起跳那一刻上锁，落地那一刻清账。
 *
 * <p>锁着的每一帧只做三件事：转向指回锁定落点（路标与走目标半路怎么变都拽
 * 不动这一跳）、按弧线合同对账（起跳 tick 的地面摩擦吞一半冲量，谁偷都补回
 * 来）、落地把余速收到走路量级（带着跳劲冲线就是从桥尾另一头冲下去）。
 */
public final class LeapFlight {
    /** 滞空每 tick 的自然衰减（原版空气阻力），弧线合同按它算。
     *  包内共享：{@code Leaper} 的起跳配速解的是同一条弧线；数值本身
     *  以 {@code BallisticArc} 为唯一来源——图、执行、扫掠仿真三处必须
     *  同一套物理，否则"图连了边执行走不过"的两张皮就会回来。 */
    static final double AIR_DRAG = BallisticArc.HORIZONTAL_DRAG;

    /** 起跳后一直没离地就放弃锁定的时限（卡在什么东西上了）。 */
    private static final int GIVE_UP_TICKS = 30;

    /** 锁定后尚未离地那几 tick 的步速：够她挪到沿边，不够她冲出落点。 */
    private static final double CREEP_PACE = 0.3D;

    /** 落地那一 tick 把水平余速收到这个量级——跳是跳，走是走。
     *  必须低于崖边看护的介入线（0.15）：从前正卡在线上，落地残速带着
     *  她滑过一格宽拐角，看护睁眼时人已经在沿外（竖向 L 下跳实测摔点）。 */
    private static final double LANDING_TROT = 0.10D;

    private final Mob mob;
    private final SureFootedNavigation nav;

    /** 这一跳锁死的落点；空中转向只认它。null = 没在跳。 */
    private Vec3 aim;
    private double speed;
    private double dirX;
    private double dirZ;
    private boolean aloft;
    private int since;

    public LeapFlight(Mob mob, SureFootedNavigation nav) {
        this.mob = mob;
        this.nav = nav;
    }

    public boolean locked() {
        return aim != null;
    }

    /** 上落点锁：滞空转向从此只认这一个点，落地才解。 */
    public void lock(BlockPos landing, double speed, double dirX, double dirZ) {
        lock(new Vec3(landing.getX() + 0.5D, landing.getY(),
                landing.getZ() + 0.5D), speed, dirX, dirZ);
    }

    /** 亚格瞄点版：穿缝的跳落在车道坐标上，不落格心。 */
    public void lock(Vec3 landing, double speed, double dirX, double dirZ) {
        this.aim = landing;
        this.speed = speed;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.aloft = false;
        this.since = mob.tickCount;
    }

    /**
     * 属性到位移的换算：每单位 {@code MOVEMENT_SPEED} 值多少格每 tick。
     *
     * <p>拿原版实体校准：僵尸属性 0.23、倍率 1.0，实测约 4.3 格每秒，
     * 即 0.215 格每 tick——0.215 ÷ 0.23 ≈ 0.935。原版是把
     * "倍率 × 属性"喂给 {@code travel()} 当加速度、由摩擦收敛出稳态速
     * 度；自有执行器直写位移，就得自己走完这段换算。
     */
    private static final double GROUND_PACE = 0.935D;

    /** 属性读不到时的兜底身手（女仆实测约 0.51）。 */
    private static final double ASSUMED_SPEED = 0.5D;

    /**
     * 走路的每 tick 位移，**按原版的口径算**：速度倍率 × 移动速度属性。
     *
     * <p>自有执行器直写 {@code deltaMovement}，把原版那条
     * "{@code MoveControl} → 倍率 × MOVEMENT_SPEED → {@code travel()}"
     * 整个绕了过去——倍率与属性都不起作用，她只剩一档手调的步速。代价
     * 是玩家点名的两桩：跟随跟不上（她 3.2 格每秒，玩家走路就有 4.3、
     * 冲刺 5.6），战斗撤不开被怪追上。
     *
     * <p>算出来跟随约 5.2 格每秒、战斗约 5.7，正好咬住冲刺档——这不是
     * 调出来的数，是把原版那条链补完的结果。
     */
    public double paceStep() {
        double pace = nav.pace();
        if (pace <= 1.0E-6D) {
            // 没给倍率不等于要她站着：漏传一档就把人钉死，代价太大。
            pace = 1.0D;
        }
        double attr = mob.getAttribute(Attributes.MOVEMENT_SPEED) == null
                ? ASSUMED_SPEED
                : mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return pace * attr * GROUND_PACE;
    }

    /** 滞空期间落点锁死；落地（或落水——水接住也算到）收腿后解锁。 */
    public void steer() {
        // **没起飞的锁到点就撤，而且要第一个判**：迈步被挡、人根本没离
        // 地时，若锁的落点恰在脚下近处，下面的下行刹车分支会每 tick 提
        // 前返回，超时解锁永远轮不到——锁死整场（栅栏圈实测：note=flight
        // 挂九百四十 tick，路都清了锁还在）。
        if (!aloft && mob.onGround()
                && mob.tickCount - since > GIVE_UP_TICKS) {
            aim = null;
            return;
        }
        // **滞空侧也要有超时**：卡骑在栏杆上、嵌在栅栏拐角里的姿态
        // onGround 恒假，上面的地面超时永远不触发，下行刹车分支每 tick
        // 提前返回——锁挂整场（栅栏圈实测 note=flight 九百四十 tick）。
        // 最长的弧线合同二十来 tick，滞空翻三倍还没落就不是在飞。
        if (mob.tickCount - since > GIVE_UP_TICKS * 2) {
            aim = null;
            return;
        }
        if (!mob.onGround() && !mob.isInWater()) {
            aloft = true;
            restoreTheArc();
            // 下行到柱即坠：已经飘到落柱正上方就不再往前带——弧线合同只
            // 补不削，而每 tick 的空中转向又让移动控制持续加速，十几 tick
            // 能把落地速度堆到走路量级；一格宽拐角上这份冲劲就是滑出侧沿
            // 的那一下（竖向 L 从高臂跳下的实测摔点，落点是半砖时更甚）。
            // 停灯、削过冲；但阻尼留底（0.08）——刹到零会把唇沿自救的小
            // 跳半路杀死，落回原唇再跳、循环成机枪（之字梯 184 连跳实测）。
            //
            // **半格这道门槛不能再放宽。**放到 0.2 试过一轮：刹车提前到滞空
            // 中段就介入，机枪当场回来（玻璃行往返 284 连跳）。孤台落点的过
            // 冲另有去处——{@code Leaper} 收瞄点，不动这里的推力。
            if (aim.y < mob.getY() - 0.5D
                    && Math.hypot(aim.x - mob.getX(), aim.z - mob.getZ())
                            < 0.4D) {
                Vec3 falling = mob.getDeltaMovement();
                double flat = Math.hypot(falling.x, falling.z);
                if (flat > 0.08D) {
                    double keep = Math.max(0.08D, flat * 0.5D);
                    mob.setDeltaMovement(
                            falling.x / flat * keep,
                            falling.y,
                            falling.z / flat * keep
                    );
                }
                mob.getMoveControl().setWantedPosition(
                        mob.getX(), aim.y, mob.getZ(), 0.0D
                );
                return;
            }
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
        // 还没离地的那几 tick：移动控制也得跟着慢。
        //
        // 普通跳跃感觉不到这一条——起跳那一刻就离地，地面相只有一 tick。
        // 可**下台阶这类锁是从地面上开始的**：她要先走到沿边才腾空，而这
        // 段路若按 nav.pace() 全速推，她在离地前就被推到了走速，带着那份
        // 速度飞过短落点（倒 T 实测：落在竖笔顶上时 0.05，三 tick 后 0.28，
        // 冲出横杠东沿两格半）。
        //
        // 顺带记一条更普遍的：**写速度收不住她**——收步、收腿、崖边刹停
        // 都是每 tick 写一次 deltaMovement，而移动控制紧接着又按全速把她推
        // 回去。要她慢，就得把控制器的步速一起降下来。
        // **滞空时移动控制不插手**：弧线由 restoreTheArc 按合同管着，
        // 多一份推力就是多一份偏差。这一行原本写的是 nav.pace()，而那
        // 个值一直是 0（自有分支从不走 super.moveTo，speedModifier 没人
        // 赋值）——于是"滞空按步速推"从未真正发生过，整套弹道其实是在
        // 无推力下标定的。把倍率接上之后它当场开始推，落点全偏，末地烛
        // 中继十副本齐摔。零推力才是这套弧线合同的前提。
        mob.getMoveControl().setWantedPosition(
                aim.x, aim.y, aim.z, aloft ? 0.0D : CREEP_PACE
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
