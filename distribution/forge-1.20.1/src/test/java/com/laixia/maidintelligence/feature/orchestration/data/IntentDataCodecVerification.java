package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.behavior.application.ability.AbilityTemplateCompiler;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.data.AbilityDefinitionCodec;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityTemplate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompiledAbilityTemplate;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;

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
        abilityReloadIsAtomicAndFallbackSafe();
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
        /*
         * Discovered rather than listed, for the same reason the intents below
         * are: a plan added to the data pack but forgotten here failed as
         * "references unknown plan", which reads like a broken intent rather
         * than an out-of-date test.
         */
        Map<OrchestrationId, PlanDefinition> plans =
                new LinkedHashMap<>();
        for (String name : bundledNames(MaidIntentReloadListener.PLAN_PREFIX)) {
            plans.put(id(name), parsePlan(name));
        }

        Map<OrchestrationId, IntentDefinition> intents =
                new LinkedHashMap<>();
        /*
         * Scanned rather than listed. A hardcoded list means a new intent file
         * ships untested by the one suite that compiles the built-in data as a
         * whole, and nothing about adding the file would reveal that.
         */
        List<String> intentNames = bundledNames(
                MaidIntentReloadListener.INTENT_PREFIX
        );
        require(intentNames.size() >= 7,
                "Only found " + intentNames.size()
                        + " bundled intents; the scan is not seeing the "
                        + "resource directory");
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
                                CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                                FactComparison.EQUAL,
                                0.0D
                        ),
                        "Hunger intent lacks its idle-occupancy guard: " + name
                );
            }
        }
        require(
                hasCondition(
                        intents.get(id("gaze_recall")),
                        CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                        FactComparison.LESS_OR_EQUAL,
                        1.0D
                ),
                "Gaze recall must allow soft occupancy preemption"
        );
        for (String name : List.of(
                "post_task_return",
                "wander_return",
                "snack_cabinet_meal"
        )) {
            require(
                    hasCondition(
                            intents.get(id(name)),
                            CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                            FactComparison.EQUAL,
                            0.0D
                    ),
                    "Passive intent lacks its idle-occupancy guard: " + name
            );
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
        require(
                plans.get(id("fetch_snack_cabinet_meal"))
                        .resumePolicy() == ResumePolicy.RESTART_STEP,
                "Snack cabinet plan is not recoverable after interruption"
        );
        IntentCatalog catalog = IntentCatalog.compile(
                1L,
                intents.values(),
                plans.values(),
                CompanionIntentIds.vocabulary()
        );
        /*
         * Compared against what was scanned rather than a fixed number. A
         * count that has to be edited alongside every new file is a count that
         * gets edited to whatever makes the build pass; comparing against the
         * directory also catches two files that collapse onto one id.
         */
        require(catalog.intents().size() == intentNames.size(),
                "Catalog holds " + catalog.intents().size()
                        + " intents but " + intentNames.size()
                        + " files were bundled");
        require(catalog.planCount() == plans.size(),
                "Catalog holds " + catalog.planCount()
                        + " plans but " + plans.size() + " were loaded");
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

    private static void abilityReloadIsAtomicAndFallbackSafe()
            throws IOException {
        AbilityDefinition valid = AbilityDefinitionCodec.parse(
                CompanionAbilityIds.DEPLOY_BOAT,
                resource(MaidIntentReloadListener.ABILITY_PREFIX
                        + "/deploy_boat.json")
        ).result().orElseThrow(() ->
                new AssertionError("Failed to parse deploy_boat ability"));
        AbilityTemplateCompiler compiler = new AbilityTemplateCompiler();
        MutableIntentCatalog intents = new MutableIntentCatalog();
        intents.publish(IntentCatalog.compile(
                1L,
                List.of(),
                List.of(),
                CompanionIntentIds.vocabulary()
        ));
        MutableAbilityCatalog abilities = new MutableAbilityCatalog();
        abilities.publish(AbilityCatalog.compile(
                1L,
                List.of(valid)
        ));
        MaidIntentReloadListener listener =
                new MaidIntentReloadListener(
                        intents,
                        CompanionIntentIds.vocabulary(),
                        abilities,
                        compiler,
                        () -> {
                        }
                );
        AbilityDefinition invalid = new AbilityDefinition(
                id("invalid_ability"),
                AbilityTemplate.WORLD_ITEM_DEPLOY,
                id("unknown_action"),
                Map.of(),
                20,
                20,
                20,
                10.0D,
                5.0D,
                10
        );
        applyAbility(listener, compiler, invalid);
        require(intents.current().generation() == 1L
                        && abilities.current().generation() == 1L,
                "Invalid ability replaced a last-good catalog");

        applyAbility(listener, compiler, valid);
        require(intents.current().generation() == 2L
                        && abilities.current().generation() == 2L,
                "Valid ability and generated intents were not atomic");
        require(intents.current().intents().size() == 2
                        && intents.current().planCount() == 1,
                "Ability template did not publish both activation intents");
    }

    private static void applyAbility(
            MaidIntentReloadListener listener,
            AbilityTemplateCompiler compiler,
            AbilityDefinition definition
    ) {
        CompiledAbilityTemplate compiled = compiler.compile(definition);
        Map<OrchestrationId, IntentDefinition> generated =
                new LinkedHashMap<>();
        compiled.intents().forEach(intent ->
                generated.put(intent.id(), intent));
        listener.apply(
                new MaidIntentReloadListener.Prepared(
                        generated,
                        Map.of(compiled.plan().id(), compiled.plan()),
                        Map.of(definition.id(), definition),
                        compiler.extendVocabulary(
                                CompanionIntentIds.vocabulary(),
                                List.of(compiled)
                        ),
                        null
                ),
                null,
                null
        );
    }

    /**
     * Definition names bundled under {@code prefix}, read from the resource
     * tree on disk because a classpath directory cannot be listed portably.
     */
    private static List<String> bundledNames(String prefix)
            throws IOException {
        java.nio.file.Path directory = java.nio.file.Path.of(
                "src", "main", "resources"
        ).resolve(RESOURCE_ROOT + prefix);
        if (!java.nio.file.Files.isDirectory(directory)) {
            throw new IOException("Missing resource directory " + directory);
        }
        try (java.util.stream.Stream<java.nio.file.Path> entries =
                     java.nio.file.Files.list(directory)) {
            return entries
                    .map(entry -> entry.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted()
                    .toList();
        }
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
