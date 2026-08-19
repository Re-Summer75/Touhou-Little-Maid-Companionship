package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 静止黑匣子：她十秒纹丝不动，就把她的全部状态倒进日志。
 *
 * <p>站桩匣子（{@code LipRescue} 里那只）要求"脑子里有走目标"才计数，而
 * 玩家稳定复现的战后瘫痪是**意图全被挡、没人写走目标**的形态——那只匣子
 * 对它失明。这只不设前提：不坐、不睡、不骑、没在打铁（有攻击目标且敌人
 * 在感知内算正常战斗站位），就只问一件事——她动没动。十秒不动一次供词：
 * 攻击记忆、走目标、导航状态、离主人多远，一行分诊。
 */
public final class TlmStillnessBox {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 纹丝不动多少 tick 算异常。十秒：没有任何正常生活会站这么久。 */
    private static final int STILL_TICKS = 200;

    /** 一 tick 挪不过这个数就算没动。 */
    private static final double STILL = 0.02D;

    private final Map<EntityMaid, Vec3> anchor = new WeakHashMap<>();
    private final Map<EntityMaid, Integer> stillFor = new WeakHashMap<>();
    private final Map<EntityMaid, Boolean> confessed = new WeakHashMap<>();

    private TlmStillnessBox() {
    }

    public static TlmStillnessBox create() {
        return new TlmStillnessBox();
    }

    /** 每 tick 一次。 */
    public void tick(EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose() || maid.isSleeping()
                || maid.isPassenger()) {
            reset(maid);
            return;
        }
        Vec3 now = maid.position();
        Vec3 last = anchor.put(maid, now);
        if (last == null || now.distanceTo(last) > STILL) {
            reset(maid);
            return;
        }
        int ticks = stillFor.merge(maid, 1, Integer::sum);
        if (ticks < STILL_TICKS || Boolean.TRUE.equals(confessed.get(maid))) {
            return;
        }
        confessed.put(maid, true);
        LivingEntity target = maid.getTarget();
        var walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        LivingEntity owner = maid.getOwner();
        LOGGER.warn(
                "[maid-stillness] {} frozen {}t at ({}, {}, {}) | "
                        + "attackMem={} target={} targetDist={} | walk={} | "
                        + "nav(done={}, to={}) | ownerDist={}",
                maid.getName().getString(),
                ticks,
                String.format("%.1f", maid.getX()),
                String.format("%.1f", maid.getY()),
                String.format("%.1f", maid.getZ()),
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .isPresent(),
                target == null ? "-" : target.getType().toShortString()
                        + "@" + target.blockPosition().toShortString()
                        + (target.isAlive() ? "" : "(dead)"),
                target == null ? "-"
                        : String.format("%.1f", maid.distanceTo(target)),
                walk == null ? "-"
                        : walk.getTarget().currentBlockPosition()
                                .toShortString(),
                maid.getNavigation().isDone(),
                maid.getNavigation().getTargetPos(),
                owner == null ? "-"
                        : String.format("%.1f", maid.distanceTo(owner))
        );
    }

    private void reset(EntityMaid maid) {
        stillFor.put(maid, 0);
        confessed.put(maid, false);
    }
}
