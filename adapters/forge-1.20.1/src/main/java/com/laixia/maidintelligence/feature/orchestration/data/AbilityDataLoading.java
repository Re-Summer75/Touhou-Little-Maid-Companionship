package com.laixia.maidintelligence.feature.orchestration.data;

import com.laixia.maidintelligence.feature.behavior.data.AbilityDefinitionCodec;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompiledAbilityTemplate;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loading and merging for {@code maid_ai/abilities}.
 *
 * <p>Split out of {@link MaidIntentReloadListener} for room rather than for
 * design: that file sat one line under the size limit, and the layout gate
 * treats a file at its limit as debt to clear on the next substantial change
 * rather than as something to keep squeezing. The rules themselves are
 * unchanged and still run through the listener's {@code loadStack}.
 */
final class AbilityDataLoading {
    private AbilityDataLoading() {
    }

    static Map<OrchestrationId, AbilityDefinition> load(
            ResourceManager resourceManager,
            List<String> errors
    ) {
        Map<OrchestrationId, AbilityDefinition> loaded =
                new LinkedHashMap<>();
        resourceManager.listResourceStacks(
                MaidIntentReloadListener.ABILITY_PREFIX,
                MaidIntentReloadListener::jsonResource
        ).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> MaidIntentReloadListener.loadStack(
                        entry.getKey(),
                        entry.getValue(),
                        MaidIntentReloadListener.ABILITY_PREFIX,
                        loaded,
                        AbilityDefinitionCodec::parse,
                        ignored -> {
                        },
                        errors
                ));
        return loaded;
    }

    /**
     * A collision here means an ability generated a definition that shadows one
     * an author wrote, so it is rejected rather than resolved: whichever way it
     * were resolved, one of the two would silently stop being the thing that
     * runs.
     */
    static void mergePlans(
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

    static void mergeIntents(
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
}
