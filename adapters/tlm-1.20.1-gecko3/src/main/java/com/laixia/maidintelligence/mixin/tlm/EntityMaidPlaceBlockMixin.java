package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.api.MaidWorldAdvancementTriggers;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 女仆放方块走的是 {@code BlockItem#place}，既没有 TLM 事件也不会触发 Forge 的
 * {@code BlockEvent.EntityPlaceEvent}（那个只从 {@code ItemStack#useOn} 进来），
 * 所以 {@code placed_block} 与 {@code item_used_on_block} 只能在这里接。
 * <p>
 * 只挂四参数的真实实现，另外两个重载都只是转发，挂上去会重复上报。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidPlaceBlockMixin {
    @Inject(
            method = "placeItemBlock(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void maidIntelligence$reportPlacedBlock(
            InteractionHand hand,
            BlockPos placePos,
            Direction direction,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> callback
    ) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!Boolean.TRUE.equals(callback.getReturnValue()) || maid.level().isClientSide()) {
            return;
        }
        // 判定要看放好之后的方块，所以在 RETURN 而不是 HEAD 上报。
        AdapterRuntime.require(MaidWorldAdvancementTriggers.class)
                .placedBlock(maid, placePos, stack);
    }
}
