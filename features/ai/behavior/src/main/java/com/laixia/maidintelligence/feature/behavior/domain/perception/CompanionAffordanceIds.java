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
