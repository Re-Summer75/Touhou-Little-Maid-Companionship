package com.laixia.maidintelligence.feature.behavior.domain.perception;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

public final class CompanionAffordanceIds {
    public static final OrchestrationId TAKE_FOOD =
            id("affordance/take_food");
    /**
     * Food kept inside something that has to be opened, as against food lying
     * on the ground. Both relieve hunger, so both advertise
     * {@link #TAKE_FOOD} — but a query that can only walk to a block has to be
     * able to say so, or loose items would fill its results and be discarded.
     */
    /**
     * 地上那件能拿来打的东西。
     *
     * <p>与 {@link #TAKE_FOOD} 共用同一批掉落物广告：观察一次登记一次，靠再次
     * 观察续期，过期自然消失。同一个掉落物可以既是食物又是武器，也可以两者都
     * 不是——广告主登记它**是什么**，由查询方决定这一刻要的是哪一样。
     */
    public static final OrchestrationId TAKE_WEAPON =
            id("affordance/take_weapon");
    // 这里曾经有一条 take_drop：把"地上有东西可捡"也登记成广告。删掉了——
    // 广告板解决的是"每个人各扫一遍世界太贵"，而那对方块（柜子、椅子）成立，
    // 对掉落物不成立：它就是身边的一圈实体，原版也是直接扫的。送进广告板之后
    // 观察节流、广告过期、索引每 tick 的检查预算三样叠在一起，实测让她收了几件
    // 就对满地的东西视而不见。见 TlmAffordancePerceptionService.queryLooseDrops。

    public static final OrchestrationId OPEN_CONTAINER =
            id("affordance/open_container");
    public static final OrchestrationId OCCUPY_SEAT =
            id("affordance/occupy_seat");
    public static final OrchestrationId RIDE_VEHICLE =
            id("affordance/ride_vehicle");
    public static final OrchestrationId SOCIALIZE_WITH_OWNER =
            id("affordance/socialize_with_owner");

    public static final OrchestrationId HUNGER_RELIEF =
            id("commodity/hunger_relief");
    /** 这件东西作为武器值多少，{@code [0,1]}，与 WeaponCandidate 同一把尺。 */
    public static final OrchestrationId ARMAMENT =
            id("commodity/armament");
    // 配套 take_drop 的 salvage 也一并删了。清扫的排序仍然只按距离——那条道理
    // 没变（钻石和圆石都是一趟路，她要的是把地上清干净），只是现在由扫描直接
    // 按距离排，不再需要一个恒为一的商品来表达"不排价"。

    public static final OrchestrationId SEATING =
            id("commodity/seating");
    public static final OrchestrationId TRANSPORT =
            id("commodity/transport");
    public static final OrchestrationId COMPANIONSHIP =
            id("commodity/companionship");

    private CompanionAffordanceIds() {
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }
}
