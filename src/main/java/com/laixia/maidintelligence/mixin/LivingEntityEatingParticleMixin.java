package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEatingParticleMixin {
    @Inject(method = "spawnItemParticles", at = @At("HEAD"), cancellable = true)
    private void maidIntelligence$suppressOriginalMaidEatingParticles(
            ItemStack stack,
            int amount,
            CallbackInfo callback
    ) {
        if ((Object) this instanceof EntityMaid
                && stack.getUseAnimation() == UseAnim.EAT) {
            callback.cancel();
        }
    }
}
