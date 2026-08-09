package com.laixia.maidintelligence.feature.ai.command;

import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidLearningApi;
import com.laixia.maidintelligence.feature.behavior.command.MaidLearningCommands;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityRuntimeState;
import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.DecisionTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTraceComparison;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MaidAiCommands {
    private final MaidIntentApi<Entity> intents;
    private final MaidAbilityApi<Entity> abilities;
    private final Predicate<Entity> maidType;
    private final BooleanSupplier diagnosticsEnabled;
    private final MaidLearningCommands learningCommands;

    public MaidAiCommands(
            MaidIntentApi<Entity> intents,
            MaidAbilityApi<Entity> abilities,
            MaidLearningApi<Entity> learning,
            Predicate<Entity> maidType,
            BooleanSupplier diagnosticsEnabled
    ) {
        this.intents = Objects.requireNonNull(intents, "intents");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.maidType = Objects.requireNonNull(maidType, "maidType");
        this.diagnosticsEnabled = Objects.requireNonNull(
                diagnosticsEnabled,
                "diagnosticsEnabled"
        );
        this.learningCommands = new MaidLearningCommands(
                learning,
                maidType
        );
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
        learningCommands.onRegisterCommands(event);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tlmcompanionship")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ai")
                        .then(Commands.literal("stats")
                                .executes(this::showStats))
                        .then(Commands.literal("explain")
                                .then(Commands.argument(
                                        "maid",
                                        EntityArgument.entity()
                                ).executes(this::explain)))
                        .then(abilityCommands())
                        .then(Commands.literal("reset")
                                .executes(this::resetStats))));
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<
            CommandSourceStack, ?> abilityCommands() {
        return Commands.literal("ability")
                .then(Commands.literal("grant")
                        .then(abilityTarget(this::grantAbility)))
                .then(Commands.literal("revoke")
                        .then(abilityTarget(this::revokeAbility)))
                .then(Commands.literal("activate")
                        .then(abilityTarget(this::activateAbility)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(
                                "maid",
                                EntityArgument.entity()
                        ).executes(this::inspectAbilities)));
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<
            CommandSourceStack, ?> abilityTarget(
            com.mojang.brigadier.Command<
                    CommandSourceStack> command
    ) {
        return Commands.argument("maid", EntityArgument.entity())
                .then(Commands.argument(
                        "ability",
                        ResourceLocationArgument.id()
                ).executes(command));
    }

    private int showStats(CommandContext<CommandSourceStack> context) {
        IntentMetrics intent = intents.metrics();
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.intent.stats"),
                intent.catalogGeneration(),
                intent.intentDefinitions(),
                intent.planDefinitions(),
                intent.evaluations(),
                intent.activations(),
                intent.switches(),
                intent.interruptions(),
                intent.failures(),
                intent.cancellations()
        ), false);
        return 1;
    }

    private int explain(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!diagnosticsEnabled.getAsBoolean()) {
            context.getSource().sendFailure(Component.translatable(
                    commandKey("ai.intent.diagnostics_disabled")
            ));
            return 0;
        }
        Entity entity = EntityArgument.getEntity(context, "maid");
        if (!maidType.test(entity)) {
            context.getSource().sendFailure(Component.translatable(
                    commandKey("ai.intent.invalid_target")
            ));
            return 0;
        }
        IntentTrace trace = intents.inspect(entity);
        DecisionTrace decision = intents.inspectDecision(entity);
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.intent.explain"),
                trace.activeIntent() == null
                        ? "-"
                        : trace.activeIntent().toString(),
                trace.activeState().isEmpty() ? "-" : trace.activeState(),
                trace.activeSinceTick(),
                trace.lastTransition()
        ), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.intent.observability"),
                decision.decisionId().toString(),
                decision.facts().size(),
                decision.observations().events().size(),
                decision.observations().beliefs().size(),
                decision.observations().outcomes().size(),
                decision.hasActiveOperation()
                        ? decision.activeOperationId().toString()
                        : "-"
        ), false);
        for (IntentTrace.Candidate candidate : trace.candidates()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    commandKey("ai.intent.candidate"),
                    candidate.intent().toString(),
                    String.format(
                            Locale.ROOT,
                            "%.3f",
                            candidate.score()
                    ),
                    candidate.status()
            ), false);
        }
        IntentTraceComparison comparison = intents.compare(entity);
        if (comparison.shadowAvailable()) {
            IntentTrace shadow = comparison.shadow();
            context.getSource().sendSuccess(() -> Component.translatable(
                    commandKey("ai.intent.shadow"),
                    shadow.activeIntent() == null
                            ? "-"
                            : shadow.activeIntent().toString(),
                    shadow.activeState().isEmpty()
                            ? "-"
                            : shadow.activeState(),
                    comparison.activeIntentMatches(),
                    comparison.matchingCandidates(),
                    comparison.comparedCandidates()
            ), false);
        } else {
            context.getSource().sendSuccess(() -> Component.translatable(
                    commandKey("ai.intent.shadow_disabled")
            ), false);
        }
        return 1;
    }

    private int grantAbility(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = abilityMaid(context);
        if (maid == null) {
            return 0;
        }
        OrchestrationId ability = abilityId(context);
        boolean granted = abilities.grant(
                maid,
                ability,
                context.getSource().getLevel().getGameTime(),
                "operator_command"
        );
        return abilityResult(
                context,
                granted,
                "ai.ability.granted",
                "ai.ability.unknown",
                ability
        );
    }

    private int revokeAbility(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = abilityMaid(context);
        if (maid == null) {
            return 0;
        }
        OrchestrationId ability = abilityId(context);
        boolean revoked = abilities.revoke(maid, ability);
        return abilityResult(
                context,
                revoked,
                "ai.ability.revoked",
                "ai.ability.not_granted",
                ability
        );
    }

    private int activateAbility(
            CommandContext<CommandSourceStack> context
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = abilityMaid(context);
        if (maid == null) {
            return 0;
        }
        OrchestrationId ability = abilityId(context);
        boolean requested = abilities.request(
                maid,
                ability,
                AbilityActivationSource.COMMAND,
                context.getSource().getLevel().getGameTime()
        ).isPresent();
        return abilityResult(
                context,
                requested,
                "ai.ability.requested",
                "ai.ability.unavailable",
                ability
        );
    }

    private int inspectAbilities(
            CommandContext<CommandSourceStack> context
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = abilityMaid(context);
        if (maid == null) {
            return 0;
        }
        AbilityRuntimeState runtime = abilities.inspect(
                maid,
                context.getSource().getLevel().getGameTime()
        );
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.ability.inspect"),
                abilities.catalogGeneration(),
                abilities.grants(maid).grants().size(),
                runtime.requests().size(),
                runtime.cooldownUntil().size(),
                runtime.executing().size()
        ), false);
        return 1;
    }

    private Entity abilityMaid(
            CommandContext<CommandSourceStack> context
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "maid");
        if (!maidType.test(entity)) {
            context.getSource().sendFailure(Component.translatable(
                    commandKey("ai.intent.invalid_target")
            ));
            return null;
        }
        return entity;
    }

    private static OrchestrationId abilityId(
            CommandContext<CommandSourceStack> context
    ) {
        var id = ResourceLocationArgument.getId(context, "ability");
        return new OrchestrationId(id.getNamespace(), id.getPath());
    }

    private static int abilityResult(
            CommandContext<CommandSourceStack> context,
            boolean succeeded,
            String successKey,
            String failureKey,
            OrchestrationId ability
    ) {
        if (succeeded) {
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            commandKey(successKey),
                            ability.toString()
                    ),
                    false
            );
            return 1;
        }
        context.getSource().sendFailure(Component.translatable(
                commandKey(failureKey),
                ability.toString()
        ));
        return 0;
    }

    private int resetStats(CommandContext<CommandSourceStack> context) {
        intents.resetMetrics();
        context.getSource().sendSuccess(
                () -> Component.translatable(commandKey("ai.reset")),
                false
        );
        return 1;
    }

    private static long nanosToMicros(long nanos) {
        return nanos / 1_000L;
    }

    private static String commandKey(String suffix) {
        return "command." + ModResources.MOD_ID + "." + suffix;
    }
}
