package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.combat.SpacingPolicy;

/**
 * 她该站在哪：退多少、贴多近。
 */
public final class CombatSpacingVerification {
    private static final SpacingPolicy POLICY = SpacingPolicy.INSTANCE;

    /**
     * 一个典型的近战对峙。
     *
     * <p>刻意让两边一样长：原版的近战距离由体型算出，而女仆和僵尸体型相同，
     * 所以这才是最常见的情形，不是边界情形。
     */
    private static final double HER_REACH = 2.0D;
    private static final double ZOMBIE_REACH = 2.0D;

    private CombatSpacingVerification() {
    }

    public static void main(String[] args) {
        verifiesRetreatTakesBackMoreThanItLost();
        verifiesRetreatStopsBeingNeededSomewhere();
        verifiesSwingReadyMeansCloseIn();
        verifiesRecoveryIsSpentOutOfReach();
        verifiesEqualReachStillEarnsAStepBack();
        verifiesPinnedSheFightsToeToToe();
        verifiesMeleeGivesNoOvershoot();
        System.out.println("Combat spacing verification passed.");
    }

    /**
     * 退一步换一步等于原地打拍子。
     *
     * <p>玩家看到的是"女仆走一步敌人走一步女仆又走一步"：目标逼近一格，她就
     * 只退回那一格，于是每次修正刚好被引发它的那一步吃掉，两个都在原地踏步。
     * 让开的距离必须够用，否则不如不让。
     */
    private static void verifiesRetreatTakesBackMoreThanItLost() {
        double lost = 1.0D;
        double given = POLICY.groundToGive(8.0D, 8.0D - lost);
        require(
                given > lost,
                "She gave up " + given + " blocks to recover " + lost
                        + ", so the next step undoes it and both mark time"
        );
    }

    /** 但也不能反过来：已经站得够远时不该继续后退。 */
    private static void verifiesRetreatStopsBeingNeededSomewhere() {
        require(
                POLICY.groundToGive(8.0D, 30.0D) == 0.0D,
                "Thirty blocks out she was still told to back away"
        );
        require(
                POLICY.groundToGive(8.0D, 8.0D) > 0.0D,
                "Standing exactly at the held range earns no margin at all, "
                        + "which is where the step-for-step shuffle starts"
        );
    }

    /** 能挥刀的时候就进去挥——那一下才是目的。 */
    private static void verifiesSwingReadyMeansCloseIn() {
        require(
                POLICY.meleeHold(true, ZOMBIE_REACH, true) == 0.0D,
                "With her attack recovered she still held off instead of "
                        + "closing to use it"
        );
    }

    /**
     * 冷却期间不该站在对方打得到的地方。
     *
     * <p>挥完一刀有恢复时间，把这段时间花在对方的攻击距离里是纯送：她打不了，
     * 它能打。这段时间该待在它的攻击距离之外——玩家说的"不应该直接贴脸上被
     * 敌人打"。
     */
    private static void verifiesRecoveryIsSpentOutOfReach() {
        double hold = POLICY.meleeHold(false, ZOMBIE_REACH, true);
        require(
                hold > ZOMBIE_REACH,
                "During recovery she held " + hold + " blocks, still inside "
                        + "the target's " + ZOMBIE_REACH + "-block reach"
        );
    }

    /**
     * 体型相同的对手照样要拉开——优势来自时间，不是臂长。
     *
     * <p>这条直指一个真实缺陷：判据一度要求"存在一个它够不着而她够得着的位置"，
     * 而原版近战距离由体型算出，女仆和僵尸一样大,两边的攻击距离完全相同,那个
     * 位置永远不存在。于是她对着最常见的敌人恒定贴脸硬抗。真正的收益是它走回
     * 来那段路——那段时间她不挨打——跟谁的手长无关。
     */
    private static void verifiesEqualReachStillEarnsAStepBack() {
        double hold = POLICY.meleeHold(false, HER_REACH, true);
        require(
                hold > HER_REACH,
                "Against something with her own reach she held " + hold
                        + " and stayed inside it, which is standing there "
                        + "taking hits during every recovery"
        );
    }

    /**
     * 退不掉的时候就贴着换血。
     *
     * <p>"距离收益"真正的判据是能不能进出，而不是臂长之差：没有地方退，或者
     * 对方跟得上她，那退出去就只是丢掉自己的攻击，对方一分钱不少赚。
     */
    private static void verifiesPinnedSheFightsToeToToe() {
        require(
                POLICY.meleeHold(false, ZOMBIE_REACH, false) == 0.0D,
                "Unable to disengage she still gave ground, which costs her "
                        + "the swing and spares the target nothing"
        );
    }

    /** 近战不该像远程那样多退一截——那段路她的下一刀还得走回来。 */
    private static void verifiesMeleeGivesNoOvershoot() {
        double melee = POLICY.groundToGive(3.0D, 1.0D, 0.0D);
        double ranged = POLICY.groundToGive(3.0D, 1.0D);
        require(
                melee < ranged,
                "Melee gave up as much ground as a ranged retreat (" + melee
                        + "), so the whole recovery is spent walking back"
        );
        require(
                melee > 0.0D,
                "Melee gave up no ground at all"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
