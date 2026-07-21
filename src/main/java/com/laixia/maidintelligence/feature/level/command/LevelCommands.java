package com.laixia.maidintelligence.feature.level.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.LevelChange;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
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

public final class LevelCommands {
    private static final DynamicCommandExceptionType NOT_A_MAID = new DynamicCommandExceptionType(
            value -> Component.translatable("command.maid_intelligence.error.not_maid", value)
    );

    private final MaidLevelApi levelApi;

    public LevelCommands(MaidLevelApi levelApi) {
        this.levelApi = levelApi;
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
                        .then(Commands.argument("experience", IntegerArgumentType.integer(0))
                                .executes(this::addExperience)));
        var setCommand = Commands.literal("set")
                .then(Commands.argument("maid", EntityArgument.entity())
                        .then(Commands.argument(
                                        "level",
                                        IntegerArgumentType.integer(1, DefaultLevelCurve.MAX_LEVEL)
                                )
                                .executes(context -> setLevel(context, 0))
                                .then(Commands.argument("experience", IntegerArgumentType.integer(0))
                                        .executes(context -> setLevel(
                                                context,
                                                IntegerArgumentType.getInteger(context, "experience")
                                        )))));

        dispatcher.register(Commands.literal("maidintelligence")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("level")
                        .then(getCommand)
                        .then(addCommand)
                        .then(setCommand)));
    }

    private int getLevel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityMaid maid = getMaid(context);
        LevelProgress progress = levelApi.getProgress(maid);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.maid_intelligence.level.get",
                maid.getDisplayName(),
                progress.level(),
                progress.experience()
        ), false);
        return progress.level();
    }

    private int addExperience(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityMaid maid = getMaid(context);
        int amount = IntegerArgumentType.getInteger(context, "experience");
        LevelChange change = levelApi.awardExperience(maid, amount, ExperienceSource.COMMAND);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.maid_intelligence.level.add",
                maid.getDisplayName(),
                amount,
                change.after().level(),
                change.after().experience()
        ), true);
        return change.after().level();
    }

    private int setLevel(CommandContext<CommandSourceStack> context, int experience) throws CommandSyntaxException {
        EntityMaid maid = getMaid(context);
        int level = IntegerArgumentType.getInteger(context, "level");
        LevelProgress progress = levelApi.setProgress(maid, level, experience);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.maid_intelligence.level.set",
                maid.getDisplayName(),
                progress.level(),
                progress.experience()
        ), true);
        return progress.level();
    }

    private static EntityMaid getMaid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(context, "maid");
        if (entity instanceof EntityMaid maid) {
            return maid;
        }
        throw NOT_A_MAID.create(entity.getDisplayName());
    }
}
