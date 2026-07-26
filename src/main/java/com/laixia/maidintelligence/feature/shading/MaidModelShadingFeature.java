package com.laixia.maidintelligence.feature.shading;

import com.laixia.maidintelligence.core.feature.FeatureContext;
import com.laixia.maidintelligence.core.feature.FeatureModule;
import com.laixia.maidintelligence.feature.shading.client.ShadingClientSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 根据实际顶点为 Gecko 每个立方体逐面重算外法线与外向绕序。
 * 算法不依赖模型、骨骼、光影包名称或立方体之间的穿插关系。
 */
public final class MaidModelShadingFeature implements FeatureModule {
    public static final MaidModelShadingFeature INSTANCE = new MaidModelShadingFeature();

    private boolean initialized;

    private MaidModelShadingFeature() {
    }

    @Override
    public void initialize(FeatureContext context) {
        if (initialized) {
            return;
        }
        initialized = true;
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ShadingClientSetup.initialize(
                        context.modEventBus()
                )
        );
    }
}
