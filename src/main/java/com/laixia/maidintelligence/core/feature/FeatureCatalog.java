package com.laixia.maidintelligence.core.feature;

import com.laixia.maidintelligence.compat.tlm.TlmFeatureModule;
import com.laixia.maidintelligence.feature.interaction.MaidInteractionFeature;
import com.laixia.maidintelligence.feature.level.LevelFeature;

import java.util.List;

/**
 * 唯一的特性组合根，Forge 与 TLM 入口共享此清单。
 */
public final class FeatureCatalog {
    private static final List<FeatureModule> FEATURES = List.of(
            LevelFeature.INSTANCE,
            MaidInteractionFeature.INSTANCE
    );
    private static final List<TlmFeatureModule> TLM_FEATURES = FEATURES.stream()
            .filter(TlmFeatureModule.class::isInstance)
            .map(TlmFeatureModule.class::cast)
            .toList();

    private FeatureCatalog() {
    }

    public static List<FeatureModule> all() {
        return FEATURES;
    }

    public static List<TlmFeatureModule> tlmFeatures() {
        return TLM_FEATURES;
    }
}
