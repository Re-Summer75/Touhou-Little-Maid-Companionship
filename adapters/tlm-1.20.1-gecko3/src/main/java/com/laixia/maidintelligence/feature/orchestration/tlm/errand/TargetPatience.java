package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 盯不动的目标要放手，放过手的一阵不再看——每个逐件干活的差事都需要这一套。
 *
 * <p>从拾物差事里提炼出来，因为它的每一条规则都是在那儿用读数买的，而下一个
 * 走路型行为（耕作、伐木、送货）不该重买一遍：
 *
 * <ul>
 *   <li>**不放手会挂死整趟**：清扫每 tick 重挑最近的一件，够不着的那件每次都被
 *       挑中——实测最后一件卡住之后她原地站了四百二十 tick。</li>
 *   <li>**只记一件放弃的会来回横跳**：放弃 A 去够 B、B 也够不着时把 A 忘了，
 *       下一 tick 最近的又是 A——实测二十件只收到十三件。</li>
 *   <li>**寻路失败必须核对失败的是谁**：{@code CANT_REACH_WALK_TARGET_SINCE}
 *       没有 TTL，只在下一次成功写入移动目标时被清掉，别的差事撞墙留下的旧记忆
 *       能一直躺着。不核对就是死亡螺旋：陈旧记忆让每次挑目标都当场拉黑最近的
 *       候选，四个连着拉黑之后交白卷、差事失败、**没有任何写入去清掉那条记忆**，
 *       下一 tick 重演——实测她收了七件之后对着满地东西一动不动。</li>
 * </ul>
 */
public final class TargetPatience {
    /**
     * 盯同一件多久还没成，就当它做不成。
     *
     * <p>五秒：够她绕过一堵矮墙，不够她对着一件永远做不成的事发呆。
     */
    private static final int GIVE_UP_TICKS = 100;

    /**
     * 放弃之后多久愿意再看它一眼。
     *
     * <p>不是永久拉黑：世界会变——水把它冲过来、玩家把墙拆了、她自己换了个位置
     * 就有路了。
     */
    private static final int RECONSIDER_TICKS = 600;

    /**
     * 同时记几件放弃过的。八件：够覆盖一个角落里挤着的一小堆，又不至于把一整片
     * 地都拉黑。满了挤掉最早的，而每一条本来也会到期。
     */
    private static final int SHUNNED_LIMIT = 8;

    private final Map<EntityMaid, Attempt> attempts = new WeakHashMap<>();

    /**
     * 这一件此刻还值不值得当目标。
     *
     * <p>值得就顺手把它记成"正在盯的那件"（换目标即重新计时）；不值得的原因有
     * 两种——刚放弃过它，或这一刻起放弃它（盯满了、或导航证实走不到）。
     *
     * @param trustNavFailure 这一件的"寻路失败"可不可信。要跳才够得着的目标不可
     *                        信——它本来就没有路，路在跳里，见拾物的起跳一节。
     */
    public boolean worthTrying(
            EntityMaid maid,
            Entity candidate,
            long gameTime,
            boolean trustNavFailure
    ) {
        Attempt attempt = attempts.computeIfAbsent(
                maid, ignored -> new Attempt()
        );
        if (attempt.shunned(candidate, gameTime)) {
            return false;
        }
        boolean provenUnreachable = trustNavFailure
                && navFailedOn(maid, candidate);
        if (attempt.stalledOn(candidate, gameTime, provenUnreachable)) {
            attempt.giveUp(candidate, gameTime);
            return false;
        }
        attempt.watch(candidate, gameTime);
        return true;
    }

    /**
     * 导航说走不到，而且失败的确实是这一件。
     *
     * <p>核对靠她此刻的移动目标：真的指着这一件，失败才归它。
     */
    private static boolean navFailedOn(EntityMaid maid, Entity candidate) {
        if (!maid.getBrain().hasMemoryValue(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        )) {
            return false;
        }
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget()
                        .currentPosition()
                        .closerThan(candidate.position(), 2.0D))
                .orElse(false);
    }

    private static final class Attempt {
        private final Map<Integer, Long> shunned = new LinkedHashMap<>();
        private int target;
        private long since;

        void watch(Entity candidate, long gameTime) {
            if (target != candidate.getId()) {
                target = candidate.getId();
                since = gameTime;
            }
        }

        boolean shunned(Entity candidate, long gameTime) {
            Long until = shunned.get(candidate.getId());
            if (until == null) {
                return false;
            }
            if (gameTime >= until) {
                shunned.remove(candidate.getId());
                return false;
            }
            return true;
        }

        boolean stalledOn(
                Entity candidate,
                long gameTime,
                boolean provenUnreachable
        ) {
            return candidate.getId() == target
                    && (provenUnreachable
                    || gameTime - since >= GIVE_UP_TICKS);
        }

        void giveUp(Entity candidate, long gameTime) {
            if (shunned.size() >= SHUNNED_LIMIT) {
                var oldest = shunned.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            shunned.put(candidate.getId(), gameTime + RECONSIDER_TICKS);
            target = 0;
            since = gameTime;
        }
    }
}
