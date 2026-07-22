package com.laixia.maidintelligence.feature.status;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.ExtraMaidBrainManager;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.event.MaidFoodStatusHandler;
import com.laixia.maidintelligence.feature.status.service.DefaultMaidStatusService;
import com.laixia.maidintelligence.feature.status.service.MaidActionService;
import com.laixia.maidintelligence.feature.status.service.MaidExpressionService;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.StatusExtraBrain;
import com.laixia.maidintelligence.feature.status.tlm.StatusTaskData;
import com.laixia.maidintelligence.feature.status.tlm.TlmMaidStatusStore;

public final class StatusFeedbackFeature implements FeatureModule, TlmFeatureModule {
    public static final StatusFeedbackFeature INSTANCE = new StatusFeedbackFeature();

    private final DefaultMaidStatusService statusService = new DefaultMaidStatusService(
            new TlmMaidStatusStore(),
            DefaultHungerPolicy.INSTANCE,
            new ToolReplacementService(DefaultToolDurabilityPolicy.INSTANCE),
            new MaidMealAccess(),
            new MaidExpressionService(),
            new MaidActionService()
    );
    private boolean initialized;

    private StatusFeedbackFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        context.gameEventBus().register(new MaidFoodStatusHandler(statusService));
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        StatusTaskData.register(register);
    }

    @Override
    public void registerExtraBrain(ExtraMaidBrainManager manager) {
        manager.addExtraMaidBrain(new StatusExtraBrain(statusService));
    }

    public MaidStatusApi api() {
        return statusService;
    }
}
