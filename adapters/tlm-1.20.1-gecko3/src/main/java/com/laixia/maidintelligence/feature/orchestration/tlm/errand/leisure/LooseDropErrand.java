package com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .EntityApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.laixia.maidintelligence.feature.perception.tlm.TlmLooseDrop;
import com.laixia.maidintelligence.feature.perception.tlm
        .TlmAffordancePerceptionService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * 把地上的东西收起来，一件接一件。
 *
 * <p>本体早就会捡东西了——{@code pushEntities} 每 tick 扫她身边
 * {@code MAID_PICKUP_RANGE}（默认 0.5 格）的范围。缺的从来不是捡，是**走过去**：
 * 一场架打完，战利品散在她脚下三五格外，而没有任何东西会让她挪一步。
 *
 * <p>所以这里只做走的那一半，捡还是交回给本体。{@link #commit} 里那一次
 * {@code pickupItem} 是补最后一步——她停在一格外时自动范围还够不着。
 *
 * <p>**拾物开关不在这个类里。**它在 {@code canPickup} 里，而那个谓词同时还管着
 * 物品过滤白名单和背包余量；广告主只在它点头时才登记这件掉落物。关掉拾物，
 * "有东西可捡"这条事实就是假的，这个差事根本不会被选中。一处开关，一处生效。
 */
public final class LooseDropErrand implements Errand {
    /**
     * 一次看几件。
     *
     * <p>不需要多：这个差事每完成一件就重来一次，而"最近的那一件"每次都会重新
     * 算。要几个备选只是为了第一件在这一 tick 恰好被别人捡走时不至于空手而归。
     */
    private static final int CANDIDATES = 4;

    private final TlmAffordancePerceptionService perception;

    public LooseDropErrand(TlmAffordancePerceptionService perception) {
        this.perception = Objects.requireNonNull(perception, "perception");
    }

    @Override
    public String name() {
        return "loose_drop";
    }

    /**
     * 一次激活清一片，不是一次一件。
     *
     * <p>地上还有第二件东西时收工，代价是可见的一停：意图结束之后要走完"重新
     * 选中 → 重新找目标 → 重新写移动目标"，而这中间她没有移动目标。参数调不掉
     * 它——冷却归零、评估间隔对齐之后那一停仍然在。见 {@link Errand#sweeps}。
     */
    @Override
    public boolean sweeps() {
        return true;
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        Attempt attempt = attempts.get(maid);
        for (Entity candidate
                : perception.queryLooseDrops(maid, CANDIDATES, gameTime)) {
            if (!collectable(maid, candidate)) {
                continue;
            }
            if (attempt != null && attempt.shunned(candidate, gameTime)) {
                continue;
            }
            // 要跳的那种不吃"寻路走不到"这条即时放弃——它本来就走不到，路在跳里，
            // 而立刻放弃正是"她对着一件跳一下就能拿到的东西毫无反应"的成因。
            // 那种目标交给五秒计时器兜底：真站不到底下，五秒后一样会放手。
            boolean cantReach = unreachable(maid)
                    && !TlmLooseDrop.needsAJump(maid, candidate);
            if (attempt != null
                    && attempt.stalledOn(candidate, gameTime, cantReach)) {
                attempt = attempt.giveUp(candidate, gameTime);
                attempts.put(maid, attempt);
                continue;
            }
            attempt = Attempt.on(attempt, candidate, gameTime);
            attempts.put(maid, attempt);
            return new EntityApproachTarget(candidate);
        }
        return null;
    }

    /**
     * 同一件东西盯了多久还没收到，就当它够不着。
     *
     * <p>够不着的东西是存在的：滑进墙角、卡在栅栏另一侧、落在她跳不上去的台子上。
     * 而清扫是"一次激活清一片"，{@link #find} 每 tick 都会重新挑最近的那一件——
     * 于是它每次都挑中同一件走不到的东西，**整趟就此挂死**。实测二十件散落里，
     * 最后一件卡住之后她原地站了四百二十 tick，而地上就剩那一件。
     *
     * <p>五秒：够她绕过一堵矮墙，不够她对着一件永远拿不到的东西发呆。
     */
    private static final int GIVE_UP_TICKS = 100;

    /**
     * 走不到——问导航自己，不用等那五秒。
     *
     * <p>原版的移动行为算不出路径时会记下 {@code CANT_REACH_WALK_TARGET_SINCE}，
     * 那是**即时**的答案，比"盯够五秒"快得多。计时器留着，是因为有些够不着并不
     * 表现为寻路失败：路是通的，只是终点在她头顶，走到了也拿不到。
     *
     * <p>两条一起的差别量过：只有计时器时，一件别扭的东西要她站满八十九 tick；
     * 加上这一问之后剩下的只是导航真正需要的那几拍。
     */
    private static boolean unreachable(EntityMaid maid) {
        return maid.getBrain().hasMemoryValue(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE
        );
    }

    /**
     * 放弃之后多久愿意再看它一眼。
     *
     * <p>不是永久拉黑：世界会变——水把它冲过来、玩家把墙拆了、她自己换了个位置就
     * 有路了。够久到她先去把别的收完，又不至于让一件后来能拿的东西永远躺在那儿。
     */
    private static final int RECONSIDER_TICKS = 600;

    /**
     * 她正盯着哪一件、盯了多久，以及最近放弃过哪些。
     *
     * <p>只有这一个持有者，而它随女仆一起消失（弱引用表），所以没有需要在退出
     * 路径上清理的东西。
     */
    private final Map<EntityMaid, Attempt> attempts = new WeakHashMap<>();

    /**
     * 一次只记一件放弃过的东西是不够的。
     *
     * <p>第一版就是那样，而它在墙角失效：放弃 A 去够 B，B 也够不着，于是放弃 B——
     * 而放弃 B 的同时把 A 忘了，下一 tick 最近的又是 A。她就在两件永远拿不到的
     * 东西之间来回，读数是清扫意图占四百五十三 tick 的静止、二十件只收到十三件。
     *
     * <p>八件：够覆盖一个角落里挤着的一小堆，又不至于让她把一整片地都拉黑。满了
     * 就挤掉最早的那一条，而每一条本来也会到期。
     */
    private static final int SHUNNED_LIMIT = 8;

    private static final class Attempt {
        private final Map<Integer, Long> shunned = new LinkedHashMap<>();
        private int target;
        private long since;

        static Attempt on(Attempt previous, Entity candidate, long gameTime) {
            Attempt attempt = previous == null ? new Attempt() : previous;
            if (attempt.target != candidate.getId()) {
                attempt.target = candidate.getId();
                attempt.since = gameTime;
            }
            return attempt;
        }

        /** 刚放弃过它，这一阵不再看它。 */
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

        /** 走不到，或者盯着它够久了还没收到。 */
        boolean stalledOn(
                Entity candidate,
                long gameTime,
                boolean unreachable
        ) {
            return candidate.getId() == target
                    && (unreachable || gameTime - since >= GIVE_UP_TICKS);
        }

        Attempt giveUp(Entity candidate, long gameTime) {
            if (shunned.size() >= SHUNNED_LIMIT) {
                var oldest = shunned.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            shunned.put(candidate.getId(), gameTime + RECONSIDER_TICKS);
            target = 0;
            since = gameTime;
            return this;
        }
    }

    /**
     * 站到底下，然后跳。
     *
     * <p>把跳跃算进"够不够得着"只让她**愿意去**；原版只在寻路要迈台阶时才跳，
     * 而悬在两格高处的东西没有路可走——寻路当场报走不到，她于是对着一件跳一下就
     * 能拿到的东西毫无反应。这一处补的就是那一下。
     *
     * <p>捡还是本体的：跳到顶点时她的碰撞箱加上拾取半径正好罩住那件东西，
     * {@code pushEntities} 每 tick 都在扫，所以这里只管起跳。
     *
     * <p>先站到底下再跳，不然是原地空跳。水平半格以内才起跳，那正好是拾取半径
     * 的量级；再宽就成了朝着东西的方向乱蹦。
     */
    @Override
    public void whileApproaching(EntityMaid maid, ApproachTarget target) {
        if (!(target instanceof EntityApproachTarget entityTarget)) {
            return;
        }
        Entity aim = entityTarget.entity();
        if (!maid.onGround() || !TlmLooseDrop.needsAJump(maid, aim)) {
            return;
        }
        double dx = aim.getX() - maid.getX();
        double dz = aim.getZ() - maid.getZ();
        if (dx * dx + dz * dz > UNDER_IT * UNDER_IT) {
            return;
        }
        maid.getJumpControl().jump();
    }

    /**
     * 站得多近才值得起跳。
     *
     * <p>半格太严：那只覆盖"东西悬在空中、她能站到正下方"的情形。而两格高的东西
     * 多半是**摆在一个两格高的方块顶上**，她走不到正下方——方块是实心的——只能停在
     * 旁边，水平约一格。卡在半格上她就永远不跳，实机看仍然是毫无反应。
     *
     * <p>一格二：够她站在旁边起跳，又不至于隔着老远朝东西的方向乱蹦。跳完仍然
     * 够不着的那些由"盯久了就放弃"收场——五秒之后她转去下一件，不会一直蹦。
     */
    private static final double UNDER_IT = 1.2D;

    /**
     * 掉落物随时可能被别人捡走、被水冲走、或者自己消失，所以走到一半没了是常态
     * 而不是故障。
     *
     * <p>每 tick 重问 {@code canPickup} 还有第二个作用：**背包在路上被装满**时她
     * 会当场放弃这一趟，而不是走到跟前才发现塞不下。
     */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return target instanceof EntityApproachTarget entityTarget
                && collectable(maid, entityTarget.entity());
    }

    /**
     * 收尾按类型分派，用的全是本体自己的那几个方法。
     *
     * <p>这里不自己实现任何一种拾取：经验怎么折算等级、P 点算不算经验、物品怎么
     * 合并堆叠，都是本体的规则，抄一份就意味着有一天两份会不一样。
     */
    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(target instanceof EntityApproachTarget entityTarget)) {
            return false;
        }
        Entity collectable = entityTarget.entity();
        if (!collectable.isAlive()) {
            // 走到跟前的最后一刻，本体那圈自动拾取先收了它。**这算成功。**
            // 报失败会让这一趟计入冷却，而她此刻正站在剩下那堆东西中间——
            // 目的达成了，只是不是由这一行代码达成的。
            return true;
        }
        return take(maid, collectable);
    }

    /** 交回本体的那几个方法，并如实回答到底有没有收走。 */
    private static boolean take(EntityMaid maid, Entity collectable) {
        if (collectable instanceof ItemEntity item) {
            return maid.pickupItem(item, false);
        }
        if (collectable instanceof ExperienceOrb orb) {
            maid.pickupXPOrb(orb);
            // 问它到底有没有被收走，而不是"调用过了就算数"。本体那两个方法各自
            // 还有一个 canPickup 不查的前提，不满足时它们什么都不做。
            return !orb.isAlive();
        }
        if (collectable instanceof EntityPowerPoint point) {
            maid.pickupPowerPoint(point);
            return !point.isAlive();
        }
        return false;
    }

    private static boolean collectable(EntityMaid maid, Entity candidate) {
        return TlmLooseDrop.collectable(maid, candidate);
    }
}
