package com.laixia.maidintelligence.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 特性与车万女仆注册系统之间的窄适配接口。
 */
public interface TlmFeatureModule {
    default void registerTaskData(TaskDataRegister register) {
    }

    default void registerTasks(TaskManager manager) {
    }

    default void registerExtraBrain(ExtraMaidBrainManager manager) {
    }

    @OnlyIn(Dist.CLIENT)
    default void registerMaidTips(MaidTipsOverlay overlay) {
    }

    @OnlyIn(Dist.CLIENT)
    default void registerMaidLayer(
            EntityMaidRenderer renderer,
            EntityRendererProvider.Context context
    ) {
    }

    @OnlyIn(Dist.CLIENT)
    default void registerGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
    }
}
