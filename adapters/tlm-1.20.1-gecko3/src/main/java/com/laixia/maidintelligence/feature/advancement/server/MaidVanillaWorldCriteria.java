package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidWorldAdvancementTriggers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Instance-scoped adapter for vanilla inventory, world and activity criteria.
 */
public final class MaidVanillaWorldCriteria
        implements MaidWorldAdvancementTriggers {
    private final MaidAdvancementManager manager;

    public MaidVanillaWorldCriteria(MaidAdvancementManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    private void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        manager.fire(maid, action);
    }

    @Override
    public void inventoryChanged(EntityMaid maid, ItemStack changed) {
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
    @Override
    public void inventoryChanged(EntityMaid maid) {
        fire(maid, mirror -> {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(
                    mirror,
                    mirror.getInventory(),
                    ItemStack.EMPTY
            );
            for (ItemStack stack : mirror.getInventory().items) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(
                            mirror,
                            mirror.getInventory(),
                            stack
                    );
                }
            }
            for (ItemStack stack : mirror.getInventory().armor) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(
                            mirror,
                            mirror.getInventory(),
                            stack
                    );
                }
            }
            for (ItemStack stack : mirror.getInventory().offhand) {
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(
                            mirror,
                            mirror.getInventory(),
                            stack
                    );
                }
            }
        });
    }

    /** 对齐原版玩家每 20 tick 一次的位置判定。 */
    @Override
    public void location(EntityMaid maid) {
        fire(maid, CriteriaTriggers.LOCATION::trigger);
    }

    @Override
    public void effectsChanged(EntityMaid maid, @Nullable Entity source) {
        fire(maid, mirror -> CriteriaTriggers.EFFECTS_CHANGED.trigger(
                mirror,
                source
        ));
    }

    @Override
    public void consumedItem(EntityMaid maid, ItemStack food) {
        fire(maid, mirror -> CriteriaTriggers.CONSUME_ITEM.trigger(
                mirror,
                food
        ));
    }

    @Override
    public void changedDimension(
            EntityMaid maid,
            ResourceKey<Level> from,
            ResourceKey<Level> to
    ) {
        fire(maid, mirror -> CriteriaTriggers.CHANGED_DIMENSION.trigger(
                mirror,
                from,
                to
        ));
    }

    @Override
    public void netherTravel(
            EntityMaid maid,
            Vec3 enteredNetherPosition
    ) {
        fire(maid, mirror -> CriteriaTriggers.NETHER_TRAVEL.trigger(
                mirror,
                enteredNetherPosition
        ));
    }

    /**
     * 女仆放下方块。TLM 直接调 {@code BlockItem#place}，Forge 的放置事件根本不发，
     * 只能靠 mixin 送进来。
     */
    @Override
    public void placedBlock(
            EntityMaid maid,
            BlockPos placed,
            ItemStack used
    ) {
        fire(maid, mirror -> {
            CriteriaTriggers.PLACED_BLOCK.trigger(mirror, placed, used);
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(
                    mirror,
                    placed,
                    used
            );
        });
    }

    @Override
    public void usingItem(EntityMaid maid, ItemStack using) {
        fire(maid, mirror -> CriteriaTriggers.USING_ITEM.trigger(
                mirror,
                using
        ));
    }

    /**
     * 女仆钓上东西。原版触发器只接受原版的浮漂实体（内部只用它取被钩住的实体），
     * 而 TLM 的浮漂不是它的子类，所以在钩点造一个不入世界的替身喂进去。
     */
    @Override
    public void fishingRodHooked(
            EntityMaid maid,
            ItemStack rod,
            Vec3 hookPosition,
            List<ItemStack> loot
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        FishingHook stand = new FishingHook(
                EntityType.FISHING_BOBBER,
                level
        );
        stand.moveTo(
                hookPosition.x,
                hookPosition.y,
                hookPosition.z
        );
        fire(maid, mirror -> CriteriaTriggers.FISHING_ROD_HOOKED.trigger(
                mirror,
                rod,
                stand,
                loot
        ));
    }

    @Override
    public void fellFromHeight(EntityMaid maid, Vec3 fallStart) {
        fire(maid, mirror -> CriteriaTriggers.FALL_FROM_HEIGHT.trigger(
                mirror,
                fallStart
        ));
    }

    @Override
    public void toolDurabilityChanged(
            EntityMaid maid,
            ItemStack tool,
            int damage
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(
                        mirror,
                        tool,
                        damage
                ));
    }

    @Override
    public void enteredBlock(EntityMaid maid, BlockState state) {
        fire(maid, mirror -> CriteriaTriggers.ENTER_BLOCK.trigger(
                mirror,
                state
        ));
    }

    @Override
    public void slidDownBlock(EntityMaid maid, BlockState state) {
        fire(maid, mirror -> CriteriaTriggers.HONEY_BLOCK_SLIDE.trigger(
                mirror,
                state
        ));
    }

    @Override
    public void recipeCrafted(
            EntityMaid maid,
            ResourceLocation recipe,
            List<ItemStack> ingredients
    ) {
        fire(maid, mirror -> CriteriaTriggers.RECIPE_CRAFTED.trigger(
                mirror,
                recipe,
                ingredients
        ));
    }

    @Override
    public void bredAnimals(
            EntityMaid maid,
            Animal parent,
            Animal partner,
            @Nullable AgeableMob child
    ) {
        fire(maid, mirror -> CriteriaTriggers.BRED_ANIMALS.trigger(
                mirror,
                parent,
                partner,
                child
        ));
    }
}
