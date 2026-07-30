package com.laixia.maidintelligence.feature.level.command;

import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.LevelChange;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Forge command tree backed by an entity-neutral level API. The distribution
 * composition root supplies the version-specific TLM entity adapter.
 */
public final class LevelCommands {
    private static final DynamicCommandExceptionType NOT_A_MAID =
            new DynamicCommandExceptionType(
                    value -> Component.translatable(
                            commandKey("error.not_maid"),
                            value
                    )
            );

    private final MaidLevelApi<Entity> levelApi;
    private final Predicate<Entity> maidPredicate;

    public LevelCommands(
            MaidLevelApi<Entity> levelApi,
            Predicate<Entity> maidPredicate
    ) {
        this.levelApi = Objects.requireNonNull(levelApi, "levelApi");
        this.maidPredicate = Objects.requireNonNull(
                maidPredicate,
                "maidPredicate"
        );
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var getCommand = Commands.literal("get")
                .then(Commands.argument("maid", EntityArgument.entity())
                        .executes(this::getLevel));
        var addCommand = Commands.literal("add")
                .then(Commands.argument("maid", EntityArgument.entity())
                        .then(Commands.argument(
                                        "experience",
                                        IntegerArgumentType.integer(0)
                                )
                                .executes(this::addExperience)));
        var setCommand = Commands.literal("set")
                .then(Commands.argument("maid", EntityArgument.entity())
                        .then(Commands.argument(
                                        "level",
                                        IntegerArgumentType.integer(
                                                1,
                                                DefaultLevelCurve.MAX_LEVEL
                                        )
                                )
                                .executes(context -> setLevel(context, 0))
                                .then(Commands.argument(
                                                "experience",
                                                IntegerArgumentType.integer(0)
                                        )
                                        .executes(context -> setLevel(
                                                context,
                                                IntegerArgumentType.getInteger(
                                                        context,
                                                        "experience"
                                                )
                                        )))));

        dispatcher.register(Commands.literal("tlmcompanionship")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("level")
                        .then(getCommand)
                        .then(addCommand)
                        .then(setCommand)));
    }

    private int getLevel(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Entity maid = getMaid(context);
        LevelProgress progress = levelApi.getProgress(maid);
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("level.get"),
                maid.getDisplayName(),
                progress.level(),
                progress.experience()
        ), false);
        return progress.level();
    }

    private int addExperience(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Entity maid = getMaid(context);
        int amount = IntegerArgumentType.getInteger(context, "experience");
        LevelChange change = levelApi.awardExperience(
                maid,
                amount,
                ExperienceSource.COMMAND
        );
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("level.add"),
                maid.getDisplayName(),
                amount,
                change.after().level(),
                change.after().experience()
        ), true);
        return change.after().level();
    }

    private int setLevel(
            CommandContext<CommandSourceStack> context,
            int experience
    ) throws CommandSyntaxException {
        Entity maid = getMaid(context);
        int level = IntegerArgumentType.getInteger(context, "level");
        LevelProgress progress = levelApi.setProgress(
                maid,
                level,
                experience
        );
        context.getSource().sendSuccess(() -> Component.translatable(
                commandKey("level.set"),
                maid.getDisplayName(),
                progress.level(),
                progress.experience()
        ), true);
        return progress.level();
    }

    private Entity getMaid(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "maid");
        if (maidPredicate.test(entity)) {
            return entity;
        }
        throw NOT_A_MAID.create(entity.getDisplayName());
    }

    private static String commandKey(String path) {
        return ModResources.translationKey("command", path);
    }
}
