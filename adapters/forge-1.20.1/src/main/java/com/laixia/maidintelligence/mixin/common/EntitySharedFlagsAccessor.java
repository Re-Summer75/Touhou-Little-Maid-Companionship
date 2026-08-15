package com.laixia.maidintelligence.mixin.common;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 只翻那个同步标志位，不碰属性。
 *
 * <p>"跑步"在原版里是 {@code Entity} 的第 3 号共享标志位，客户端据此选动画。
 * {@code Entity.setSprinting} 做的就是翻它，而 {@code LivingEntity} 覆写了这个方法，
 * **额外挂上 {@code SPEED_MODIFIER_SPRINTING}——移动速度 +30%**。
 *
 * <p>这里要的只是那个动画。走覆写过的入口就等于顺手给她加了三成速度，而那是她本来
 * 没有的能力，会改掉整套追逃算术。所以直接调 {@code Entity} 那个受保护的写标志位方法：
 * 标志位照样同步到客户端，属性表一动不动。
 */
@Mixin(Entity.class)
public interface EntitySharedFlagsAccessor {
    @Invoker("setSharedFlag")
    void maidIntelligence$setSharedFlag(int flag, boolean value);
}
