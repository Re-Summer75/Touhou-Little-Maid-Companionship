package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.DodgePolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.GuardFacingPolicy;

/**
 * 不挨那一下的两段算术：面对敌人后退时脸怎么转，以及射来的箭要不要让。
 *
 * <p>玩家报的是两件事——"后退时身体会抽搐"和"不会预判躲开远程"。它们看起来无关，
 * 修法却落在同一处：**别每 tick 重新决定，也别瞬时生效**。所以两段算术放在一起问。
 *
 * <p>这里最要紧的一条是 {@link #verifiesFacingNeverBendsHerPath()}：保持窗口敢开到
 * 一秒，全靠"姿态只影响脸、不影响脚"这个性质成立。它一旦不成立，回差就从修抽搐
 * 变成了把她往奇怪的地方带一整秒，而那种 bug 在游戏里极难看出是从哪儿来的。
 */
public final class EvasionVerification {
    /** 骷髅箭每 tick 的位移。 */
    private static final double ARROW_SPEED = 1.6D;

    /** 女仆碰撞箱半宽。 */
    private static final double HALF_WIDTH = 0.3D;

    private static final double TOLERANCE = 1.0E-6D;

    private EvasionVerification() {
    }

    public static void main(String[] args) {
        verifiesSheTurnsRatherThanSnaps();
        verifiesSheTurnsTheShortWayRoundTheWrap();
        verifiesTheTurnActuallyArrives();
        verifiesFacingNeverBendsHerPath();
        verifiesBackwardsIsBackwards();
        verifiesAnArrowFromAcrossTheRoomIsSeenComing();
        verifiesAPointBlankShotIsNotDodged();
        verifiesAShotGoingWideIsIgnored();
        verifiesAShotAlreadyPastHerIsIgnored();
        verifiesSteppingAsideActuallyWidensTheMiss();
        verifiesLettingItPassIsOneStepNotASprint();
        verifiesADeadOnShotStillHasASide();
        verifiesARaisedShieldMakesDodgingPointless();
        verifiesSheKeepsGoingWhileSheLeans();
        System.out.println("Evasion verification passed.");
    }

    // ---- 面对敌人后退 ----

    /** 一 tick 转不过一个上限——瞬时赋值正是玩家看到的那一下抽搐。 */
    private static void verifiesSheTurnsRatherThanSnaps() {
        GuardFacingPolicy policy = GuardFacingPolicy.INSTANCE;
        float stepped = policy.stepToward(0.0F, 180.0F);
        require(
                Math.abs(stepped) <= GuardFacingPolicy.TURN_DEGREES_PER_TICK
                        + TOLERANCE,
                "回身一百八十度是一 tick 转完的，那是瞬移不是转身"
        );
        require(
                Math.abs(policy.stepToward(0.0F, 5.0F) - 5.0F) < TOLERANCE,
                "只差五度也要走满一步——转速上限成了转速下限"
        );
    }

    /**
     * 跨过 ±180 时走近路。
     *
     * <p>不绕回去的话，从 179 度转到 -179 度会被算成三百五十八度的远路——她会为了
     * 两度的差别整整转一圈，而那是"抽搐"里最刺眼的一种。
     */
    private static void verifiesSheTurnsTheShortWayRoundTheWrap() {
        float stepped = GuardFacingPolicy.INSTANCE.stepToward(179.0F, -179.0F);
        require(
                Math.abs(wrapped(stepped - 179.0F)) <= 2.0F + TOLERANCE,
                "为了两度的差别绕了一整圈"
        );
    }

    /** 转下去要到得了，而且到了就停。 */
    private static void verifiesTheTurnActuallyArrives() {
        GuardFacingPolicy policy = GuardFacingPolicy.INSTANCE;
        float yaw = 0.0F;
        for (int tick = 0; tick < 32; tick++) {
            yaw = policy.stepToward(yaw, 170.0F);
        }
        require(
                Math.abs(wrapped(yaw - 170.0F)) < TOLERANCE,
                "转了三十二 tick 还没转到"
        );
        require(
                Math.abs(wrapped(policy.stepToward(yaw, 170.0F) - 170.0F))
                        < TOLERANCE,
                "到了之后还在动"
        );
    }

