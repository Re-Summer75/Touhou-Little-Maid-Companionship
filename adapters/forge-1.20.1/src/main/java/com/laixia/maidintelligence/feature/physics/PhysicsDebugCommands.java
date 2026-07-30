package com.laixia.maidintelligence.feature.physics;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.client.PhysicsDebugStick;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Registers {@code /maidphysicsdebug}, which hands the caller the skeleton
 * debug stick. The stick is a tagged vanilla item, so the command works
 * without any custom item registration and the overlay it toggles is purely
 * client-side.
 */
public final class PhysicsDebugCommands {
    private PhysicsDebugCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("maidphysicsdebug")
                        .executes(PhysicsDebugCommands::giveDebugStick)
        );
    }

    private static int giveDebugStick(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = PhysicsDebugStick.create();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("已给予女仆骨骼调试棒，手持即可显示骨骼与顶点"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }
}
