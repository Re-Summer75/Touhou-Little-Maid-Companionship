package com.laixia.maidintelligence.feature.advancement.bridge;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Reports inventory, world and activity facts to vanilla advancement criteria.
 */
public interface MaidWorldAdvancementTriggers {
    void inventoryChanged(EntityMaid maid, ItemStack changed);

    void inventoryChanged(EntityMaid maid);

    void location(EntityMaid maid);

    void effectsChanged(EntityMaid maid, @Nullable Entity source);

    void consumedItem(EntityMaid maid, ItemStack food);

    void changedDimension(
            EntityMaid maid,
            ResourceKey<Level> from,
            ResourceKey<Level> to
    );

    void netherTravel(EntityMaid maid, Vec3 enteredNetherPosition);

    void placedBlock(EntityMaid maid, BlockPos placed, ItemStack used);

    void usingItem(EntityMaid maid, ItemStack using);

    void fishingRodHooked(
            EntityMaid maid,
            ItemStack rod,
            Vec3 hookPosition,
            List<ItemStack> loot
    );

    void fellFromHeight(EntityMaid maid, Vec3 fallStart);

    void toolDurabilityChanged(EntityMaid maid, ItemStack tool, int damage);

    void enteredBlock(EntityMaid maid, BlockState state);

    void slidDownBlock(EntityMaid maid, BlockState state);

    void recipeCrafted(
            EntityMaid maid,
            ResourceLocation recipe,
            List<ItemStack> ingredients
    );

    void bredAnimals(
            EntityMaid maid,
            Animal parent,
            Animal partner,
            @Nullable AgeableMob child
    );
}
