package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.port.MaidCourtshipMemory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 桥接需要的少量瞬时状态：物品栏、药水效果、工具耐久与所处方块的指纹（用来做定时脏检查），
 * 进入下界时的位置（{@code nether_travel} 判定要用），
 * 以及女仆撮合过、还没生出幼崽的动物（{@code bred_animals} 判定要用）。
 * 这些都不入档，女仆卸载时随 {@link #forget} 丢掉。
 */
public final class MaidBridgeMemory
        implements MaidCourtshipMemory<EntityMaid> {
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
    /** 浮空的起点与起始 tick；判定拿它和当前位置比落差。 */
    private final Map<UUID, Levitation> levitationStarts = new HashMap<>();

    /** 岩浆骑行的起点；同为 DistanceTrigger，比的是起点到当前位置。 */
    private final Map<UUID, Vec3> lavaRideStarts = new HashMap<>();

    /** 她最近扔出去的那支三叉戟带不带引雷。 */
    private final Set<UUID> channellingThrows = new HashSet<>();

    /** 当前这一发弩打死的目标，按女仆分组；每次射击重置。 */
    private final Map<UUID, List<Entity>> crossbowKills = new HashMap<>();

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

    @Override
    public void rememberCourtedAnimal(EntityMaid maid, int animalEntityId) {
        courtedAnimals.put(animalEntityId, maid.getUUID());
    }

    public Optional<UUID> takeCourtedAnimal(int animalEntityId) {
        return Optional.ofNullable(courtedAnimals.remove(animalEntityId));
    }

    /**
     * 这一段浮空是从哪儿、从第几 tick 开始的。
     *
     * <p>判定拿的是**起点与当前位置的落差**，而不是一段走完的位移，所以起点必须
     * 停在效果出现的那一刻，不能每 tick 刷新。
     */
    public record Levitation(Vec3 from, int startTick) {
    }

    /** 已经在浮空就保持原来的起点，否则从这一刻开始记。 */
    public Levitation beginLevitation(EntityMaid maid) {
        return levitationStarts.computeIfAbsent(
                maid.getUUID(),
                id -> new Levitation(maid.position(), maid.tickCount)
        );
    }

    /** 效果没了就把这一段丢掉。 */
    public void endLevitation(UUID maidId) {
        levitationStarts.remove(maidId);
    }

    /**
     * 这一趟岩浆骑行是从哪儿开始的。
     *
     * <p>与浮空同一形状：判定是个 {@code DistanceTrigger}，比的是起点到当前位置，
     * 所以起点要停在骑上去的那一刻。
     */
    public Vec3 beginLavaRide(EntityMaid maid) {
        return lavaRideStarts.computeIfAbsent(
                maid.getUUID(), id -> maid.position()
        );
    }

    /** 下来了或者离开岩浆了，这一趟就结束了。 */
    public void endLavaRide(UUID maidId) {
        lavaRideStarts.remove(maidId);
    }

    /**
     * 记下她刚扔出去的三叉戟带不带引雷。
     *
     * <p>要在投掷那一刻记：{@code ThrownTrident#getPickupItem} 是 protected，
     * 飞行中的三叉戟读不到附魔，而她松手时手上还拿着它。
     */
    public void rememberChannellingThrow(EntityMaid maid, boolean channelling) {
        if (channelling) {
            channellingThrows.add(maid.getUUID());
        } else {
            channellingThrows.remove(maid.getUUID());
        }
    }

    /** 这一支是不是引雷三叉戟；取走后即失效，一支只认领一次。 */
    public boolean takeChannellingThrow(UUID maidId) {
        return channellingThrows.remove(maidId);
    }

    /**
     * 开始新的一发弩。
     *
     * <p>`killed_by_crossbow` 数的是**一发**打死了几个（`arbalistic` 要一发五杀），
     * 不是累计击杀，所以每次射击都要另起一组。多重射击一次放三支箭，它们属于同一发。
     */
    public void beginCrossbowShot(EntityMaid maid) {
        crossbowKills.put(maid.getUUID(), new ArrayList<>());
    }

    /**
     * 记一笔弩矢击杀，并回报这一发到目前为止打死的全部目标。
     *
     * <p>空表示这一发不存在——她没射过弩，或者这次击杀不该算在弩头上。原版每次
     * 击杀都拿整份名单去判定，criterion 自己数不重复的种类，这里照做。
     */
    public List<Entity> recordCrossbowKill(UUID maidId, Entity victim) {
        List<Entity> shot = crossbowKills.get(maidId);
        if (shot == null) {
            return List.of();
        }
        shot.add(victim);
        return List.copyOf(shot);
    }

    public void forget(UUID maidId) {
        inventoryPrints.remove(maidId);
        effectPrints.remove(maidId);
        netherEntries.remove(maidId);
        toolWear.remove(maidId);
        enteredBlocks.remove(maidId);
        levitationStarts.remove(maidId);
        lavaRideStarts.remove(maidId);
        channellingThrows.remove(maidId);
        crossbowKills.remove(maidId);
        courtedAnimals.values().removeIf(maidId::equals);
    }

    public void clear() {
        inventoryPrints.clear();
        effectPrints.clear();
        netherEntries.clear();
        toolWear.clear();
        enteredBlocks.clear();
        levitationStarts.clear();
        lavaRideStarts.clear();
        channellingThrows.clear();
        crossbowKills.clear();
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
