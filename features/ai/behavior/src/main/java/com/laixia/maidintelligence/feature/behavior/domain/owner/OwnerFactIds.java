package com.laixia.maidintelligence.feature.behavior.domain.owner;

import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a maid can notice about her owner.
 *
 * <p>Kept apart from the general companion vocabulary because this is one
 * coherent subject — everything here describes the same person, is read from
 * one place, and is normalised on one scale.
 *
 * <p>Every fraction here is on <code>[0,1]</code> and every flag is 0 or 1, so
 * a datapack can weigh any of them against any other without having to know
 * which is measured in hearts, ticks, or blocks. Raw counts are the exception
 * and say so in their name.
 */
public final class OwnerFactIds {
    private static final String NAMESPACE = "tlm_companionship";

    // What he is holding, which is most of what says what he is doing.

    /** Either hand holds something edible. */
    public static final OrchestrationId HOLDING_FOOD =
            id("fact/owner_holding_food");

    /** How nourishing the held food is, worst to best. */
    public static final OrchestrationId HELD_FOOD_QUALITY =
            id("fact/owner_held_food_quality");

    public static final OrchestrationId HOLDING_WEAPON =
            id("fact/owner_holding_weapon");

    public static final OrchestrationId HOLDING_TOOL =
            id("fact/owner_holding_tool");

    /** A placeable block: he is building. */
    public static final OrchestrationId HOLDING_BLOCK =
            id("fact/owner_holding_block");

    public static final OrchestrationId HANDS_EMPTY =
            id("fact/owner_hands_empty");

    /** Armour points worn, as a fraction of a full set. */
    public static final OrchestrationId ARMOR_FRACTION =
            id("fact/owner_armor_fraction");

    /** Mid-use: eating, drawing a bow, raising a shield. */
    public static final OrchestrationId USING_ITEM =
            id("fact/owner_using_item");

    // How he is doing.

    public static final OrchestrationId HEALTH_FRACTION =
            id("fact/owner_health_fraction");

    /** His own hunger bar, not the maid's. */
    public static final OrchestrationId FOOD_FRACTION =
            id("fact/owner_food_fraction");

    /** Breath remaining; below one, he is drowning. */
    public static final OrchestrationId AIR_FRACTION =
            id("fact/owner_air_fraction");

    public static final OrchestrationId ON_FIRE =
            id("fact/owner_on_fire");

    public static final OrchestrationId IN_WATER =
            id("fact/owner_in_water");

    /** Fades over the moments after he is struck, rather than a bare flag. */
    public static final OrchestrationId HURT_RECENTLY =
            id("fact/owner_hurt_recently");

    // How he is carrying himself.

    public static final OrchestrationId SNEAKING =
            id("fact/owner_sneaking");

    public static final OrchestrationId SPRINTING =
            id("fact/owner_sprinting");

    public static final OrchestrationId RIDING =
            id("fact/owner_riding");

    public static final OrchestrationId SLEEPING =
            id("fact/owner_sleeping");

    /** Elytra or creative flight. */
    public static final OrchestrationId FLYING =
            id("fact/owner_flying");

    /** Ground speed against a sprint, so setting off reads as a rise here. */
    public static final OrchestrationId SPEED =
            id("fact/owner_speed");

    // What is acting on him. Counts, not fractions.

    public static final OrchestrationId HARMFUL_EFFECT_COUNT =
            id("fact/owner_harmful_effect_count");

    public static final OrchestrationId BENEFICIAL_EFFECT_COUNT =
            id("fact/owner_beneficial_effect_count");

    // What he is carrying.

    /** How much of his pack is food. */
    public static final OrchestrationId INVENTORY_FOOD =
            id("fact/owner_inventory_food");

    public static final OrchestrationId INVENTORY_FULLNESS =
            id("fact/owner_inventory_fullness");

    /** Raw level, not a fraction: there is no maximum to divide by. */
    public static final OrchestrationId EXPERIENCE_LEVEL =
            id("fact/owner_experience_level");

    private OwnerFactIds() {
    }

    /**
     * Every owner fact with its type, for merging into the intent vocabulary.
     */
    public static Map<OrchestrationId, FactType> facts() {
        Map<OrchestrationId, FactType> facts = new LinkedHashMap<>();
        for (OrchestrationId flag : new OrchestrationId[]{
                HOLDING_FOOD,
                HOLDING_WEAPON,
                HOLDING_TOOL,
                HOLDING_BLOCK,
                HANDS_EMPTY,
                USING_ITEM,
                ON_FIRE,
                IN_WATER,
                SNEAKING,
                SPRINTING,
                RIDING,
                SLEEPING,
                FLYING
        }) {
            facts.put(flag, FactType.BOOLEAN);
        }
        for (OrchestrationId number : new OrchestrationId[]{
                HELD_FOOD_QUALITY,
                ARMOR_FRACTION,
                HEALTH_FRACTION,
                FOOD_FRACTION,
                AIR_FRACTION,
                HURT_RECENTLY,
                SPEED,
                HARMFUL_EFFECT_COUNT,
                BENEFICIAL_EFFECT_COUNT,
                INVENTORY_FOOD,
                INVENTORY_FULLNESS,
                EXPERIENCE_LEVEL
        }) {
            facts.put(number, FactType.NUMBER);
        }
        return Map.copyOf(facts);
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId(NAMESPACE, path);
    }
}
