package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldLedger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 她到底有没有挡下来过。
 *
 * <p>{@link ShieldLedger} 其余每一列量的都是**前提**：盾在不在副手、举没举起来、
 * 身体朝没朝对。这些全部成立仍然可能一次都没挡到——对方绕到侧面、伤害类型绕过
 * 盾牌、盾正在冷却里——而在那些列上，"挡住了"和"举着但没挡住"长得一模一样。
 *
 * <p>所以必须单独数这个事件。用 Forge 的 {@link ShieldBlockEvent}：它只在原版
 * {@code isDamageSourceBlocked} 判定成立之后触发，是"这一下真的被挡住了"唯一会
 * 走到的地方。**不是按 tick 采样**——靶场早先把每局四十二次起跳读成一次挥刀，
 * 就是采样采出来的。
 *
 * <p>放在测试支撑层而不是生产路径上，因为它是量具：生产侧不需要知道自己挡了
 * 几下。注册与注销由基准局负责，`AdvancementGameTests` 的探针是同一个形状。
 */
public final class ShieldBlockProbe {
    private ShieldBlockProbe() {
    }

    /** 装上探针；同一轮里重复注册是无害的，Forge 会忽略同一实例。 */
    public static void install() {
        MinecraftForge.EVENT_BUS.register(ShieldBlockProbe.class);
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            ShieldLedger.noteBlocked(maid, event.getBlockedDamage());
        }
    }
}
