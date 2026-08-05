package com.laixia.maidintelligence.feature.orchestration.tlm.context;

import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.food.FoodProperties;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reads what a maid can notice about her owner.
 *
 * <p>Cached per owner per tick rather than per maid. Everything here describes
 * the owner alone, so several maids serving one person would otherwise repeat
 * identical work — and the pack scan below would repeat with them, which is the
 * one reading expensive enough to matter.
 */
public final class TlmOwnerFactReader {
    /** Roughly a sprint, so ordinary walking lands near the middle. */
    private static final double SPRINT_SPEED = 0.28D;

    /** Nutrition of a hearty cooked meal; better food simply saturates. */
    private static final double GOOD_NUTRITION = 10.0D;

    /** Vanilla's damage-tint duration, which is what hurtTime counts down. */
    private static final double HURT_TICKS = 10.0D;

    /**
     * A pack scan walks every slot, so it runs on its own slower clock. What it
     * measures — whether he is carrying food at all — does not change between
     * one tick and the next in any way a maid could act on.
     */
    private static final int INVENTORY_INTERVAL_TICKS = 40;

    private final Map<LivingEntity, Entry> entries = new WeakHashMap<>();

    /**
     * @param owner the maid's owner, or null when she has none in reach
     */
    public OwnerFacts read(LivingEntity owner, long gameTime) {
        if (owner == null || !owner.isAlive()) {
            return OwnerFacts.absent();
        }
        Entry entry = entries.computeIfAbsent(owner, ignored -> new Entry());
        if (entry.tick == gameTime && entry.facts != null) {
            return entry.facts;
        }
        entry.facts = build(owner, gameTime, entry);
        entry.tick = gameTime;
        return entry.facts;
    }

    private OwnerFacts build(
            LivingEntity owner,
            long gameTime,
            Entry entry
    ) {
        ItemStack main = owner.getMainHandItem();
        ItemStack off = owner.getOffhandItem();
        Player player = owner instanceof Player casted ? casted : null;
        refreshInventory(player, gameTime, entry);
        return new OwnerFacts(
                flag(isFood(owner, main) || isFood(owner, off)),
                foodQuality(owner, main, off),
                flag(isWeapon(main) || isWeapon(off)),
                flag(isTool(main) || isTool(off)),
                flag(main.getItem() instanceof BlockItem
                        || off.getItem() instanceof BlockItem),
                flag(main.isEmpty() && off.isEmpty()),
                fraction(owner.getArmorValue(), 20.0D),
                flag(owner.isUsingItem()),
                fraction(owner.getHealth(), owner.getMaxHealth()),
                player == null
                        ? Double.NaN
                        : fraction(player.getFoodData().getFoodLevel(), 20.0D),
                fraction(owner.getAirSupply(), owner.getMaxAirSupply()),
                flag(owner.isOnFire()),
                flag(owner.isInWater()),
                fraction(owner.hurtTime, HURT_TICKS),
                flag(owner.isShiftKeyDown()),
                flag(owner.isSprinting()),
                flag(owner.isPassenger()),
                flag(owner.isSleeping()),
                flag(owner.isFallFlying()
                        || (player != null && player.getAbilities().flying)),
                speed(owner, gameTime, entry),
                effectCount(owner, MobEffectCategory.HARMFUL),
                effectCount(owner, MobEffectCategory.BENEFICIAL),
                entry.inventoryFood,
                entry.inventoryFullness,
                player == null ? Double.NaN : player.experienceLevel
        );
    }

    /**
     * Ground speed from where he actually moved. A player's own velocity field
     * is client-authoritative and often reads zero on the server even as he
     * runs, so the distance covered since the last reading is what counts.
     */
    private static double speed(
            LivingEntity owner,
            long gameTime,
            Entry entry
    ) {
        double x = owner.getX();
        double z = owner.getZ();
        long elapsed = gameTime - entry.positionTick;
        double result = 0.0D;
        if (entry.positionTick != Long.MIN_VALUE
                && elapsed > 0L
                && elapsed <= 20L) {
            double dx = x - entry.lastX;
            double dz = z - entry.lastZ;
            double perTick = Math.sqrt(dx * dx + dz * dz) / elapsed;
            result = Math.min(1.0D, perTick / SPRINT_SPEED);
        }
        entry.lastX = x;
        entry.lastZ = z;
        entry.positionTick = gameTime;
        return result;
    }

    private static void refreshInventory(
            Player player,
            long gameTime,
            Entry entry
    ) {
        if (player == null) {
            entry.inventoryFood = Double.NaN;
            entry.inventoryFullness = Double.NaN;
            return;
        }
        if (entry.inventoryTick != Long.MIN_VALUE
                && gameTime - entry.inventoryTick < INVENTORY_INTERVAL_TICKS
                && gameTime >= entry.inventoryTick) {
            return;
        }
        entry.inventoryTick = gameTime;
        Inventory inventory = player.getInventory();
        int slots = inventory.items.size();
        int filled = 0;
        int food = 0;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            filled++;
            if (isFood(player, stack)) {
                food++;
            }
        }
        entry.inventoryFullness = fraction(filled, slots);
        entry.inventoryFood = fraction(food, slots);
    }

    private static double foodQuality(
            LivingEntity owner,
            ItemStack main,
            ItemStack off
    ) {
        double best = Math.max(
                nutrition(owner, main),
                nutrition(owner, off)
        );
        return best < 0.0D ? 0.0D : Math.min(1.0D, best / GOOD_NUTRITION);
    }

    private static double nutrition(LivingEntity owner, ItemStack stack) {
        FoodProperties properties = food(owner, stack);
        return properties == null ? -1.0D : properties.getNutrition();
    }

    private static boolean isFood(LivingEntity owner, ItemStack stack) {
        return food(owner, stack) != null;
    }

    private static FoodProperties food(LivingEntity owner, ItemStack stack) {
        return stack.isEmpty() ? null : stack.getFoodProperties(owner);
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem;
    }

    /**
     * An axe answers to both this and {@link #isWeapon}, which is the honest
     * reading: whether it is being carried to fell trees or to fight is not
     * something the item can say.
     */
    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof DiggerItem;
    }

    private static double effectCount(
            LivingEntity owner,
            MobEffectCategory category
    ) {
        int count = 0;
        for (MobEffectInstance effect : owner.getActiveEffects()) {
            if (effect.getEffect().getCategory() == category) {
                count++;
            }
        }
        return count;
    }

    private static double flag(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private static double fraction(double value, double maximum) {
        if (maximum <= 0.0D || !Double.isFinite(value)) {
            return Double.NaN;
        }
        return Math.max(0.0D, Math.min(1.0D, value / maximum));
    }

    private static final class Entry {
        private long tick = Long.MIN_VALUE;
        private OwnerFacts facts;
        private long positionTick = Long.MIN_VALUE;
        private double lastX;
        private double lastZ;
        private long inventoryTick = Long.MIN_VALUE;
        private double inventoryFood = Double.NaN;
        private double inventoryFullness = Double.NaN;
    }
}
