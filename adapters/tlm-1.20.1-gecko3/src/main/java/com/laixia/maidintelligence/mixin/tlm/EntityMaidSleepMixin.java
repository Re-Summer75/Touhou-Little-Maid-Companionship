package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidFeatAdvancementTriggers;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女仆睡觉走的是宿主自己的 {@code EntityMaid#startSleeping}，不经过
 * {@code Player#startSleeping}，所以 Forge 的睡眠事件根本看不见她，
 * {@code slept_in_bed} 只能在这里接。
 * <p>
 * 挂 TLM 自己的类而不是原版的类：签名由 javap 核实过，属于本仓库既有的
 * mixin 类别（与 {@code EntityMaidPlaceBlockMixin} 同型）。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidSleepMixin {
    @Inject(method = "startSleeping", at = @At("RETURN"), remap = false)
    private void maidIntelligence$reportSleep(
            BlockPos bedPos,
            CallbackInfo callback
    ) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.level().isClientSide()) {
            return;
        }
        AdapterRuntime.require(MaidFeatAdvancementTriggers.class)
                .sleptInBed(maid);
    }
}
