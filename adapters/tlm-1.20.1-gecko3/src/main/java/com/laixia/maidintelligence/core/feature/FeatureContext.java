package com.laixia.maidintelligence.core.feature;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 特性初始化时可使用的 Forge 基础设施。
 */
public record FeatureContext(IEventBus modEventBus, IEventBus gameEventBus) {
}
