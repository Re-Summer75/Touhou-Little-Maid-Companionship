package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.List;
import java.util.Map;

public final class MaidIntentReloadListener
        extends SimplePreparableReloadListener<
        MaidIntentReloadListener.Prepared> {
    public static final String INTENT_PREFIX = "maid_ai/intents";
    public static final String PLAN_PREFIX = "maid_ai/plans";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MutableIntentCatalog catalog;
    private final IntentVocabulary vocabulary;

    public MaidIntentReloadListener(
            MutableIntentCatalog catalog,
            IntentVocabulary vocabulary
    ) {
        this.catalog = catalog;
        this.vocabulary = vocabulary;
    }

    @Override
    protected Prepared prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<OrchestrationId, PlanDefinition> plans =
                loadPlanDefinitions(resourceManager, vocabulary);
        Map<OrchestrationId, IntentDefinition> intents =
                loadIntentDefinitions(
                        resourceManager,
                        vocabulary,
                        plans
                );
        return new Prepared(intents, plans);
    }

    @Override
    protected void apply(
            Prepared prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        try {
            IntentCatalog compiled = IntentCatalog.compile(
                    catalog.nextGeneration(),
                    prepared.intents().values(),
                    prepared.plans().values(),
                    vocabulary
            );
            catalog.publish(compiled);
            LOGGER.info(
                    "Loaded {} maid intent(s) and {} maid plan(s), generation {}",
                    prepared.intents().size(),
                    prepared.plans().size(),
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
        OrchestrationId id = definitionId(location, prefix);
        if (id == null) {
            LOGGER.warn("Ignoring invalid maid AI resource path {}", location);
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
                        .resultOrPartial(error -> LOGGER.warn(
                                "Invalid maid AI definition {} from {}: {}",
                                location,
                                resource.sourcePackId(),
                                error
                        ))
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
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn(
                        "Failed to read maid AI definition {} from {}",
                        location,
                        resource.sourcePackId(),
                        exception
                );
            }
        }
        if (selected != null) {
            output.put(id, selected);
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
            Map<OrchestrationId, PlanDefinition> plans
    ) {
        public Prepared {
            intents = Map.copyOf(intents);
            plans = Map.copyOf(plans);
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
