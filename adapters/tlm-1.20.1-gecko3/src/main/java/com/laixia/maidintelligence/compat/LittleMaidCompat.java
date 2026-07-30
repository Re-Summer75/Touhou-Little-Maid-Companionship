package com.laixia.maidintelligence.compat;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.overlay.MaidTipsOverlay;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.compat.tlm.TlmAdapterRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * “TLM：朝夕相伴”的本体兼容入口。后续的工作模式、AI 行为和互动功能统一从这里注册。
 */
@LittleMaidExtension
public final class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        TlmAdapterRegistry.modules().forEach(feature -> feature.registerTaskData(register));
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        TlmAdapterRegistry.modules().forEach(feature -> feature.registerTasks(manager));
    }

    @Override
    public void addExtraMaidBrain(ExtraMaidBrainManager manager) {
        TlmAdapterRegistry.modules().forEach(feature -> feature.registerExtraBrain(manager));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addMaidTips(MaidTipsOverlay overlay) {
        TlmAdapterRegistry.modules().forEach(feature -> feature.registerMaidTips(overlay));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addAdditionMaidLayer(
            EntityMaidRenderer renderer,
            EntityRendererProvider.Context context
    ) {
        TlmAdapterRegistry.modules().forEach(feature -> feature.registerMaidLayer(renderer, context));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addAdditionGeckoMaidLayer(
            GeckoEntityMaidRenderer<? extends Mob> renderer,
            EntityRendererProvider.Context context
    ) {
        TlmAdapterRegistry.modules().forEach(feature ->
                feature.registerGeckoMaidLayer(renderer, context)
        );
    }
}
