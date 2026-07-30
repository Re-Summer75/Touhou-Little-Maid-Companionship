package com.laixia.maidintelligence.feature.advancement.menu;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 进度页的容器。沿用本体配置页的做法：只带玩家背包槽位，让界面继承到女仆界面的全套外框
 * （左侧状态区、顶部 Tab 条、任务列表等），进度树画在右侧内容区。
 */
public final class MaidAdvancementContainer extends AbstractMaidContainer {
    private static final int PLAYER_INVENTORY_SIZE = 27;

    public MaidAdvancementContainer(int id, Inventory inventory, int entityId) {
        super(AdvancementMenus.ADVANCEMENT_PAGE.get(), id, inventory, entityId);
    }

    public static MenuProvider create(int entityId) {
        return new SimpleMenuProvider(
                (id, inventory, player) -> new MaidAdvancementContainer(id, inventory, entityId),
                Component.empty()
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();
        if (index < PLAYER_INVENTORY_SIZE) {
            if (!this.moveItemStackTo(slotStack, PLAYER_INVENTORY_SIZE, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(slotStack, 0, PLAYER_INVENTORY_SIZE, true)) {
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
