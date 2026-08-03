package com.laixia.maidintelligence.feature.behavior.application.ability;

import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityTemplate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompiledAbilityTemplate;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.FactCondition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;
import com.laixia.maidintelligence.feature.orchestration.domain.action.ActionSchema;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AbilityTemplateCompiler {
    public CompiledAbilityTemplate compile(AbilityDefinition definition) {
        if (definition.template() != AbilityTemplate.WORLD_ITEM_DEPLOY) {
            throw new IllegalArgumentException(
                    "Unsupported ability template: " + definition.template()
            );
        }
        OrchestrationId planId = derived(definition.id(), "ability_plan/");
        OrchestrationId commandSignal =
                signal(definition.id(), AbilityActivationSource.COMMAND);
        OrchestrationId autonomousSignal =
                signal(definition.id(), AbilityActivationSource.AUTONOMOUS);
        Map<String, String> parameters =
                new LinkedHashMap<>(definition.parameters());
        parameters.put("ability_id", definition.id().toString());
        PlanDefinition plan = new PlanDefinition(
                planId,
                "execute",
                Map.of("execute", new PlanDefinition.State(
                        definition.action(),
                        parameters,
                        definition.timeoutTicks(),
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE,
                        PlanDefinition.FAILURE
                )),
                ResumePolicy.ATOMIC,
                Set.of(),
                definition.requestTtlTicks()
        );
        List<IntentDefinition> intents = List.of(
                intent(
                        definition,
                        planId,
                        commandSignal,
                        AbilityActivationSource.COMMAND
                ),
                intent(
                        definition,
                        planId,
                        autonomousSignal,
                        AbilityActivationSource.AUTONOMOUS
                )
        );
        return new CompiledAbilityTemplate(
                definition,
                plan,
                intents,
                commandSignal,
                autonomousSignal
        );
    }

    public List<CompiledAbilityTemplate> compileAll(
            Collection<AbilityDefinition> definitions
    ) {
        List<CompiledAbilityTemplate> compiled = new ArrayList<>();
        definitions.stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(definition -> compiled.add(compile(definition)));
        return List.copyOf(compiled);
    }

    public IntentVocabulary extendVocabulary(
            IntentVocabulary base,
            Collection<CompiledAbilityTemplate> templates
    ) {
        Set<OrchestrationId> facts = new HashSet<>(base.facts());
        Set<OrchestrationId> signals = new HashSet<>(base.signals());
        Map<OrchestrationId, FactType> factTypes =
                new HashMap<>(base.factTypes());
        for (CompiledAbilityTemplate template : templates) {
            addSignal(template.commandSignal(), facts, signals, factTypes);
            addSignal(template.autonomousSignal(), facts, signals, factTypes);
        }
        Map<OrchestrationId, ActionSchema> schemas =
                new HashMap<>(base.actionSchemas());
        return new IntentVocabulary(
                facts,
                base.actions(),
                signals,
                factTypes,
                schemas
        );
    }

    public static OrchestrationId signal(
            OrchestrationId ability,
            AbilityActivationSource source
    ) {
        return derived(
                ability,
                "ability_signal/",
                source.name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    private static IntentDefinition intent(
            AbilityDefinition definition,
            OrchestrationId plan,
            OrchestrationId signal,
            AbilityActivationSource source
    ) {
        double score = source == AbilityActivationSource.COMMAND
                ? definition.commandScore()
                : definition.autonomousScore();
        // Abilities without an owner-command soft-preempt lifecycle may only
        // start while native behavior occupancy is idle.
        return new IntentDefinition(
                derived(
                        definition.id(),
                        "ability_intent/",
                        source.name().toLowerCase(java.util.Locale.ROOT)
                ),
                plan,
                List.of(
                        new FactCondition(
                                signal,
                                FactComparison.EQUAL,
                                1.0D
                        ),
                        new FactCondition(
                                CompanionIntentIds.BEHAVIOR_OCCUPANCY_LEVEL,
                                FactComparison.EQUAL,
                                0.0D
                        )
                ),
                List.of(),
                score,
                Math.max(0.001D, score * 0.25D),
                1.0D,
                1,
                0,
                0.0D,
                definition.interruptPriority(),
                0
        );
    }

    private static void addSignal(
            OrchestrationId signal,
            Set<OrchestrationId> facts,
            Set<OrchestrationId> signals,
            Map<OrchestrationId, FactType> factTypes
    ) {
        facts.add(signal);
        signals.add(signal);
        factTypes.put(signal, FactType.SIGNAL);
    }

    private static OrchestrationId derived(
            OrchestrationId ability,
            String prefix,
            String... suffix
    ) {
        StringBuilder path = new StringBuilder(prefix)
                .append(ability.path());
        for (String part : suffix) {
            path.append('/').append(part);
        }
        return new OrchestrationId(ability.namespace(), path.toString());
    }
}
