package com.laixia.maidintelligence.feature.level;

import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.advancement.server.MaidCriteria;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.client.LevelGuiHandler;
import com.laixia.maidintelligence.feature.level.command.LevelCommands;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.event.LevelExperienceHandler;
import com.laixia.maidintelligence.feature.level.service.DefaultMaidLevelService;
import com.laixia.maidintelligence.feature.level.tlm.LevelTaskData;
import com.laixia.maidintelligence.feature.level.tlm.TlmMaidLevelStore;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public final class LevelFeature implements FeatureModule, TlmFeatureModule {
    public static final LevelFeature INSTANCE = new LevelFeature();

    private final MaidLevelApi levelApi = new DefaultMaidLevelService(
            new TlmMaidLevelStore(),
            DefaultLevelCurve.INSTANCE,
            LevelFeature::onLevelUp
    );
    private boolean initialized;

    private LevelFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;

        context.gameEventBus().register(new LevelExperienceHandler(levelApi));
        context.gameEventBus().addListener(new LevelCommands(levelApi)::onRegisterCommands);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> context.gameEventBus().register(new LevelGuiHandler(levelApi))
        );
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        LevelTaskData.register(register);
    }

    public MaidLevelApi api() {
        return levelApi;
    }

    private static void onLevelUp(EntityMaid maid, int oldLevel, int newLevel) {
        ModNetwork.sendLevelUp(maid, oldLevel, newLevel);
        MaidCriteria.level(maid, newLevel);
    }
}
