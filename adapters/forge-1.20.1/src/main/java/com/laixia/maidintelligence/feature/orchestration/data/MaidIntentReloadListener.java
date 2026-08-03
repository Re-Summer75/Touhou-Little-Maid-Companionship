package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.behavior.application.ability.AbilityTemplateCompiler;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.data.AbilityDefinitionCodec;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompiledAbilityTemplate;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MaidIntentReloadListener
        extends SimplePreparableReloadListener<
        MaidIntentReloadListener.Prepared> {
    public static final String INTENT_PREFIX = "maid_ai/intents";
    public static final String PLAN_PREFIX = "maid_ai/plans";
    public static final String ABILITY_PREFIX = "maid_ai/abilities";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MutableIntentCatalog catalog;
    private final IntentVocabulary vocabulary;
    private final Runnable publishedCallback;
    private final MutableAbilityCatalog abilityCatalog;
    private final AbilityTemplateCompiler abilityCompiler;

    public MaidIntentReloadListener(
            MutableIntentCatalog catalog,
            IntentVocabulary vocabulary
    ) {
        this(catalog, vocabulary, null, null, () -> {
        });
    }

    public MaidIntentReloadListener(
            MutableIntentCatalog catalog,
            IntentVocabulary vocabulary,
            Runnable publishedCallback
    ) {
        this(catalog, vocabulary, null, null, publishedCallback);
    }

    public MaidIntentReloadListener(
            MutableIntentCatalog catalog,
            IntentVocabulary vocabulary,
            MutableAbilityCatalog abilityCatalog,
            AbilityTemplateCompiler abilityCompiler,
            Runnable publishedCallback
    ) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.vocabulary = java.util.Objects.requireNonNull(
                vocabulary,
                "vocabulary"
        );
        this.abilityCatalog = abilityCatalog;
        this.abilityCompiler = abilityCompiler;
        if ((abilityCatalog == null) != (abilityCompiler == null)) {
            throw new IllegalArgumentException(
                    "Ability catalog and compiler must be configured together"
            );
        }
        this.publishedCallback = java.util.Objects.requireNonNull(
                publishedCallback,
                "publishedCallback"
        );
    }

    @Override
    protected Prepared prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        try {
            List<String> errors = new ArrayList<>();
            Map<OrchestrationId, AbilityDefinition> abilities =
                    abilityCatalog == null
                            ? Map.of()
                            : loadAbilityDefinitions(
                                    resourceManager,
                                    errors
                            );
            if (!errors.isEmpty()) {
                return Prepared.failed(String.join("; ", errors));
            }
            List<CompiledAbilityTemplate> templates =
                    abilityCompiler == null
                            ? List.of()
                            : abilityCompiler.compileAll(abilities.values());
            IntentVocabulary effectiveVocabulary =
                    abilityCompiler == null
                            ? vocabulary
                            : abilityCompiler.extendVocabulary(
                                    vocabulary,
                                    templates
                            );
            Map<OrchestrationId, PlanDefinition> plans =
                    loadPlanDefinitions(
                            resourceManager,
                            effectiveVocabulary
                    );
            mergeAbilityPlans(plans, templates);
            Map<OrchestrationId, IntentDefinition> intents =
                    loadIntentDefinitions(
                            resourceManager,
                            effectiveVocabulary,
                            plans
                    );
            mergeAbilityIntents(intents, templates);
            return new Prepared(
                    intents,
                    plans,
                    abilities,
                    effectiveVocabulary,
                    null
            );
        } catch (IllegalArgumentException exception) {
            return Prepared.failed(exception.getMessage());
        }
    }

    @Override
    protected void apply(
            Prepared prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        if (prepared.error() != null) {
            LOGGER.error(
                    "Rejected maid ability data reload; keeping catalog generation {}: {}",
                    catalog.current().generation(),
                    prepared.error()
            );
            return;
        }
        try {
            IntentVocabulary compileVocabulary =
                    prepared.vocabulary().facts().isEmpty()
                            && prepared.vocabulary().actions().isEmpty()
                            ? vocabulary
                            : prepared.vocabulary();
            IntentCatalog compiled = IntentCatalog.compile(
                    catalog.nextGeneration(),
                    prepared.intents().values(),
                    prepared.plans().values(),
                    compileVocabulary
            );
            AbilityCatalog compiledAbilities = abilityCatalog == null
                    ? null
                    : AbilityCatalog.compile(
                            compiled.generation(),
                            prepared.abilities().values()
                    );
            catalog.publish(compiled);
            if (compiledAbilities != null) {
                abilityCatalog.publish(compiledAbilities);
            }
            publishedCallback.run();
            LOGGER.info(
                    "Loaded {} maid intent(s), {} plan(s), and {} ability definition(s), generation {}",
                    prepared.intents().size(),
                    prepared.plans().size(),
                    prepared.abilities().size(),
                    compiled.generation()
            );
        } catch (IllegalArgumentException exception) {
            LOGGER.error(
                    "Rejected maid AI data reload; keeping catalog generation {}",
                    catalog.current().generation(),
                    exception
            );
        }
    }

    private static Map<OrchestrationId, IntentDefinition>
    loadIntentDefinitions(
            ResourceManager resourceManager,
            IntentVocabulary vocabulary,
            Map<OrchestrationId, PlanDefinition> plans
    ) {
        Map<OrchestrationId, IntentDefinition> loaded =
                new LinkedHashMap<>();
        resourceManager.listResourceStacks(
                INTENT_PREFIX,
                MaidIntentReloadListener::jsonResource
        ).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> loadStack(
                        entry.getKey(),
                        entry.getValue(),
                        INTENT_PREFIX,
                        loaded,
                        IntentDefinitionCodec::parse,
                        intent -> IntentCatalog.compile(
                                0L,
                                List.of(intent),
                                plans.values(),
                                vocabulary
                        )
                ));
        return loaded;
    }

    private static Map<OrchestrationId, AbilityDefinition>
    loadAbilityDefinitions(
            ResourceManager resourceManager,
            List<String> errors
    ) {
        Map<OrchestrationId, AbilityDefinition> loaded =
                new LinkedHashMap<>();
        resourceManager.listResourceStacks(
                ABILITY_PREFIX,
                MaidIntentReloadListener::jsonResource
        ).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> loadStack(
                        entry.getKey(),
                        entry.getValue(),
                        ABILITY_PREFIX,
                        loaded,
                        AbilityDefinitionCodec::parse,
                        ignored -> {
                        },
                        errors
                ));
        return loaded;
    }

    private static void mergeAbilityPlans(
            Map<OrchestrationId, PlanDefinition> plans,
            List<CompiledAbilityTemplate> templates
    ) {
        for (CompiledAbilityTemplate template : templates) {
            if (plans.putIfAbsent(
                    template.plan().id(),
                    template.plan()
            ) != null) {
                throw new IllegalArgumentException(
                        "Ability generated duplicate plan "
                                + template.plan().id()
                );
            }
        }
    }

    private static void mergeAbilityIntents(
            Map<OrchestrationId, IntentDefinition> intents,
            List<CompiledAbilityTemplate> templates
    ) {
        for (CompiledAbilityTemplate template : templates) {
            for (IntentDefinition intent : template.intents()) {
                if (intents.putIfAbsent(intent.id(), intent) != null) {
                    throw new IllegalArgumentException(
                            "Ability generated duplicate intent "
                                    + intent.id()
                    );
                }
            }
        }
    }

    private static Map<OrchestrationId, PlanDefinition>
    loadPlanDefinitions(
            ResourceManager resourceManager,
            IntentVocabulary vocabulary
    ) {
        Map<OrchestrationId, PlanDefinition> loaded =
                new LinkedHashMap<>();
        resourceManager.listResourceStacks(
                PLAN_PREFIX,
                MaidIntentReloadListener::jsonResource
        ).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> loadStack(
                        entry.getKey(),
                        entry.getValue(),
                        PLAN_PREFIX,
                        loaded,
                        PlanDefinitionCodec::parse,
                        plan -> IntentCatalog.compile(
                                0L,
                                List.of(),
                                List.of(plan),
                                vocabulary
                        )
                ));
        return loaded;
    }

    private static <T> void loadStack(
            ResourceLocation location,
            List<Resource> stack,
            String prefix,
            Map<OrchestrationId, T> output,
            DefinitionParser<T> parser,
            DefinitionValidator<T> validator
    ) {
        loadStack(
                location,
                stack,
                prefix,
                output,
                parser,
                validator,
                null
        );
    }

    private static <T> void loadStack(
            ResourceLocation location,
            List<Resource> stack,
            String prefix,
            Map<OrchestrationId, T> output,
            DefinitionParser<T> parser,
            DefinitionValidator<T> validator,
            List<String> errors
    ) {
        OrchestrationId id = definitionId(location, prefix);
        if (id == null) {
            LOGGER.warn("Ignoring invalid maid AI resource path {}", location);
            recordError(errors, "Invalid resource path " + location);
            return;
        }
        T selected = null;
        for (Resource resource : stack) {
            try (InputStreamReader reader = new InputStreamReader(
                    resource.open(),
                    StandardCharsets.UTF_8
            )) {
                JsonElement json = JsonParser.parseReader(reader);
                T parsed = parser.parse(id, json)
                        .resultOrPartial(error -> {
                            LOGGER.warn(
                                    "Invalid maid AI definition {} from {}: {}",
                                    location,
                                    resource.sourcePackId(),
                                    error
                            );
                            recordError(
                                    errors,
                                    location + ": " + error
                            );
                        })
                        .orElse(null);
                if (parsed != null) {
                    try {
                        validator.validate(parsed);
                        selected = parsed;
                    } catch (IllegalArgumentException exception) {
                        LOGGER.warn(
                                "Invalid maid AI definition {} from {}: {}",
                                location,
                                resource.sourcePackId(),
                                exception.getMessage()
                        );
                        recordError(
                                errors,
                                location + ": " + exception.getMessage()
                        );
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn(
                        "Failed to read maid AI definition {} from {}",
                        location,
                        resource.sourcePackId(),
                        exception
                );
                recordError(
                        errors,
                        location + ": " + exception.getMessage()
                );
            }
        }
        if (selected != null) {
            output.put(id, selected);
        }
    }

    private static void recordError(
            List<String> errors,
            String error
    ) {
        if (errors != null) {
            errors.add(error);
        }
    }

    private static OrchestrationId definitionId(
            ResourceLocation resource,
            String prefix
    ) {
        String expectedPrefix = prefix + "/";
        String path = resource.getPath();
        if (!path.startsWith(expectedPrefix) || !path.endsWith(".json")) {
            return null;
        }
        String relative = path.substring(
                expectedPrefix.length(),
                path.length() - ".json".length()
        );
        try {
            return new OrchestrationId(resource.getNamespace(), relative);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean jsonResource(ResourceLocation location) {
        return location.getPath().endsWith(".json");
    }

    public record Prepared(
            Map<OrchestrationId, IntentDefinition> intents,
            Map<OrchestrationId, PlanDefinition> plans,
            Map<OrchestrationId, AbilityDefinition> abilities,
            IntentVocabulary vocabulary,
            String error
    ) {
        public Prepared(
                Map<OrchestrationId, IntentDefinition> intents,
                Map<OrchestrationId, PlanDefinition> plans
        ) {
            this(
                    intents,
                    plans,
                    Map.of(),
                    IntentVocabulary.empty(),
                    null
            );
        }

        public Prepared {
            intents = Map.copyOf(intents);
            plans = Map.copyOf(plans);
            abilities = Map.copyOf(abilities);
            java.util.Objects.requireNonNull(vocabulary, "vocabulary");
        }

        private static Prepared failed(String error) {
            return new Prepared(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    IntentVocabulary.empty(),
                    error == null ? "Unknown ability compile failure" : error
            );
        }
    }

    @FunctionalInterface
    private interface DefinitionParser<T> {
        DataResult<T> parse(OrchestrationId id, JsonElement json);
    }

    @FunctionalInterface
    private interface DefinitionValidator<T> {
        void validate(T definition);
    }
}
