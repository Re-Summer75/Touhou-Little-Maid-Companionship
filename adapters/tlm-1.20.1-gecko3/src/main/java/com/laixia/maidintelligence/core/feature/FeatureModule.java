package com.laixia.maidintelligence.core.feature;

/**
 * 一个垂直业务特性的 Forge 生命周期入口。
 */
public interface FeatureModule {
    void initialize(FeatureContext context);
}