    /**
     * 无论她的脸转到哪里，脚下走的仍是行进方向。
     *
     * <p>保持窗口能开到一秒，靠的就是这条：姿态只改朝向，不改她走去哪。
     */
    private static void verifiesFacingNeverBendsHerPath() {
        GuardFacingPolicy policy = GuardFacingPolicy.INSTANCE;
        float travelYaw = 37.0F;
        for (float yaw = -180.0F; yaw <= 180.0F; yaw += 15.0F) {
            double forward = policy.forwardShare(yaw, travelYaw);
            double strafe = policy.strafeShare(yaw, travelYaw);
            // MC 的 moveRelative：world = 绕 yaw 旋转 (横移, 前进)。
            double radians = Math.toRadians(yaw);
            double worldX = strafe * Math.cos(radians)
                    - forward * Math.sin(radians);
            double worldZ = forward * Math.cos(radians)
                    + strafe * Math.sin(radians);
            double wantedX = -Math.sin(Math.toRadians(travelYaw));
            double wantedZ = Math.cos(Math.toRadians(travelYaw));
            require(
                    Math.abs(worldX - wantedX) < 1.0E-5D
                            && Math.abs(worldZ - wantedZ) < 1.0E-5D,
                    "脸转到 " + yaw + " 度时她走岔了方向"
            );
        }
    }

    /** 正对行进方向是全速前进，背对是全速后退，两头都不横移。 */
    private static void verifiesBackwardsIsBackwards() {
        GuardFacingPolicy policy = GuardFacingPolicy.INSTANCE;
        require(
                Math.abs(policy.forwardShare(90.0F, 90.0F) - 1.0D) < TOLERANCE
                        && Math.abs(policy.strafeShare(90.0F, 90.0F))
                                < TOLERANCE,
                "正对行进方向却不是径直往前"
        );
        require(
                Math.abs(policy.forwardShare(-90.0F, 90.0F) + 1.0D) < TOLERANCE
                        && Math.abs(policy.strafeShare(-90.0F, 90.0F))
                                < TOLERANCE,
                "背对行进方向却不是倒着走"
        );
    }

    // ---- 让开射来的箭 ----

