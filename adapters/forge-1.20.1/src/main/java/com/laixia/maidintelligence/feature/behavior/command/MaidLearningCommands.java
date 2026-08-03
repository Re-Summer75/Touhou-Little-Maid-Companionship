package com.laixia.maidintelligence.feature.behavior.command;

import com.laixia.maidintelligence.feature.behavior.api.MaidLearningApi;
import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.function.Predicate;

public final class MaidLearningCommands {
    private final MaidLearningApi<Entity> learning;
    private final Predicate<Entity> maidType;

    public MaidLearningCommands(
            MaidLearningApi<Entity> learning,
            Predicate<Entity> maidType
    ) {
        this.learning = Objects.requireNonNull(learning, "learning");
        this.maidType = Objects.requireNonNull(maidType, "maidType");
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(Commands.literal("tlmcompanionship")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ai")
                        .then(Commands.literal("learning")
                                .then(target("inspect", this::inspect))
                                .then(target("freeze", context ->
                                        freeze(context, true)))
                                .then(target("unfreeze", context ->
                                        freeze(context, false)))
                                .then(target("reset", this::reset))
                                .then(target("export", this::export)))));
    }

    private com.mojang.brigadier.builder.ArgumentBuilder<
            CommandSourceStack, ?> target(
            String operation,
            com.mojang.brigadier.Command<CommandSourceStack> command
    ) {
        return Commands.literal(operation).then(Commands.argument(
                "maid",
                EntityArgument.entity()
        ).executes(command));
    }

    private int inspect(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = maid(context);
        if (maid == null) {
            return 0;
        }
        CompanionLearningProfile profile = learning.profile(maid);
        context.getSource().sendSuccess(() -> Component.translatable(
                key("inspect"),
                learning.mode().name(),
                profile.frozen(),
                profile.revision(),
                profile.reliability().size(),
                profile.preferences().size(),
                profile.habits().size(),
                profile.recentSignals().size()
        ), false);
        return 1;
    }

    private int freeze(
            CommandContext<CommandSourceStack> context,
            boolean frozen
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = maid(context);
        if (maid == null) {
            return 0;
        }
        learning.freeze(maid, frozen);
        context.getSource().sendSuccess(() -> Component.translatable(
                key(frozen ? "frozen" : "unfrozen")
        ), false);
        return 1;
    }

    private int reset(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = maid(context);
        if (maid == null) {
            return 0;
        }
        learning.reset(maid);
        context.getSource().sendSuccess(
                () -> Component.translatable(key("reset")),
                false
        );
        return 1;
    }

    private int export(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity maid = maid(context);
        if (maid == null) {
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                key("export"),
                learning.export(maid)
        ), false);
        return 1;
    }

    private Entity maid(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "maid");
        if (!maidType.test(entity)) {
            context.getSource().sendFailure(Component.translatable(
                    "command." + ModResources.MOD_ID
                            + ".ai.intent.invalid_target"
            ));
            return null;
        }
        return entity;
    }

    private static String key(String suffix) {
        return "command." + ModResources.MOD_ID
                + ".ai.learning." + suffix;
    }
}
