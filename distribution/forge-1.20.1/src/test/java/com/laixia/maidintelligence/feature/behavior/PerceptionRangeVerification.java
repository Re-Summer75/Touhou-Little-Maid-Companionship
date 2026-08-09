package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;

/**
 * 感知半径是所有行为的地基，这里钉住它与其他距离的关系。
 *
 * <p>写这组断言的直接原因：自由模式曾把搜索盒覆盖为女仆自己的碰撞箱，
 * 于是她感知不到任何实体，战斗意图永远无法触发——而那个覆盖单看是合理的，
 * 只有把"感知"当作一个整体来约束才能发现它错了。距离一旦各写各的，
 * 就会出现"能捡到比能看见更远的东西"这类没人写下来、也没人察觉的矛盾。
 */
public final class PerceptionRangeVerification {
    private PerceptionRangeVerification() {
    }

    /** 浮点求根的容差；牵引绳是几十格量级，这个余量远小于一格。 */
    private static final double EPSILON = 1.0E-9D;

    public static void main(String[] args) {
        verifiesPerceptionIsTheStatedCap();
        verifiesSquaredMatchesLinear();
        verifiesClampNeverWidens();
        verifiesTeleportOutrunsPerception();
        verifiesGazeDoesNotExceedPerception();
        verifiesPlanningStaysInsideTheLeash();
        verifiesLeashNeverPinsAMaidAlreadyOutside();
        verifiesEveryHeadingEndsInsideTheLeash();
        verifiesHeightSpendsLeashToo();
        System.out.println("Perception range verification passed.");
    }

    /**
     * 她自己规划的落点必须严格在传送线以内。
     *
     * <p>兜底传送是"超过就拉回"，所以正好停在线上的计划等于计划被撤销：
     * 一次浮点误差、主人挪半步，她就被拽回刚刚逃离的战斗里。
     */
    private static void verifiesPlanningStaysInsideTheLeash() {
        require(
                OwnerFollowPolicy.INSTANCE.planningRadius(0.0D)
                        < OwnerFollowPolicy.TELEPORT_DISTANCE,
                "She plans right up to the teleport line, so a rounding error "
                        + "is enough to undo the walk she just took"
        );
    }

    /**
     * 已经在绳外时不能把她钉死。
     *
     * <p>规划半径若小于她当前所在，每个方向都会算成"走不了"，于是她站着
     * 不动等传送——而横向挪开和往主人那边走本来都是允许的。
     */
    private static void verifiesLeashNeverPinsAMaidAlreadyOutside() {
        double stranded = OwnerFollowPolicy.TELEPORT_DISTANCE + 10.0D;
        require(
                OwnerFollowPolicy.INSTANCE.planningRadius(stranded) >= stranded,
                "A maid already past the leash was told she may not stand "
                        + "where she is standing"
        );
        require(
                OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                        stranded, 0.0D, 0.0D, -1.0D, 0.0D
                ) > 0.0D,
                "Walking back toward her owner was refused"
        );
        require(
                OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                        stranded, 0.0D, 0.0D, 1.0D, 0.0D
                ) == 0.0D,
                "Walking further out was allowed from outside the leash"
        );
    }

    /**
     * 任何朝向走完允许的距离后都仍在绳内。
     *
     * <p>这是这条几何唯一要保证的事，所以扫一圈朝向、几档起始位置一起验，
     * 而不是只验"正后方"那一个好算的方向。
     */
    private static void verifiesEveryHeadingEndsInsideTheLeash() {
        double radius = OwnerFollowPolicy.INSTANCE.planningRadius(0.0D);
        for (double offset : new double[] {0.0D, 5.0D, 15.0D, 22.0D}) {
            for (int step = 0; step < 24; step++) {
                double angle = step * Math.PI / 12.0D;
                double headingX = Math.cos(angle);
                double headingZ = Math.sin(angle);
                double reach = OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                        offset, 0.0D, 0.0D, headingX, headingZ
                );
                double endX = offset + reach * headingX;
                double endZ = reach * headingZ;
                double ended = Math.sqrt(endX * endX + endZ * endZ);
                require(
                        ended <= radius + EPSILON,
                        "From " + offset + " blocks out, heading " + step
                                + " left her " + ended + " blocks away, past "
                                + "the " + radius + " she may plan to"
                );
                require(
                        !OwnerFollowPolicy.INSTANCE.shouldTeleport(
                                ended * ended
                        ),
                        "A walk she was told she may take ends in a teleport"
                );
            }
        }
    }

    /**
     * 爬高也消耗牵引绳。
     *
     * <p>兜底传送量的是三维距离，而后撤只在水平面上选方向。忽略高度差就会
     * 得出"塔顶上还能再横着走二十格"，而她其实早已超线。
     */
    private static void verifiesHeightSpendsLeashToo() {
        double level = OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                0.0D, 0.0D, 0.0D, 1.0D, 0.0D
        );
        double lifted = OwnerFollowPolicy.INSTANCE.reachBeforeTeleport(
                0.0D, 20.0D, 0.0D, 1.0D, 0.0D
        );
        require(
                lifted < level,
                "Standing twenty blocks above her owner bought her no less "
                        + "ground than standing beside him"
        );
    }

    private static void verifiesPerceptionIsTheStatedCap() {
        require(
                PerceptionRange.BLOCKS == 16.0D,
                "Perception is no longer the agreed sixteen blocks"
        );
    }

    /** 平方值是给比距离的调用方用的，必须始终与线性值一致。 */
    private static void verifiesSquaredMatchesLinear() {
        require(
                PerceptionRange.SQUARED
                        == PerceptionRange.BLOCKS * PerceptionRange.BLOCKS,
                "Squared perception drifted from its linear value"
        );
    }

    /**
     * clamp 只收窄，不放宽。
     *
     * <p>活动半径是可配置的，玩家把它调到 64 也不该让她看得更远——
     * 否则"感知统一 16"就只是一句注释。
     */
    private static void verifiesClampNeverWidens() {
        require(
                PerceptionRange.clamp(64.0D) == PerceptionRange.BLOCKS,
                "A wide activity radius widened perception with it"
        );
        require(
                PerceptionRange.clamp(4.0D) == 4.0D,
                "A deliberately narrow reach was widened to the cap"
        );
        require(
                PerceptionRange.clamp(PerceptionRange.BLOCKS)
                        == PerceptionRange.BLOCKS,
                "Clamping at exactly the cap changed the value"
        );
    }

    /**
     * 传送距离必须大于感知距离。
     *
     * <p>她只会走向已经注意到的东西，所以牵引绳必须比视野长；两者相等时，
     * 一次发生在感知边缘的差事会在半路把她扯回去，看起来像瞬移 bug
     * 而不像规则。
     */
    private static void verifiesTeleportOutrunsPerception() {
        require(
                OwnerFollowPolicy.TELEPORT_DISTANCE > PerceptionRange.BLOCKS,
                "The teleport leash is no longer than perception, so errands "
                        + "at the edge of sight get cut short"
        );
    }

    /** 注视召唤也是一种感知，不该看得比感知更远。 */
    private static void verifiesGazeDoesNotExceedPerception() {
        require(
                GazeRecallPolicy.DEFAULT_RANGE <= PerceptionRange.BLOCKS,
                "Gaze recall reaches past what she can perceive"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
