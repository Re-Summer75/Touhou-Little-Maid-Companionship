package com.laixia.maidintelligence.feature.interaction;

import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.interaction.event.MaidInteractionHandler;

public final class MaidInteractionFeature implements FeatureModule {
    public static final MaidInteractionFeature INSTANCE = new MaidInteractionFeature();

    private boolean initialized;

    private MaidInteractionFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        context.gameEventBus().register(new MaidInteractionHandler());
    }
}
