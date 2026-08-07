package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.ai.domain.OwnerFollowPolicy;
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

    public static void main(String[] args) {
        verifiesPerceptionIsTheStatedCap();
        verifiesSquaredMatchesLinear();
        verifiesClampNeverWidens();
        verifiesTeleportOutrunsPerception();
        verifiesGazeDoesNotExceedPerception();
        System.out.println("Perception range verification passed.");
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
