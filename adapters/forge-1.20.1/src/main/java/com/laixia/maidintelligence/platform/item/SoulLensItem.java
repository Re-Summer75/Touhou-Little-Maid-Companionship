package com.laixia.maidintelligence.platform.item;

import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Held to see what each nearby maid is thinking.
 *
 * <p>The item itself does nothing — no use action, no state. Holding it is the
 * whole interface: the server notices and starts sending decision summaries,
 * and the client draws them beside each maid. That keeps a diagnostic view from
 * costing anything for players who are not looking at one, without needing a
 * toggle to remember or a screen to open.
 */
public final class SoulLensItem extends Item {
    private static final String TIP_KEY =
            ModResources.translationKey("item", "soul_lens.tip");

    public SoulLensItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * Either hand counts, so the lens can be held alongside whatever the player
     * is actually working with.
     */
    public static boolean isHeldBy(@Nullable Player player) {
        return player != null
                && (player.getMainHandItem().getItem() instanceof SoulLensItem
                || player.getOffhandItem().getItem() instanceof SoulLensItem);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable(TIP_KEY)
                .withStyle(ChatFormatting.GRAY));
    }
}
