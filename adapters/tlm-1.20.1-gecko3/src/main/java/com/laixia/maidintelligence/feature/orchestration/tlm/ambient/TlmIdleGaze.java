package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.IdleGazePolicy;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 东张西望。
 *
 * <p>只在她**没有行程**时上岗：脚被走路或战斗占着的时候，眼睛归路和对手，这里
 * 一个字不写（见 {@code tick} 里的让位）。环顾因此是空闲时的默认值而不是决定，
 * 不需要谁去协调，也不会和别的写入者抢同一块记忆。
 *
 * <p>一眼要**记住**，理由和游走落点完全一样：每 tick 重挑一个方向不是环顾，是抽搐。
 * 这条契约在差事骨架那边是写下来的（{@code Errand#find} 的稳定性），这里没有骨架
 * 替她记，所以自己记。
 */
public final class TlmIdleGaze {
    /** 找活物看时一次看几只。 */
    private static final int NEARBY_CANDIDATES = 6;

    private final Map<EntityMaid, Glance> glances = new WeakHashMap<>();

    /**
     * 这一眼看着什么，看到什么时候。
     *
     * <p>看活物时记的是那只活物；看方向时记的是**方位与抬头量**，而不是世界里的
     * 一个点。差别在她走起来之后才显出来：固定的点会随着她的移动不断改变角度，
     * 走过头就得回头看，而原版会钳制头部偏航——表现是猛地一甩。上个台阶之后那个
     * 点低于她的视线，表现是盯着地面。
     *
     * <p>记方位则两样都不会发生：每 tick 以她**当下**的位置和视线高度重新算一次，
     * 于是"往左前方看着"在她走动时保持成立，正是一个人边走边张望的样子。
     */
    private record Glance(
            LivingEntity at,
            double bearing,
            double lift,
            long until
    ) {
    }

    /** 每 tick 一次，由 {@code MaidIntentBehavior} 的 preTick 驱动。 */
    public void tick(EntityMaid maid, long gameTime) {
        if (!maid.isAlive() || maid.isSleeping()) {
            return;
        }
        // 在路上不写：脚被行程占着的时候，眼睛默认归路。
        //
        // 类注释曾说"任何有事做的意图都会盖掉这一眼"，实际不成立：差事只在写入
        // **新**路线的那一 tick 顺手写一次注视（alreadyHeading 提前返回），而这里
        // 每 tick 都在覆写——于是整段行走期间她的头归闲看管，四成时间扭头盯着
        // 主人走路，实机看着极不自然。行程写下的注视目标（EntityTracker 会跟着
        // 移动的目的地走）留在记忆里不动，正好就是"看着要去的地方"。
        // 打架同理，脸归战斗的 face 管，这里不去抢。
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || maid.getBrain().hasMemoryValue(
                        MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        Glance current = glances.get(maid);
        if (current == null || gameTime >= current.until()
                || (current.at() != null && !current.at().isAlive())) {
            current = pick(maid, gameTime);
            glances.put(maid, current);
        }
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET, tracker(maid, current)
        );
    }

    /** 把这一眼折算成此刻的一个注视点。 */
    private static PositionTracker tracker(EntityMaid maid, Glance glance) {
        if (glance.at() != null) {
            return new EntityTracker(glance.at(), true);
        }
        IdleGazePolicy policy = IdleGazePolicy.INSTANCE;
        Vec3 here = maid.position();
        return new BlockPosTracker(new Vec3(
                here.x + policy.offsetX(glance.bearing()),
                maid.getEyeY() + glance.lift(),
                here.z + policy.offsetZ(glance.bearing())
        ));
    }

    /** 挑一眼：主人、附近某个活物，或者随便一个方向。 */
    private static Glance pick(EntityMaid maid, long gameTime) {
        IdleGazePolicy policy = IdleGazePolicy.INSTANCE;
        RandomSource random = maid.getRandom();
        long until = gameTime + policy.glanceTicks(random.nextDouble());

        LivingEntity owner = maid.getOwner();
        if (owner != null && owner.isAlive()
                && policy.looksAtOwner(random.nextDouble())) {
            return new Glance(owner, 0.0D, 0.0D, until);
        }
        // 与上面用的是不同的随机数：共用一个的话"不看主人"就恒等于"看活物"。
        if (policy.looksAtCreature(random.nextDouble())) {
            LivingEntity creature = nearby(maid, random);
            if (creature != null) {
                return new Glance(creature, 0.0D, 0.0D, until);
            }
        }
        return new Glance(
                null,
                random.nextDouble(),
                policy.offsetY(random.nextDouble()),
                until
        );
    }

    /**
     * 附近随便一只**看得见**的活物，没有则为 {@code null}。
     *
     * <p>读的是传感器已经填好的可见实体记忆，不自己扫世界：这一条每 tick 都跑，而
     * 它买到的只是一个转头方向，不值一次实体查询。
     *
     * <p><b>视线不必在这里再问一遍——量过。</b>"她会不会盯着墙那边（多半是脚下岩洞
     * 里）的一只东西"是一个很像真凶的猜想：那样的注视点就在正下方，表现正是"周围
     * 没有生物却低头看地板"。但这份记忆的 {@code findAll} 本身就带视线过滤，
     * 把显式检查去掉之后 {@code sheDoesNotStareThroughAWall} 照样绿。
     *
     * <p>所以这条性质由那条测试钉住，而不是由这里再写一遍判据。宿主或原版哪天不再
     * 过滤，红的是那条测试——那正是它该做的事。
     */
    private static LivingEntity nearby(EntityMaid maid, RandomSource random) {
        var visible = maid.getBrain()
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visible.isEmpty()) {
            return null;
        }
        List<LivingEntity> found = new ArrayList<>(NEARBY_CANDIDATES);
        for (LivingEntity candidate : visible.get().findAll(
                other -> other != maid
                        && other.isAlive()
                        // 脚边那只不算：低头盯着脚下一只鸡就是"看向地面"，
                        // 而那是这一条要避免的两个症状之一。
                        && maid.distanceTo(other)
                                >= IdleGazePolicy.NEAREST_GLANCE
                        && maid.distanceTo(other)
                                <= IdleGazePolicy.GLANCE_RANGE
        )) {
            found.add(candidate);
            if (found.size() >= NEARBY_CANDIDATES) {
                break;
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        // 敌人也在候选里，而且应该在：盯着一只正在靠近的僵尸是完全合理的一眼。
        // 真打起来之后，交战那一侧会用自己的注视目标盖掉这里。
        return found.get(random.nextInt(found.size()));
    }

    private TlmIdleGaze() {
    }

    /** 一个实例服务全部女仆；状态按女仆分。 */
    public static TlmIdleGaze create() {
        return new TlmIdleGaze();
    }
}
