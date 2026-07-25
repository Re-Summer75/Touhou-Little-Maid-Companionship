package com.laixia.maidintelligence.feature.advancement;

import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.advancement.client.AdvancementClientSetup;
import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.feature.advancement.event.MaidAdvancementBridgeHandlers;
import com.laixia.maidintelligence.feature.advancement.event.MaidAdvancementLifecycleHandlers;
import com.laixia.maidintelligence.feature.advancement.menu.AdvancementMenus;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementManager;
import com.laixia.maidintelligence.feature.advancement.server.MaidBridgeMemory;
import com.laixia.maidintelligence.feature.advancement.tlm.LegacyAchievementData;
import com.laixia.maidintelligence.feature.advancement.tlm.MaidStatisticsData;
import com.laixia.maidintelligence.feature.level.LevelFeature;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 女仆的原版进度系统：每只女仆拥有独立的 {@code PlayerAdvancements}，
 * 由一个镜像玩家把女仆行为喂进原版触发器，因此玩家能拿的进度女仆同样能拿。
 * <p>
 * 本模块自带的四个触发器（喂食、等级、好感等级、累计经验）让阈值类条件也能写成
 * 普通的 advancement JSON，附属模组直接发数据包即可扩展。
 */
public final class MaidAdvancementFeature implements FeatureModule, TlmFeatureModule {
    public static final MaidAdvancementFeature INSTANCE = new MaidAdvancementFeature();

    private final MaidAdvancementManager manager = new MaidAdvancementManager();
    private final MaidBridgeMemory bridgeMemory = new MaidBridgeMemory();
    private boolean initialized;

    private MaidAdvancementFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;

        AdvancementMenus.register(context.modEventBus());
        context.modEventBus().addListener(this::onCommonSetup);
        context.gameEventBus().register(new MaidAdvancementLifecycleHandlers(manager, bridgeMemory));
        context.gameEventBus().register(new MaidAdvancementBridgeHandlers(
                bridgeMemory,
                LevelFeature.INSTANCE.api()
        ));
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> AdvancementClientSetup.register(context)
        );
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        MaidStatisticsData.register(register);
        LegacyAchievementData.register(register);
    }

    public MaidAdvancementManager manager() {
        return manager;
    }

    public MaidBridgeMemory bridgeMemory() {
        return bridgeMemory;
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 触发器注册表不是线程安全的，且必须早于任何数据包解析。
        event.enqueueWork(MaidCriteriaTriggers::register);
    }
}
