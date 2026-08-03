package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntentDataCodecVerification {
    private static final String RESOURCE_ROOT =
            "data/tlm_companionship/";

    private IntentDataCodecVerification() {
    }

    public static void main(String[] args) throws IOException {
        formatVersionIsRequiredAndBounded();
        builtInDefinitionsCompileAsOneCatalog();
        invalidCompiledReloadKeepsPreviousGeneration();
    }

    private static void formatVersionIsRequiredAndBounded() {
        OrchestrationId id = id("codec");
        require(IntentDefinitionCodec.parse(
                id,
                JsonParser.parseString("""
                        {
                          "plan": "tlm_companionship:approach_owner"
                        }
                        """)
        ).error().isPresent(), "Missing intent format version was accepted");
        require(PlanDefinitionCodec.parse(
                id,
                JsonParser.parseString("""
                        {
                          "format_version": 2,
                          "initial_state": "start",
                          "states": {}
                        }
                        """)
        ).error().isPresent(), "Unknown plan format version was accepted");
    }

    private static void builtInDefinitionsCompileAsOneCatalog()
            throws IOException {
        Map<OrchestrationId, PlanDefinition> plans =
                new LinkedHashMap<>();
        plans.put(id("approach_owner"), parsePlan("approach_owner"));
        plans.put(
                id("gaze_recall_session"),
                parsePlan("gaze_recall_session")
        );
        plans.put(
                id("fetch_snack_cabinet_meal"),
                parsePlan("fetch_snack_cabinet_meal")
        );
        plans.put(id("request_food"), parsePlan("request_food"));

        Map<OrchestrationId, IntentDefinition> intents =
                new LinkedHashMap<>();
        List<String> intentNames = List.of(
                "gaze_recall",
                "hungry_feedback",
                "hungry_standard",
                "hungry_high_trust",
                "post_task_return",
                "snack_cabinet_meal",
                "wander_return"
        );
        for (String name : intentNames) {
            intents.put(id(name), parseIntent(name));
        }
        PlanDefinition requestFood = plans.get(id("request_food"));
        require(
                requestFood.states().values().stream().anyMatch(
                        state -> state.action().equals(
                                CompanionIntentIds.REQUEST_HUNGER_ATTENTION
                        )
                ),
                "Shared food request plan has no request action"
        );
        for (String name : intentNames) {
            if (name.startsWith("hungry_")) {
                IntentDefinition hungerIntent = intents.get(id(name));
                require(
                        hungerIntent.plan().equals(id("request_food")),
                        "Hunger intent bypassed the shared request plan: " + name
                );
                require(
                        hasCondition(
                                hungerIntent,
                                CompanionIntentIds.USING_ITEM,
                                FactComparison.EQUAL,
                                0.0D
                        ),
                        "Hunger intent lacks its item-use guard: " + name
                );
            }
        }
        require(
                hasCondition(
                        intents.get(id("hungry_feedback")),
                        CompanionIntentIds.HUNGER_REQUEST,
                        FactComparison.GREATER_OR_EQUAL,
                        1.0D
                ),
                "Status feedback hunger intent is not signal-driven"
        );
        IntentDefinition snackCabinetMeal =
                intents.get(id("snack_cabinet_meal"));
        require(
                snackCabinetMeal.plan().equals(
                        id("fetch_snack_cabinet_meal")
                ),
                "Snack cabinet intent bypassed its fetch plan"
        );
        require(
                hasCondition(
                        snackCabinetMeal,
                        CompanionIntentIds.SNACK_CABINET_MEAL_AVAILABLE,
                        FactComparison.EQUAL,
                        1.0D
                ),
                "Snack cabinet intent lacks its availability guard"
        );
        require(
                plans.get(id("fetch_snack_cabinet_meal"))
                        .states()
                        .values()
                        .stream()
                        .anyMatch(state -> state.action().equals(
                                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
                        )),
                "Snack cabinet fetch plan has no extraction action"
        );
        IntentCatalog catalog = IntentCatalog.compile(
                1L,
                intents.values(),
                plans.values(),
                CompanionIntentIds.vocabulary()
        );
        require(catalog.intents().size() == 7,
                "Built-in intent inventory is incomplete");
        require(catalog.planCount() == 4,
                "Built-in plan inventory is incomplete");
    }

    private static void invalidCompiledReloadKeepsPreviousGeneration() {
        IntentVocabulary vocabulary = CompanionIntentIds.vocabulary();
        PlanDefinition plan = new PlanDefinition(
                id("fallback_plan"),
                "start",
                Map.of("start", new PlanDefinition.State(
                        CompanionIntentIds.APPROACH_OWNER,
                        Map.of(),
                        20,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE
                ))
        );
        IntentDefinition valid = new IntentDefinition(
                id("fallback_intent"),
                plan.id(),
                List.of(),
                List.of(),
                1.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        MutableIntentCatalog repository = new MutableIntentCatalog();
        repository.publish(IntentCatalog.compile(
                1L,
                List.of(valid),
                List.of(plan),
                vocabulary
        ));
        MaidIntentReloadListener listener =
                new MaidIntentReloadListener(repository, vocabulary);
        IntentDefinition invalid = new IntentDefinition(
                id("invalid_reload"),
                plan.id(),
                List.of(new FactCondition(
                        id("unknown_fact"),
                        FactComparison.EQUAL,
                        1.0D
                )),
                List.of(),
                1.0D,
                0.0D,
                1.0D,
                1,
                0,
                0.0D,
                0,
                0
        );
        listener.apply(
                new MaidIntentReloadListener.Prepared(
                        Map.of(invalid.id(), invalid),
                        Map.of(plan.id(), plan)
                ),
                null,
                null
        );
        require(repository.current().generation() == 1L,
                "Invalid reload replaced the last-good catalog");

        listener.apply(
                new MaidIntentReloadListener.Prepared(
                        Map.of(valid.id(), valid),
                        Map.of(plan.id(), plan)
                ),
                null,
                null
        );
        require(repository.current().generation() == 2L,
                "Valid reload was not atomically published");
    }

    private static IntentDefinition parseIntent(String name)
            throws IOException {
        OrchestrationId id = id(name);
        return IntentDefinitionCodec.parse(
                id,
                resource(
                        MaidIntentReloadListener.INTENT_PREFIX
                                + "/" + name + ".json"
                )
        ).result().orElseThrow(() ->
                new AssertionError("Failed to parse intent " + name));
    }

    private static PlanDefinition parsePlan(String name)
            throws IOException {
        OrchestrationId id = id(name);
        return PlanDefinitionCodec.parse(
                id,
                resource(
                        MaidIntentReloadListener.PLAN_PREFIX
                                + "/" + name + ".json"
                )
        ).result().orElseThrow(() ->
                new AssertionError("Failed to parse plan " + name));
    }

    private static boolean hasCondition(
            IntentDefinition intent,
            OrchestrationId fact,
            FactComparison comparison,
            double expected
    ) {
        return intent.conditions().stream().anyMatch(condition ->
                condition.fact().equals(fact)
                        && condition.comparison() == comparison
                        && condition.expected() == expected
        );
    }

    private static JsonElement resource(String path) throws IOException {
        String fullPath = RESOURCE_ROOT + path;
        InputStream stream = IntentDataCodecVerification.class
                .getClassLoader()
                .getResourceAsStream(fullPath);
        if (stream == null) {
            throw new IOException("Missing classpath resource " + fullPath);
        }
        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader);
        }
    }

    private static OrchestrationId id(String path) {
        return new OrchestrationId("tlm_companionship", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
