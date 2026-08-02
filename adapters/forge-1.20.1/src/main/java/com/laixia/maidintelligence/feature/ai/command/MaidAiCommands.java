package com.laixia.maidintelligence.feature.ai.command;

import com.laixia.maidintelligence.feature.ai.api.AiOptimizationSnapshot;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationSnapshot;
import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class MaidAiCommands {
    private final MaidAiOptimizationApi optimization;
    private final MaidMovementCoordinationApi movementCoordination;
    private final MaidIntentApi<Entity> intents;
    private final Predicate<Entity> maidType;
    private final BooleanSupplier diagnosticsEnabled;

    public MaidAiCommands(
            MaidAiOptimizationApi optimization,
            MaidMovementCoordinationApi movementCoordination,
            MaidIntentApi<Entity> intents,
            Predicate<Entity> maidType,
            BooleanSupplier diagnosticsEnabled
    ) {
        this.optimization = Objects.requireNonNull(
                optimization,
                "optimization"
        );
        this.movementCoordination = Objects.requireNonNull(
                movementCoordination,
                "movementCoordination"
        );
        this.intents = Objects.requireNonNull(intents, "intents");
        this.maidType = Objects.requireNonNull(maidType, "maidType");
        this.diagnosticsEnabled = Objects.requireNonNull(
                diagnosticsEnabled,
                "diagnosticsEnabled"
        );
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
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
                        .then(Commands.literal("reset")
                                .executes(this::resetStats))));
    }

    private int showStats(CommandContext<CommandSourceStack> context) {
        AiOptimizationSnapshot snapshot = optimization.snapshot();
        String hitRate = String.format(
                Locale.ROOT,
                "%.1f",
                snapshot.pathCacheHitRate() * 100.0D
        );
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.stats"),
                snapshot.pathRequests(),
                snapshot.pathCacheHits(),
                hitRate,
                snapshot.pickupCandidates(),
                snapshot.pickupSelections(),
                snapshot.brainTickSamples(),
                nanosToMicros(snapshot.averageBrainTickNanos()),
                nanosToMicros(snapshot.brainTickMaxNanos())
        ), false);
        MovementCoordinationSnapshot movement =
                movementCoordination.snapshot();
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.movement.stats"),
                movementCoordination.mode().name(),
                movement.claims(),
                movement.renewals(),
                movement.retargets(),
                movement.preemptions(),
                movement.suppressions(),
                movement.observedConflicts(),
                movement.failOpenTransitions()
        ), false);
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.adaptive.stats"),
                snapshot.activityRadiusExpansions(),
                snapshot.combatScans(),
                snapshot.combatCandidates(),
                snapshot.combatCandidateTruncations(),
                snapshot.combatTargets(),
                snapshot.maidAttackerTargets(),
                snapshot.ownerAttackerTargets(),
                snapshot.ownerTargetTargets(),
                snapshot.proactiveHostileTargets()
        ), false);
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
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("ai.intent.explain"),
                trace.activeIntent() == null
                        ? "-"
                        : trace.activeIntent().toString(),
                trace.activeState().isEmpty() ? "-" : trace.activeState(),
                trace.activeSinceTick(),
                trace.lastTransition()
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
        return 1;
    }

    private int resetStats(CommandContext<CommandSourceStack> context) {
        optimization.resetMetrics();
        movementCoordination.resetMetrics();
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
