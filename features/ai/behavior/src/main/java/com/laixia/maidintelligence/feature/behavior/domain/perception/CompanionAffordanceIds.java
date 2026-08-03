package com.laixia.maidintelligence.feature.behavior.domain.perception;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

public final class CompanionAffordanceIds {
    public static final OrchestrationId TAKE_FOOD =
            id("affordance/take_food");
    public static final OrchestrationId OCCUPY_SEAT =
            id("affordance/occupy_seat");
    public static final OrchestrationId RIDE_VEHICLE =
            id("affordance/ride_vehicle");
    public static final OrchestrationId SOCIALIZE_WITH_OWNER =
            id("affordance/socialize_with_owner");

    public static final OrchestrationId HUNGER_RELIEF =
            id("commodity/hunger_relief");
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
