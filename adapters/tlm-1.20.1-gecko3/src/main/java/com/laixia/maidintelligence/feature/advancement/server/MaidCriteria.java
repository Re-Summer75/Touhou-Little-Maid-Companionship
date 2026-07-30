package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.MaidAdvancementFeature;
import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.laixia.maidintelligence.feature.advancement.tlm.MaidStatisticsData;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * 桥接门面：所有「女仆做了某件事」的地方都只写一行，具体的镜像绑定与进度查找在
 * {@link MaidAdvancementManager} 里。没有对应封装的触发器直接用 {@link #fire} 传原版调用。
 */
public final class MaidCriteria {
    private MaidCriteria() {
    }

    public static void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        MaidAdvancementFeature.INSTANCE.manager().fire(maid, action);
    }

    public static void inventoryChanged(EntityMaid maid, ItemStack changed) {
        fire(maid, mirror -> CriteriaTriggers.INVENTORY_CHANGED.trigger(
                mirror,
                mirror.getInventory(),
                changed
        ));
    }

    /**
     * 对当前完整物品栏重放变化。TLM 的拾取与部分工作任务可能不提供可靠的 changed stack，
     * 而原版 {@code inventory_changed} 的物品谓词只检查该参数，单传空物品会漏掉这类获得。
     */
    public static void inventoryChanged(EntityMaid maid) {
        fire(maid, mirror -> {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(mirror, mirror.getInventory(), ItemStack.EMPTY);
            for (ItemStack stack : mirror.getInventory().items) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(mirror, mirror.getInventory(), stack);
                }
            }
            for (ItemStack stack : mirror.getInventory().armor) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(mirror, mirror.getInventory(), stack);
                }
            }
            for (ItemStack stack : mirror.getInventory().offhand) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(mirror, mirror.getInventory(), stack);
                }
            }
        });
    }

    /** 对齐原版玩家每 20 tick 一次的位置判定。 */
    public static void location(EntityMaid maid) {
        fire(maid, CriteriaTriggers.LOCATION::trigger);
    }

    public static void effectsChanged(EntityMaid maid, @Nullable Entity source) {
        fire(maid, mirror -> CriteriaTriggers.EFFECTS_CHANGED.trigger(mirror, source));
    }

    public static void consumedItem(EntityMaid maid, ItemStack food) {
        fire(maid, mirror -> CriteriaTriggers.CONSUME_ITEM.trigger(mirror, food));
    }

    public static void killedEntity(EntityMaid maid, Entity victim, DamageSource source) {
        fire(maid, mirror -> CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(mirror, victim, source));
    }

    public static void killedByEntity(EntityMaid maid, Entity killer, DamageSource source) {
        fire(maid, mirror -> CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger(mirror, killer, source));
    }

    public static void hurtEntity(
            EntityMaid maid,
            Entity victim,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    ) {
        fire(maid, mirror -> CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(
                mirror,
                victim,
                source,
                dealt,
                taken,
                blocked
        ));
    }

    public static void hurtByEntity(
            EntityMaid maid,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    ) {
        fire(maid, mirror -> CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(
                mirror,
                source,
                dealt,
                taken,
                blocked
        ));
    }

    public static void changedDimension(EntityMaid maid, ResourceKey<Level> from, ResourceKey<Level> to) {
        fire(maid, mirror -> CriteriaTriggers.CHANGED_DIMENSION.trigger(mirror, from, to));
    }

    public static void netherTravel(EntityMaid maid, Vec3 enteredNetherPosition) {
        fire(maid, mirror -> CriteriaTriggers.NETHER_TRAVEL.trigger(mirror, enteredNetherPosition));
    }

    /** 女仆放下方块。TLM 直接调 {@code BlockItem#place}，Forge 的放置事件根本不发，只能靠 mixin 送进来。 */
    public static void placedBlock(EntityMaid maid, BlockPos placed, ItemStack used) {
        fire(maid, mirror -> {
            CriteriaTriggers.PLACED_BLOCK.trigger(mirror, placed, used);
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(mirror, placed, used);
        });
    }

    public static void usingItem(EntityMaid maid, ItemStack using) {
        fire(maid, mirror -> CriteriaTriggers.USING_ITEM.trigger(mirror, using));
    }

    /**
     * 女仆钓上东西。原版触发器只接受原版的浮漂实体（内部只用它取被钩住的实体），
     * 而 TLM 的浮漂不是它的子类，所以在钩点造一个不入世界的替身喂进去。
     */
    public static void fishingRodHooked(EntityMaid maid, ItemStack rod, Vec3 hookPosition, List<ItemStack> loot) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        FishingHook stand = new FishingHook(EntityType.FISHING_BOBBER, level);
        stand.moveTo(hookPosition.x, hookPosition.y, hookPosition.z);
        fire(maid, mirror -> CriteriaTriggers.FISHING_ROD_HOOKED.trigger(mirror, rod, stand, loot));
    }

    public static void fellFromHeight(EntityMaid maid, Vec3 fallStart) {
        fire(maid, mirror -> CriteriaTriggers.FALL_FROM_HEIGHT.trigger(mirror, fallStart));
    }

    public static void toolDurabilityChanged(EntityMaid maid, ItemStack tool, int damage) {
        fire(maid, mirror -> CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(mirror, tool, damage));
    }

    public static void enteredBlock(EntityMaid maid, BlockState state) {
        fire(maid, mirror -> CriteriaTriggers.ENTER_BLOCK.trigger(mirror, state));
    }

    public static void slidDownBlock(EntityMaid maid, BlockState state) {
        fire(maid, mirror -> CriteriaTriggers.HONEY_BLOCK_SLIDE.trigger(mirror, state));
    }

    public static void recipeCrafted(EntityMaid maid, ResourceLocation recipe, List<ItemStack> ingredients) {
        fire(maid, mirror -> CriteriaTriggers.RECIPE_CRAFTED.trigger(mirror, recipe, ingredients));
    }

    public static void bredAnimals(EntityMaid maid, Animal parent, Animal partner, @Nullable AgeableMob child) {
        fire(maid, mirror -> CriteriaTriggers.BRED_ANIMALS.trigger(mirror, parent, partner, child));
    }

    /**
     * 玩家亲手喂食：先记进统计量，再让阈值类条件按更新后的累计次数判定。
     */
    public static void fed(EntityMaid maid, ItemStack food) {
        if (food.isEmpty() || maid.level().isClientSide()) {
            return;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(food.getItem());
        MaidStatistics statistics = MaidStatisticsData.get(maid).withFeed(item);
        MaidStatisticsData.set(maid, statistics);
        ItemStack fed = food.copyWithCount(1);
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_FED.trigger(mirror, fed, statistics));
    }

    public static void level(EntityMaid maid, int level) {
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_LEVEL.trigger(mirror, level));
    }

    public static void favorabilityLevel(EntityMaid maid, int level) {
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_FAVORABILITY_LEVEL.trigger(mirror, level));
    }

    /**
     * 女仆入账等级经验：统计量累加后判定累计阈值。
     */
    public static void experienceGained(EntityMaid maid, int amount) {
        if (amount <= 0 || maid.level().isClientSide()) {
            return;
        }
        MaidStatistics statistics = MaidStatisticsData.get(maid).withExperience(amount);
        MaidStatisticsData.set(maid, statistics);
        fire(maid, mirror -> MaidCriteriaTriggers.MAID_EXPERIENCE.trigger(mirror, statistics));
    }

    /** 定时对账用：等级与好感等级按当前值重放，用指令改过的数值也能算数。 */
    public static void replaceStanding(EntityMaid maid, int level) {
        MaidStatistics statistics = MaidStatisticsData.get(maid);
        fire(maid, mirror -> {
            MaidCriteriaTriggers.MAID_LEVEL.trigger(mirror, level);
            MaidCriteriaTriggers.MAID_FAVORABILITY_LEVEL.trigger(
                    mirror,
                    maid.getFavorabilityManager().getLevel()
            );
            MaidCriteriaTriggers.MAID_EXPERIENCE.trigger(mirror, statistics);
        });
    }
}
