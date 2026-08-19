package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * 攻击记忆的门房：指着尸体或感知外的记忆，每 tick 见一次清一次。
 *
 * <p>此前清理只在交战意图取消的**那一瞬**跑一次（{@code releaseHands} →
 * {@code forgetADeadTarget}），判据是"死了或出了感知半径"。玩家稳定复现
 * 打进了时序缝里：卫道士被推下高台的瞬间**还活着、还在感知内**（坠落途
 * 中），清理拒绝执行；它摔死在三十二格下是之后的事，而那时已经没人再来
 * 清了。静止黑匣子的供词一字不差：{@code attackMem=true target=vindicator
 * (dead) targetDist=32.1}——记忆从此永驻，差事资格全灭（每件差事都要求
 * 这条记忆为空），连东张西望都被它的门挡住：她"闲着"，却什么都不做、
 * 连头都不转，直到玩家手动更新她。
 *
 * <p>所以判据不变、时机升级：从"取消那一瞬"改成**每 tick**。活着且在感知
 * 内的目标照旧一根手指不碰——步骤超时不误清活目标的老教训（基准量过：
 * 无条件清会把僵尸局从全歼打回剩两只）原样保住。
 */
public final class TlmAttackMemoryJanitor {
    private TlmAttackMemoryJanitor() {
    }

    public static TlmAttackMemoryJanitor create() {
        return new TlmAttackMemoryJanitor();
    }

    /** 每 tick 一次。 */
    public void tick(EntityMaid maid, long gameTime) {
        LivingEntity held = maid.getTarget();
        if (held == null) {
            held = maid.getBrain()
                    .getMemory(MemoryModuleType.ATTACK_TARGET)
                    .orElse(null);
        }
        if (held == null) {
            return;
        }
        if (held.isAlive() && !held.isRemoved()
                && maid.distanceTo(held) <= PerceptionRange.BLOCKS) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.setTarget(null);
    }
}
