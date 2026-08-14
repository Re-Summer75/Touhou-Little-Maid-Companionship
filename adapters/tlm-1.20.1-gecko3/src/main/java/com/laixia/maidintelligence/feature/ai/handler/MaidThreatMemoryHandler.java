package com.laixia.maidintelligence.feature.ai.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatReachLedger;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 她挨的每一记近战，都被折算成"这个物种够多远"。
 *
 * <p>威胁的够到距离一律问它自己，而不守规矩的模组怪物什么也不会说——把攻击距离
 * 硬写在自己的 Goal 里、从不覆写 {@code getMeleeAttackRangeSqr} 的那些，从外面读到
 * 的永远只是它的碰撞箱宽度。外部唯一可读的信号就是这一记：**它确实从这么远打到了
 * 她**。记忆与褪去的规则在
 * {@link com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatReachMemory}。
 *
 * <p>用 {@code LivingAttackEvent} 而不是伤害事件，因为**挡下来的那一记同样是证据**
 * ——盾牌把伤害整个抹掉，而"它够得到"这件事和她挡没挡住无关。举着盾打一场仗本来
 * 会是她学得最多的一场，用伤害事件的话恰恰一条都学不到。
 */
public final class MaidThreatMemoryHandler {
    @SubscribeEvent
    public void onMaidAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide()) {
            return;
        }
        LivingEntity attacker = meleeAttacker(event.getSource());
        if (attacker == null || !ThreatProfile.isHostileTo(maid, attacker)) {
            // 主人误伤、火焰、掉落——都不是谁"够到"了她。
            return;
        }
        ThreatReachLedger.noteAHit(
                attacker,
                maid,
                // 与威胁采样用的是同一个量，否则学到的差额和用它的地方对不上。
                maid.distanceTo(attacker),
                maid.level().getGameTime()
        );
    }

    /**
     * 这一记是不是某个活物**亲手**打的，是就返回它。
     *
     * <p>两道筛，都不是配平：
     *
     * <ul>
     *   <li><b>直接实体必须就是攻击者本人。</b>弹射物的伤害来源里直接实体是那支箭，
     *       攻击者是射手——按射手当时的距离去学，学到的是"骷髅够十五格"。</li>
     *   <li><b>爆炸不算。</b>苦力怕的直接实体确实是它自己，但爆炸半径衰减能到七格；
     *       按近战距离记下来，她从此再也不敢靠近任何一个自爆的东西。自爆的定价是
     *       另一件事，不在这里。</li>
     * </ul>
     */
    private static LivingEntity meleeAttacker(DamageSource source) {
        if (source == null || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return null;
        }
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof LivingEntity attacker)
                || direct != source.getEntity()) {
            return null;
        }
        return attacker;
    }
}
