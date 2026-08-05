package com.laixia.maidintelligence.feature.orchestration.data;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskExpansion;
import com.laixia.maidintelligence.feature.orchestration.domain.task.TaskIntentCompiler;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code maid_ai/tasks} and folds task-shaped intents back into ordinary
 * intents and plans before anything is published.
 *
 * <p>Separate from {@link MaidIntentReloadListener} because that file was
 * already at the size limit; the loading rules it owns are unchanged and are
 * still reached through its {@code loadStack}, so a task file overrides and
 * falls back across resource packs exactly as an intent file does.
 */
final class TaskIntentExpansion {
    static final String TASK_PREFIX = "maid_ai/tasks";

    private TaskIntentExpansion() {
    }

    static Map<OrchestrationId, TaskDefinition> load(
            ResourceManager resourceManager,
            List<String> errors
    ) {
        Map<OrchestrationId, TaskDefinition> loaded = new LinkedHashMap<>();
        resourceManager.listResourceStacks(
                TASK_PREFIX,
                MaidIntentReloadListener::jsonResource
        ).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> MaidIntentReloadListener.loadStack(
                        entry.getKey(),
                        entry.getValue(),
                        TASK_PREFIX,
                        loaded,
                        TaskDefinitionCodec::parse,
                        ignored -> {
                        },
                        errors
                ));
        return loaded;
    }

    /**
     * Expands every task once, so a cycle or a combinatorial blow-up is
     * reported against the reload as a whole rather than only when some intent
     * happens to name the offending task.
     */
    static void requireExpandable(
            Map<OrchestrationId, TaskDefinition> tasks,
            List<String> errors
    ) {
        try {
            TaskExpansion.expandAll(tasks);
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
    }

    /**
     * Validates one intent, decomposing first when it names a task.
     *
     * <p>Called per file while loading, so a broken definition in an upper pack
     * falls back to the lower one instead of failing the whole reload — the
     * same contract every other definition type gets.
     */
    static void validate(
            IntentDefinition intent,
            Map<OrchestrationId, TaskDefinition> tasks,
            Map<OrchestrationId, PlanDefinition> plans,
            IntentVocabulary vocabulary
    ) {
        if (!tasks.containsKey(intent.plan())) {
            IntentCatalog.compile(
                    0L,
                    List.of(intent),
                    plans.values(),
                    vocabulary
            );
            return;
        }
        TaskIntentCompiler.Compiled compiled =
                TaskIntentCompiler.compile(intent, tasks);
        List<PlanDefinition> candidatePlans = new ArrayList<>(plans.values());
        candidatePlans.addAll(compiled.plans());
        IntentCatalog.compile(
                0L,
                compiled.intents(),
                candidatePlans,
                vocabulary
        );
    }

    /**
     * Replaces each task-shaped intent with its generated variants, in place.
     *
     * <p>After this returns nothing downstream can tell a decomposed intent
     * from a hand-written one, which is the whole point: the catalog, the
     * executor and {@code ai explain} keep working on the only shape they have
     * ever known.
     */
    static int expand(
            Map<OrchestrationId, IntentDefinition> intents,
            Map<OrchestrationId, PlanDefinition> plans,
            Map<OrchestrationId, TaskDefinition> tasks
    ) {
        if (tasks.isEmpty()) {
            return 0;
        }
        Map<OrchestrationId, IntentDefinition> generated =
                new LinkedHashMap<>();
        List<OrchestrationId> decomposed = new ArrayList<>();
        for (Map.Entry<OrchestrationId, IntentDefinition> entry
                : intents.entrySet()) {
            IntentDefinition intent = entry.getValue();
            if (!tasks.containsKey(intent.plan())) {
                continue;
            }
            TaskIntentCompiler.Compiled compiled =
                    TaskIntentCompiler.compile(intent, tasks);
            for (IntentDefinition variant : compiled.intents()) {
                if (intents.containsKey(variant.id())
                        || generated.putIfAbsent(
                                variant.id(),
                                variant
                        ) != null) {
                    throw new IllegalArgumentException(
                            "Task decomposition generated duplicate intent "
                                    + variant.id()
                    );
                }
            }
            for (PlanDefinition plan : compiled.plans()) {
                if (plans.putIfAbsent(plan.id(), plan) != null) {
                    throw new IllegalArgumentException(
                            "Task decomposition generated duplicate plan "
                                    + plan.id()
                    );
                }
            }
            decomposed.add(entry.getKey());
        }
        // Collected first: the loop above reads the map that these two lines
        // rewrite.
        decomposed.forEach(intents::remove);
        intents.putAll(generated);
        return decomposed.size();
    }
}
