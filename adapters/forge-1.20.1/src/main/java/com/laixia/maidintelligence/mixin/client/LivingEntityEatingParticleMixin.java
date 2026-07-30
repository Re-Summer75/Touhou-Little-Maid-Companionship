package com.laixia.maidintelligence.mixin.client;

import com.laixia.maidintelligence.feature.interaction.bridge.EatingParticlePolicy;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
        if (AdapterRuntime.require(EatingParticlePolicy.class)
                .suppressVanillaParticles(
                        (LivingEntity) (Object) this,
                        stack
                )) {
            callback.cancel();
        }
    }
}
