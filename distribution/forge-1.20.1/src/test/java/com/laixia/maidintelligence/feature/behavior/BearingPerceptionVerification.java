package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.perception.BearingField;

/**
 * 方位感知：把"哪个方向"变成可比较的量。
 *
 * <p>此前每个需要方向的决定都自带一份近似——后撤用一组候选角、人群方向用
 * 距离倒数加权、"退无可退"靠搜索失败倒推。三份各调各的，各自在不同处境下
 * 出错。这里只验证一件事：同一份方位场能同时回答"往哪走""被围了没""这是不是
 * 墙角"，而且答案彼此一致。
 */
public final class BearingPerceptionVerification {
    /** 她要挤过去至少需要的空间。 */
    private static final double NEEDED = 2.0D;

    private BearingPerceptionVerification() {
    }

    public static void main(String[] args) {
        verifiesBearingsWrapAround();
        verifiesNearThreatsOutweighFarOnes();
        verifiesOpenSideBeatsEmptyWall();
        verifiesGapTooNarrowIsNotAWayOut();
        verifiesSurroundedNeedsBodiesNotWalls();
        System.out.println("Bearing perception verification passed.");
    }

    /** 角度绕圈不能出错，否则正后方会落到数组外。 */
    private static void verifiesBearingsWrapAround() {
        require(
                BearingField.sectorOf(0.0D)
                        == BearingField.sectorOf(Math.PI * 2.0D),
                "整圈之后没有回到同一个扇区"
        );
        require(
                BearingField.sectorOf(-0.1D)
                        == BearingField.sectorOf(Math.PI * 2.0D - 0.1D),
                "负角度没有绕回去"
        );
    }

    /** 贴在肩上的一只，要压过对面墙根的三只。 */
    private static void verifiesNearThreatsOutweighFarOnes() {
        BearingField field = BearingField.builder()
                .threat(0.0D, 1.0D)
                .threat(Math.PI, 12.0D)
                .threat(Math.PI, 12.0D)
                .threat(Math.PI, 12.0D)
                .build();
        require(
                field.threatDistance(BearingField.sectorOf(0.0D))
                        < field.threatDistance(BearingField.sectorOf(Math.PI)),
                "近处那一只没有压过远处三只，她会朝错误的方向躲"
        );
    }

    /**
     * 最空的方向常常是她已经贴住的那堵墙。
     *
     * <p>只看威胁会选中零压力但零空间的方位，只看空间会选中最开阔、但正对着
     * 追兵的那条。两者必须一起看。
     */
    private static void verifiesOpenSideBeatsEmptyWall() {
        double wall = 0.0D;
        double open = Math.PI;
        BearingField field = BearingField.builder()
                // 墙那边没有敌人，但也没有地
                .room(wall, 0.0D)
                // 开阔那边有一只不近不远的
                .room(open, 10.0D)
                .threat(open, 8.0D)
                .build();
        require(
                field.safestBearing(NEEDED)
                        == BearingField.sectorOf(open),
                "她选了没有敌人但也无路可走的那一侧"
        );
    }

    /**
     * 两只之间的一条缝不算出路。
     *
     * <p>正前方那一格恰好没人，两侧却各站一只——她有宽度，挤过去等于同时进入
     * 两个人的攻击范围。相邻扇区必须计入。
     */
    private static void verifiesGapTooNarrowIsNotAWayOut() {
        int slot = 3;
        BearingField field = BearingField.builder()
                .room(BearingField.bearingOf(slot), 10.0D)
                .room(BearingField.bearingOf(slot + 6), 10.0D)
                .threat(BearingField.bearingOf(slot - 1), 1.5D)
                .threat(BearingField.bearingOf(slot + 1), 1.5D)
                .build();
        require(
                field.clearance(slot) < 10.0D,
                "夹在两只中间的缝隙被算成了完全通畅"
        );
        require(
                field.safestBearing(NEEDED) == slot + 6,
                "她挤进了两只之间的缝，而背后有一整片空地"
        );
    }

    /**
     * "被围住"说的是身体，不是墙。
     *
     * <p>两者要求相反的应对——被围要突围，靠墙要转身打——所以不能由同一个
     * "搜索失败"来代表。
     */
    private static void verifiesSurroundedNeedsBodiesNotWalls() {
        BearingField.Builder ringed = BearingField.builder();
        for (int sector = 0; sector < BearingField.SECTORS; sector++) {
            ringed.room(BearingField.bearingOf(sector), 10.0D);
            // 贴在身上的一圈：比她需要的空间还近，哪个方向都走不出去。
            ringed.threat(BearingField.bearingOf(sector), 1.5D);
        }
        require(
                ringed.build().surrounded(NEEDED),
                "四面都是敌人却不算被围"
        );

        // 死胡同：只有一条出路，但那条出路上没有人。
        BearingField corner = BearingField.builder()
                .room(BearingField.bearingOf(0), 10.0D)
                .threat(BearingField.bearingOf(6), 2.0D)
                .build();
        require(
                !corner.surrounded(NEEDED),
                "还有一条空着的出路时她就认为自己被围了"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
