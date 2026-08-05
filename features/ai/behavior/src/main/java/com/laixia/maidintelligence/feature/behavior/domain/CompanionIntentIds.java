package com.laixia.maidintelligence.feature.behavior.domain;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.action.ActionParameterType;
import com.laixia.maidintelligence.feature.orchestration.domain.action.ActionSchema;
import com.laixia.maidintelligence.feature.behavior.domain.forecast.CompanionActivity;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFactIds;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stable fact, signal, and action vocabulary for built-in companion intents.
 */
public final class CompanionIntentIds {
    private static final String NAMESPACE = "tlm_companionship";

    public static final OrchestrationId OWNER_VALID = id("fact/owner_valid");
    public static final OrchestrationId OWNER_DISTANCE =
            id("fact/owner_distance");
    public static final OrchestrationId FAVORABILITY = id("fact/favorability");
    public static final OrchestrationId HUNGER = id("fact/hunger");
    /** Something edible is lying within reach and free to take. */
    public static final OrchestrationId LOOSE_FOOD_AVAILABLE =
            id("fact/loose_food_available");
    public static final OrchestrationId SNACK_CABINET_MEAL_AVAILABLE =
            id("fact/snack_cabinet_meal_available");
    public static final OrchestrationId FOLLOW_MODE = id("fact/follow_mode");
    public static final OrchestrationId HOME_MODE = id("fact/home_mode");
    public static final OrchestrationId ORDERED_SIT = id("fact/ordered_sit");
    public static final OrchestrationId SITTING_POSE =
            id("fact/sitting_pose");
    public static final OrchestrationId SLEEPING = id("fact/sleeping");
    public static final OrchestrationId LEASHED = id("fact/leashed");
    public static final OrchestrationId PASSENGER = id("fact/passenger");
    public static final OrchestrationId PASSIVE_SEAT =
            id("fact/passive_seat");
    public static final OrchestrationId CAN_MOVE = id("fact/can_move");
    public static final OrchestrationId COMBAT_ACTIVE =
            id("fact/combat_active");
    public static final OrchestrationId ATTACK_TARGET_PRESENT =
            id("fact/attack_target_present");
    public static final OrchestrationId PANIC_ACTIVE =
            id("fact/panic_active");
    public static final OrchestrationId WORK_TARGET_PRESENT =
            id("fact/work_target_present");
    public static final OrchestrationId USING_ITEM =
            id("fact/using_item");
    public static final OrchestrationId BUILT_IN_TASK =
            id("fact/built_in_task");
    public static final OrchestrationId MOVEMENT_HARD_BLOCKED =
            id("fact/movement_hard_blocked");
    public static final OrchestrationId WORK_RELEASE_AGE =
            id("fact/work_release_age");
    public static final OrchestrationId MOVEMENT_LEASE_ACTIVE =
            id("fact/movement_lease_active");
    public static final OrchestrationId MOVEMENT_LEASE_PRIORITY =
            id("fact/movement_lease_priority");
    public static final OrchestrationId MOVEMENT_FAIL_OPEN =
            id("fact/movement_fail_open");
    public static final OrchestrationId BEHAVIOR_OCCUPANCY_LEVEL =
            id("fact/behavior_occupancy_level");
    public static final OrchestrationId BEHAVIOR_OCCUPANCY_REASON =
            id("fact/behavior_occupancy_reason");

    public static final OrchestrationId GAZE_RECALL =
            id("signal/gaze_recall");
    public static final OrchestrationId POST_TASK_RETURN =
            id("signal/post_task_return");
    public static final OrchestrationId RANDOM_STROLL_RETURN =
            id("signal/random_stroll_return");
    public static final OrchestrationId HUNGER_REQUEST =
            id("signal/hunger_request");

    public static final OrchestrationId APPROACH_OWNER =
            id("action/approach_owner");
    public static final OrchestrationId FETCH_SNACK_CABINET_MEAL =
            id("action/fetch_snack_cabinet_meal");
    /** Walk to a dropped item and take it, rather than opening anything. */
    public static final OrchestrationId PICK_UP_LOOSE_FOOD =
            id("action/pick_up_loose_food");
    public static final OrchestrationId COMPANION_COMMAND_WINDOW =
            id("action/companion_command_window");
    public static final OrchestrationId REQUEST_HUNGER_ATTENTION =
            id("action/request_hunger_attention");
    public static final OrchestrationId DEPLOY_BOAT =
            id("action/deploy_boat");

