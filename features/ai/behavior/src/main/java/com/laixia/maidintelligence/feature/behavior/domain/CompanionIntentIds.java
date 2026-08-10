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
    /** Blocks from the centre of her home, or NaN when she has none. */
    public static final OrchestrationId HOME_DISTANCE =
            id("fact/home_distance");
    /** Something edible is lying within reach and free to take. */
    public static final OrchestrationId LOOSE_FOOD_AVAILABLE =
            id("fact/loose_food_available");
    public static final OrchestrationId SNACK_CABINET_MEAL_AVAILABLE =
            id("fact/snack_cabinet_meal_available");
    /**
     * She is carrying something worth eating.
     *
     * <p>Distinct from the two above, which are about food out in the world.
     * This one is about the pack she already has on her, and it is the only
     * one of the three that costs her no walking at all.
     */
    public static final OrchestrationId PACK_MEAL_AVAILABLE =
            id("fact/pack_meal_available");
    /**
     * How many hostiles can reach her where she stands.
     *
     * <p>A count rather than a flag, and of what converges rather than of what
     * exists: one hostile across the room and five around her feet are
     * different situations, and only this number tells them apart.
     */
    public static final OrchestrationId HOSTILE_PRESSURE =
            id("fact/hostile_pressure");
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
    public static final OrchestrationId BEHAVIOR_OCCUPANCY_LEVEL =
            id("fact/behavior_occupancy_level");

    public static final OrchestrationId GAZE_RECALL =
            id("signal/gaze_recall");
    public static final OrchestrationId HUNGER_REQUEST =
            id("signal/hunger_request");

    public static final OrchestrationId APPROACH_OWNER =
            id("action/approach_owner");
    public static final OrchestrationId FETCH_SNACK_CABINET_MEAL =
            id("action/fetch_snack_cabinet_meal");
    /** Keep up with her owner: an anchor that moves. */
    public static final OrchestrationId FOLLOW_OWNER_ANCHOR =
            id("action/follow_owner");
    /** Go back to where she has been told she belongs. */
    public static final OrchestrationId RETURN_HOME_ANCHOR =
            id("action/return_home");
    /** Take a free seat: one chair holds one person. */
    public static final OrchestrationId REST_ON_SEAT =
            id("action/rest_on_seat");
    /** Settle in at a bookshelf, a chessboard or a computer. */
    public static final OrchestrationId USE_JOY_BLOCK =
            id("action/use_joy_block");
    /** Drift over and be near her owner for its own sake. */
    public static final OrchestrationId KEEP_COMPANY =
            id("action/keep_company");
    /** Walk to a dropped item and take it, rather than opening anything. */
    public static final OrchestrationId PICK_UP_LOOSE_FOOD =
            id("action/pick_up_loose_food");
    public static final OrchestrationId COMPANION_COMMAND_WINDOW =
            id("action/companion_command_window");
    public static final OrchestrationId REQUEST_HUNGER_ATTENTION =
            id("action/request_hunger_attention");
    /** Eat something she is already carrying, standing where she is. */
    public static final OrchestrationId EAT_FROM_PACK =
            id("action/eat_from_pack");
    public static final OrchestrationId DEPLOY_BOAT =
            id("action/deploy_boat");
    /** Fight what is threatening her or her owner, choosing her own weapon. */
    public static final OrchestrationId ENGAGE_THREAT =
            id("action/engage_threat");

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
                HUNGER_REQUEST
        );
        Set<OrchestrationId> facts = new LinkedHashSet<>(Set.of(
                OWNER_VALID,
                OWNER_DISTANCE,
                FAVORABILITY,
                HUNGER,
                SNACK_CABINET_MEAL_AVAILABLE,
                LOOSE_FOOD_AVAILABLE,
                PACK_MEAL_AVAILABLE,
                HOME_DISTANCE,
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
                HOSTILE_PRESSURE,
                ATTACK_TARGET_PRESENT,
                PANIC_ACTIVE,
                WORK_TARGET_PRESENT,
                USING_ITEM,
                BEHAVIOR_OCCUPANCY_LEVEL,
                GAZE_RECALL,
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
                        EAT_FROM_PACK,
                        FOLLOW_OWNER_ANCHOR,
                        RETURN_HOME_ANCHOR,
                        REST_ON_SEAT,
                        USE_JOY_BLOCK,
                        KEEP_COMPANY,
                        COMPANION_COMMAND_WINDOW,
                        REQUEST_HUNGER_ATTENTION,
                        DEPLOY_BOAT,
                        ENGAGE_THREAT
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
                        Map.entry(
                                PACK_MEAL_AVAILABLE,
                                FactType.BOOLEAN
                        ),
                        Map.entry(HOME_DISTANCE, FactType.NUMBER),
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
                        Map.entry(
                                BEHAVIOR_OCCUPANCY_LEVEL,
                                FactType.NUMBER
                        ),
                        Map.entry(
                                HOSTILE_PRESSURE,
                                FactType.NUMBER
                        ),
                        Map.entry(GAZE_RECALL, FactType.SIGNAL),
                        Map.entry(
                                HUNGER_REQUEST,
                                FactType.SIGNAL
                        )
                )),
                Map.ofEntries(
                        Map.entry(
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
                                )
                        ),
                        Map.entry(
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
                                )
                        ),
                        Map.entry(
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
                                )
                        ),
                        Map.entry(
                                FOLLOW_OWNER_ANCHOR,
                                new ActionSchema(
                                        FOLLOW_OWNER_ANCHOR,
                                        Map.of(
                                                "speed",
                                                ActionParameterType.NUMBER,
                                                "close_distance",
                                                ActionParameterType.INTEGER
                                        ),
                                        Set.of()
                                )
                        ),
                        Map.entry(
                                RETURN_HOME_ANCHOR,
                                new ActionSchema(
                                        RETURN_HOME_ANCHOR,
                                        Map.of(
                                                "speed",
                                                ActionParameterType.NUMBER,
                                                "close_distance",
                                                ActionParameterType.INTEGER
                                        ),
                                        Set.of()
                                )
                        ),
                        Map.entry(
                                REST_ON_SEAT,
                                new ActionSchema(
                                        REST_ON_SEAT,
                                        Map.of(
                                                "speed",
                                                ActionParameterType.NUMBER,
                                                "close_distance",
                                                ActionParameterType.INTEGER
                                        ),
                                        Set.of()
                                )
                        ),
                        Map.entry(
                                USE_JOY_BLOCK,
                                new ActionSchema(
                                        USE_JOY_BLOCK,
                                        Map.of(
                                                "speed",
                                                ActionParameterType.NUMBER,
                                                "close_distance",
                                                ActionParameterType.INTEGER
                                        ),
                                        Set.of()
                                )
                        ),
                        Map.entry(
                                KEEP_COMPANY,
                                new ActionSchema(
                                        KEEP_COMPANY,
                                        Map.of(
                                                "speed",
                                                ActionParameterType.NUMBER,
                                                "close_distance",
                                                ActionParameterType.INTEGER
                                        ),
                                        Set.of()
                                )
                        ),
                        Map.entry(
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
                                )
                        ),
                        Map.entry(
                                REQUEST_HUNGER_ATTENTION,
                                ActionSchema.withoutParameters(
                                        REQUEST_HUNGER_ATTENTION
                                )
                        ),
                        // Nothing to configure: she eats where she stands, and
                        // which mouthful is a decision rather than a parameter.
                        Map.entry(
                                EAT_FROM_PACK,
                                ActionSchema.withoutParameters(EAT_FROM_PACK)
                        ),
                        Map.entry(
                                DEPLOY_BOAT,
                                new ActionSchema(
                                        DEPLOY_BOAT,
                                        Map.of(
                                                "ability_id",
                                                ActionParameterType.STRING
                                        ),
                                        Set.of("ability_id")
                                )
                        ),
                        Map.entry(
                                ENGAGE_THREAT,
                                ActionSchema.withoutParameters(ENGAGE_THREAT)
                        )
                )
        );
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId(NAMESPACE, path);
    }
}