    /**
     * 十六格外一箭正对她射来：看得见，也来得及。
     *
     * <p>十六格是她的感知半径，所以这是她**最早**能注意到的那一刻。
     */
    private static void verifiesAnArrowFromAcrossTheRoomIsSeenComing() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double ticks = policy.ticksToClosest(0.0D, 16.0D, 0.0D, ARROW_SPEED);
        require(
                Math.abs(ticks - 10.0D) < 0.001D,
                "十六格、每 tick 一点六格，算出来不是十 tick"
        );
        require(policy.worthDodging(ticks), "十 tick 的预警她也不动");
        require(
                policy.wouldHit(
                        policy.missDistance(
                                0.0D, 16.0D, 0.0D, ARROW_SPEED, ticks
                        ),
                        HALF_WIDTH
                ),
                "正对她射来的一箭被判成打不中"
        );
    }

    /**
     * 贴脸的一箭躲不掉，她也就不试。
     *
     * <p>这条是这个能力**不是超人**的全部保证。取消它她会在零点几秒里横移出半格，
     * 那不是熟练，那是作弊。
     */
    private static void verifiesAPointBlankShotIsNotDodged() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double ticks = policy.ticksToClosest(0.0D, 5.0D, 0.0D, ARROW_SPEED);
        require(ticks > 0.0D, "五格外射来的箭被算成正在离开");
        require(
                !policy.worthDodging(ticks),
                "三 tick 的预警里她也躲开了——那是超人不是女仆"
        );
    }

    /** 从旁边过去的不必理会。 */
    private static void verifiesAShotGoingWideIsIgnored() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double ticks = policy.ticksToClosest(2.0D, 16.0D, 0.0D, ARROW_SPEED);
        require(
                !policy.wouldHit(
                        policy.missDistance(
                                2.0D, 16.0D, 0.0D, ARROW_SPEED, ticks
                        ),
                        HALF_WIDTH
                ),
                "两格开外擦过去的一箭也被当成要打中她"
        );
    }

    /** 已经飞过去的不必理会——最近点在过去，时间为负。 */
    private static void verifiesAShotAlreadyPastHerIsIgnored() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double ticks = policy.ticksToClosest(0.0D, -8.0D, 0.0D, ARROW_SPEED);
        require(ticks < 0.0D, "背后飞走的箭算出了正的到达时间");
        require(!policy.worthDodging(ticks), "她在躲一支已经过去的箭");
    }

    /** 让开的那一侧，确实是让开的那一侧。 */
    private static void verifiesSteppingAsideActuallyWidensTheMiss() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        // 稍稍偏右：最近点会在她左侧零点二格处，该往右让。
        double rx = 0.2D;
        double rz = 16.0D;
        double ticks = policy.ticksToClosest(rx, rz, 0.0D, ARROW_SPEED);
        float bearing = policy.sidestepBearing(
                rx, rz, 0.0D, ARROW_SPEED, ticks, true
        );
        double stepX = -Math.sin(Math.toRadians(bearing));
        double stepZ = Math.cos(Math.toRadians(bearing));
        double before = policy.missDistance(rx, rz, 0.0D, ARROW_SPEED, ticks);
        double after = policy.missDistance(
                rx + stepX, rz + stepZ, 0.0D, ARROW_SPEED, ticks
        );
        require(after > before, "她朝弹道让了一步");
        require(
                Math.abs(after - (before + 1.0D)) < 1.0E-5D,
                "让的方向不是垂直于弹道——挪一格却没换来一格"
        );
    }

    /**
     * 让够了就收势，而"够"刚好越过命中判据再多一点。
     *
     * <p>这条钉的是幅度上界。第一版按飞行时间保持，同一个数在她慢走和满速下差着
     * 一个数量级——实测她以满速横移了八格、冲出了房间。把量表达成距离之后，"让开
     * 半个身位"在两种情形下都是同一件事。
     */
    private static void verifiesLettingItPassIsOneStepNotASprint() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double needed = policy.clearanceNeeded(HALF_WIDTH);
        require(
                !policy.wouldHit(needed, HALF_WIDTH),
                "让满了仍然判成会打中——那她永远收不了势"
        );
        require(
                needed <= 1.0D,
                "让开一次要走 " + needed + " 格，那不是让一步"
        );
    }

    /** 正中弹道时偏移量退化，仍要挑出一侧，且两侧都是垂直的。 */
    private static void verifiesADeadOnShotStillHasASide() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        double ticks = policy.ticksToClosest(0.0D, 16.0D, 0.0D, ARROW_SPEED);
        float left = policy.sidestepBearing(
                0.0D, 16.0D, 0.0D, ARROW_SPEED, ticks, true
        );
        float right = policy.sidestepBearing(
                0.0D, 16.0D, 0.0D, ARROW_SPEED, ticks, false
        );
        require(
                Math.abs(Math.abs(wrapped(left - right)) - 180.0F) < 0.001F,
                "两侧不是相反的两个方向"
        );
        for (float bearing : new float[]{left, right}) {
            double alongFlight = Math.cos(Math.toRadians(bearing));
            require(
                    Math.abs(alongFlight) < 1.0E-5D,
                    "让的方向带着沿弹道的分量——那部分挪动一点用也没有"
            );
        }
    }

    /** 挡得住就不必躲；从背后来的挡不住。 */
    private static void verifiesARaisedShieldMakesDodgingPointless() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        // 她朝北看（视线 -Z），箭从北面来：从箭指向她的向量是 +Z，点积为负。
        require(
                policy.shieldCovers(-1.0D),
                "正对着来箭却判成挡不住"
        );
        require(
                !policy.shieldCovers(1.0D),
                "背后射来的一箭也算盾挡住了"
        );
    }

    /** 闪一下不该让她忘了原来要去哪。 */
    private static void verifiesSheKeepsGoingWhileSheLeans() {
        DodgePolicy policy = DodgePolicy.INSTANCE;
        float leaned = policy.lean(0.0F, 90.0F);
        require(
                Math.abs(wrapped(leaned - 45.0F)) < 1.0E-4F,
                "行进与闪避各差九十度，合出来的却不是正中间"
        );
        require(
                Math.abs(wrapped(policy.lean(0.0F, 180.0F) - 180.0F))
                        < 1.0E-4F,
                "两个方向恰好相反时没有让闪避说了算"
        );
    }

    private static float wrapped(float degrees) {
        float value = degrees % 360.0F;
        if (value > 180.0F) {
            value -= 360.0F;
        }
        if (value <= -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    private static void require(boolean condition, String complaint) {
        if (!condition) {
            throw new AssertionError(complaint);
        }
    }
}