    private CompanionIntentIds() {
    }

    /**
     * Adds one forecast fact per {@link CompanionActivity}, derived from the
     * enum rather than listed a second time — a new activity would otherwise
     * arrive with a fact that data packs cannot reference.
     *
     * <p>They are numbers, not booleans: each one is the probability that the
     * owner does that thing next, so a consideration normalizes it over
     * {@code [0, 1]} directly.
     */
    private static Map<OrchestrationId, FactType> withForecastFacts(
            Map<OrchestrationId, FactType> base
    ) {
        Map<OrchestrationId, FactType> merged = new LinkedHashMap<>(base);
        for (CompanionActivity activity : CompanionActivity.values()) {
            merged.put(activity.forecastFact(), FactType.NUMBER);
            merged.put(activity.forecastLiftFact(), FactType.NUMBER);
        }
        // Everything a maid notices about her owner is catalogued with itself,
        // so this file does not grow a second subject.
        merged.putAll(OwnerFactIds.facts());
        return Map.copyOf(merged);
    }

    public static IntentVocabulary vocabulary() {
        Set<OrchestrationId> signals = Set.of(
                GAZE_RECALL,
                POST_TASK_RETURN,
                RANDOM_STROLL_RETURN,
                HUNGER_REQUEST
        );
        Set<OrchestrationId> facts = new LinkedHashSet<>(Set.of(
                OWNER_VALID,
                OWNER_DISTANCE,
                FAVORABILITY,
                HUNGER,
                SNACK_CABINET_MEAL_AVAILABLE,
                LOOSE_FOOD_AVAILABLE,
                FOLLOW_MODE,
                HOME_MODE,
                ORDERED_SIT,
                SITTING_POSE,
                SLEEPING,
                LEASHED,
                PASSENGER,
                PASSIVE_SEAT,
                CAN_MOVE,
                COMBAT_ACTIVE,
                ATTACK_TARGET_PRESENT,
                PANIC_ACTIVE,
                WORK_TARGET_PRESENT,
                USING_ITEM,
                BUILT_IN_TASK,
                MOVEMENT_HARD_BLOCKED,
                WORK_RELEASE_AGE,
                MOVEMENT_LEASE_ACTIVE,
                MOVEMENT_LEASE_PRIORITY,
                MOVEMENT_FAIL_OPEN,
                BEHAVIOR_OCCUPANCY_LEVEL,
                BEHAVIOR_OCCUPANCY_REASON,
                GAZE_RECALL,
                POST_TASK_RETURN,
                RANDOM_STROLL_RETURN,
                HUNGER_REQUEST
        ));
        // Registered from the enum rather than listed again, so a new activity
        // cannot arrive with its fact left unusable by data packs.
        for (CompanionActivity activity : CompanionActivity.values()) {
            facts.add(activity.forecastFact());
            facts.add(activity.forecastLiftFact());
        }
        // Same reason, one subject over: an owner fact that is typed but not
        // declared would be a fact no data pack is allowed to name.
        facts.addAll(OwnerFactIds.facts().keySet());
        return new IntentVocabulary(
                facts,
                Set.of(
                        APPROACH_OWNER,
                        FETCH_SNACK_CABINET_MEAL,
                        PICK_UP_LOOSE_FOOD,
                        COMPANION_COMMAND_WINDOW,
                        REQUEST_HUNGER_ATTENTION,
                        DEPLOY_BOAT
                ),
                signals,
                withForecastFacts(Map.ofEntries(
                        Map.entry(OWNER_VALID, FactType.BOOLEAN),
                        Map.entry(OWNER_DISTANCE, FactType.NUMBER),
                        Map.entry(FAVORABILITY, FactType.NUMBER),
                        Map.entry(HUNGER, FactType.NUMBER),
                        Map.entry(
                                SNACK_CABINET_MEAL_AVAILABLE,
                                FactType.BOOLEAN
                        ),
                        Map.entry(
                                LOOSE_FOOD_AVAILABLE,
                                FactType.BOOLEAN
                        ),
                        Map.entry(FOLLOW_MODE, FactType.BOOLEAN),
                        Map.entry(HOME_MODE, FactType.BOOLEAN),
                        Map.entry(ORDERED_SIT, FactType.BOOLEAN),
                        Map.entry(SITTING_POSE, FactType.BOOLEAN),
                        Map.entry(SLEEPING, FactType.BOOLEAN),
                        Map.entry(LEASHED, FactType.BOOLEAN),
                        Map.entry(PASSENGER, FactType.BOOLEAN),
                        Map.entry(PASSIVE_SEAT, FactType.BOOLEAN),
                        Map.entry(CAN_MOVE, FactType.BOOLEAN),
                        Map.entry(COMBAT_ACTIVE, FactType.BOOLEAN),
                        Map.entry(
                                ATTACK_TARGET_PRESENT,
                                FactType.BOOLEAN
                        ),
                        Map.entry(PANIC_ACTIVE, FactType.BOOLEAN),
                        Map.entry(
                                WORK_TARGET_PRESENT,
                                FactType.BOOLEAN
                        ),
                        Map.entry(USING_ITEM, FactType.BOOLEAN),
                        Map.entry(BUILT_IN_TASK, FactType.BOOLEAN),
                        Map.entry(
                                MOVEMENT_HARD_BLOCKED,
                                FactType.BOOLEAN
                        ),
                        Map.entry(WORK_RELEASE_AGE, FactType.NUMBER),
                        Map.entry(
                                MOVEMENT_LEASE_ACTIVE,
                                FactType.BOOLEAN
                        ),
                        Map.entry(
                                MOVEMENT_LEASE_PRIORITY,
                                FactType.NUMBER
                        ),
                        Map.entry(
                                MOVEMENT_FAIL_OPEN,
                                FactType.BOOLEAN
                        ),
                        Map.entry(
                                BEHAVIOR_OCCUPANCY_LEVEL,
                                FactType.NUMBER
                        ),
                        Map.entry(
                                BEHAVIOR_OCCUPANCY_REASON,
                                FactType.NUMBER
                        ),
                        Map.entry(GAZE_RECALL, FactType.SIGNAL),
                        Map.entry(POST_TASK_RETURN, FactType.SIGNAL),
                        Map.entry(
                                RANDOM_STROLL_RETURN,
                                FactType.SIGNAL
                        ),
                        Map.entry(
                                HUNGER_REQUEST,
                                FactType.SIGNAL
                        )
                )),
                Map.of(
                        APPROACH_OWNER,
                        new ActionSchema(
                                APPROACH_OWNER,
                                Map.of(
                                        "speed",
                                        ActionParameterType.NUMBER,
                                        "close_distance",
                                        ActionParameterType.INTEGER,
                                        "authority",
                                        ActionParameterType.STRING
                                ),
                                Set.of()
                        ),
                        FETCH_SNACK_CABINET_MEAL,
                        new ActionSchema(
                                FETCH_SNACK_CABINET_MEAL,
                                Map.of(
                                        "speed",
                                        ActionParameterType.NUMBER,
                                        "close_distance",
                                        ActionParameterType.INTEGER
                                ),
                                Set.of()
                        ),
                        PICK_UP_LOOSE_FOOD,
                        new ActionSchema(
                                PICK_UP_LOOSE_FOOD,
                                Map.of(
                                        "speed",
                                        ActionParameterType.NUMBER,
                                        "close_distance",
                                        ActionParameterType.INTEGER
                                ),
                                Set.of()
                        ),
                        COMPANION_COMMAND_WINDOW,
                        new ActionSchema(
                                COMPANION_COMMAND_WINDOW,
                                Map.of(
                                        "duration_ticks",
                                        ActionParameterType.INTEGER,
                                        "speed",
                                        ActionParameterType.NUMBER,
                                        "close_distance",
                                        ActionParameterType.INTEGER
                                ),
                                Set.of(
                                        "duration_ticks",
                                        "speed",
                                        "close_distance"
                                )
                        ),
                        REQUEST_HUNGER_ATTENTION,
                        ActionSchema.withoutParameters(
                                REQUEST_HUNGER_ATTENTION
                        ),
                        DEPLOY_BOAT,
                        new ActionSchema(
                                DEPLOY_BOAT,
                                Map.of(
                                        "ability_id",
                                        ActionParameterType.STRING
                                ),
                                Set.of("ability_id")
                        )
                )
        );
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId(NAMESPACE, path);
    }
}
