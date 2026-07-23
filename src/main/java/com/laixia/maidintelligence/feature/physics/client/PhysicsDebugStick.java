package com.laixia.maidintelligence.feature.physics.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A plain stick tagged so the skeleton debug overlay draws while it is held.
 * Uses a vanilla item plus NBT rather than a registered item so the debug aid
 * needs no model, texture, or registration.
 */
public final class PhysicsDebugStick {
    private static final String TAG = "MaidPhysicsDebug";

    private PhysicsDebugStick() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.getOrCreateTag().putBoolean(TAG, true);
        stack.setHoverName(Component
                .literal("女仆骨骼调试棒")
                .withStyle(ChatFormatting.AQUA));
        return stack;
    }

    public static boolean isHeldBy(Player player) {
        return player != null
                && (isDebugStick(player.getMainHandItem())
                || isDebugStick(player.getOffhandItem()));
    }

    public static boolean isDebugStick(ItemStack stack) {
        return stack.is(Items.STICK)
                && stack.getTag() != null
                && stack.getTag().getBoolean(TAG);
    }
}
