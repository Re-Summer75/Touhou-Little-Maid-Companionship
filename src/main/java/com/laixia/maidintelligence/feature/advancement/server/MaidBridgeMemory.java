package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 桥接需要的少量瞬时状态：物品栏、药水效果、工具耐久与所处方块的指纹（用来做定时脏检查），
 * 进入下界时的位置（{@code nether_travel} 判定要用），
 * 以及女仆撮合过、还没生出幼崽的动物（{@code bred_animals} 判定要用）。
 * 这些都不入档，女仆卸载时随 {@link #forget} 丢掉。
 */
public final class MaidBridgeMemory {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Map<UUID, Integer> inventoryPrints = new HashMap<>();
    private final Map<UUID, Integer> effectPrints = new HashMap<>();
    private final Map<UUID, Vec3> netherEntries = new HashMap<>();
    private final Map<UUID, ToolWear> toolWear = new HashMap<>();
    private final Map<UUID, Integer> enteredBlocks = new HashMap<>();
    /** 女仆喂过、还没生出幼崽的动物，{@code bred_animals} 得等幼崽出生才算数。 */
    private final Map<Integer, UUID> courtedAnimals = new HashMap<>();

    /**
     * 物品栏是否和上次扫描不同。TLM 的工作任务会绕过所有事件直接改背包，所以要定时对账。
     */
    public boolean inventoryChanged(EntityMaid maid) {
        return changed(inventoryPrints, maid.getUUID(), inventoryPrint(maid));
    }

    /** 事件已经报告过变化时同步指纹，免得定时扫描再报一次。 */
    public void refreshInventory(EntityMaid maid) {
        inventoryPrints.put(maid.getUUID(), inventoryPrint(maid));
    }

    public boolean effectsChanged(EntityMaid maid) {
        return changed(effectPrints, maid.getUUID(), effectPrint(maid));
    }

    public void rememberNetherEntry(EntityMaid maid) {
        netherEntries.put(maid.getUUID(), maid.position());
    }

    public Optional<Vec3> takeNetherEntry(UUID maidId) {
        return Optional.ofNullable(netherEntries.remove(maidId));
    }

    /**
     * 手持工具的耐久是否变了。TLM 没有耐久事件，只能定时对账；换成另一件物品不算磨损。
     *
     * @return 变化后的损伤值，没有变化时为空
     */
    public Optional<Integer> toolWearChanged(EntityMaid maid) {
        ItemStack held = maid.getMainHandItem();
        if (held.isEmpty() || !held.isDamageableItem()) {
            toolWear.remove(maid.getUUID());
            return Optional.empty();
        }
        ToolWear current = new ToolWear(itemId(held.getItem()), held.getDamageValue());
        ToolWear previous = toolWear.put(maid.getUUID(), current);
        if (previous == null || previous.item() != current.item() || previous.damage() == current.damage()) {
            return Optional.empty();
        }
        return Optional.of(current.damage());
    }

    /**
     * 女仆所处方块是否换过了。{@code enter_block} 每 tick 都判定太贵，
     * 而且同一格重复上报没有意义。
     */
    public boolean enteredNewBlock(EntityMaid maid, int blockPrint) {
        Integer previous = enteredBlocks.put(maid.getUUID(), blockPrint);
        return previous == null || previous != blockPrint;
    }

    public void rememberCourtedAnimal(EntityMaid maid, int animalEntityId) {
        courtedAnimals.put(animalEntityId, maid.getUUID());
    }

    public Optional<UUID> takeCourtedAnimal(int animalEntityId) {
        return Optional.ofNullable(courtedAnimals.remove(animalEntityId));
    }

    public void forget(UUID maidId) {
        inventoryPrints.remove(maidId);
        effectPrints.remove(maidId);
        netherEntries.remove(maidId);
        toolWear.remove(maidId);
        enteredBlocks.remove(maidId);
        courtedAnimals.values().removeIf(maidId::equals);
    }

    public void clear() {
        inventoryPrints.clear();
        effectPrints.clear();
        netherEntries.clear();
        toolWear.clear();
        enteredBlocks.clear();
        courtedAnimals.clear();
    }

    private record ToolWear(int item, int damage) {
    }

    private static boolean changed(Map<UUID, Integer> prints, UUID maidId, int print) {
        Integer previous = prints.put(maidId, print);
        return previous == null || previous != print;
    }

    /**
     * 只看物品与数量，不看 NBT：耐久每次挥动都变，否则每秒都会报一次物品栏变化。
     */
    private static int inventoryPrint(EntityMaid maid) {
        int print = 1;
        print = 31 * print + stackPrint(maid.getMainHandItem());
        print = 31 * print + stackPrint(maid.getOffhandItem());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            print = 31 * print + stackPrint(maid.getItemBySlot(slot));
        }
        print = handlerPrint(print, maid.getMaidInv());
        print = handlerPrint(print, maid.getMaidBauble());
        return print;
    }

    private static int handlerPrint(int base, IItemHandler handler) {
        int print = base;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            print = 31 * print + stackPrint(handler.getStackInSlot(slot));
        }
        return print;
    }

    private static int stackPrint(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return 31 * itemId(stack.getItem()) + stack.getCount();
    }

    private static int itemId(Item item) {
        return BuiltInRegistries.ITEM.getId(item);
    }

    /** 只看效果与等级，不看剩余时长。 */
    private static int effectPrint(EntityMaid maid) {
        int print = 1;
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            print += 31 * BuiltInRegistries.MOB_EFFECT.getId(effect.getEffect()) + effect.getAmplifier();
        }
        return print;
    }
}
