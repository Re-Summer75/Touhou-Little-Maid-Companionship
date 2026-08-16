package com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .EntityApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .JumpForTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .TargetPatience;
import com.laixia.maidintelligence.feature.perception.tlm.TlmLooseDrop;
import com.laixia.maidintelligence.feature.perception.tlm
        .TlmAffordancePerceptionService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Objects;

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
 * 物品过滤白名单和背包余量；{@code TlmLooseDrop.collectable} 只在它点头时放行。
 * 关掉拾物，"有东西可捡"这条事实就是假的，这个差事根本不会被选中。
 *
 * <p>走路的三样通用知识都不在这个类里，这是刻意的：够不够得着在
 * {@code TlmLooseDrop}，盯不动要放手在 {@link TargetPatience}，差一跳要起跳在
 * {@link JumpForTarget}。它们每一条都是在这个差事上用读数买的，而下一个走路型
 * 行为直接从货架上拿，不再重买。
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
    private final TargetPatience patience = new TargetPatience();

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
        for (Entity candidate
                : perception.queryLooseDrops(maid, CANDIDATES, gameTime)) {
            if (!TlmLooseDrop.collectable(maid, candidate)) {
                continue;
            }
            // 这里曾有一道"主人八格以内才去"的绳子（OwnerLeash），是承诺模型
            // 落地前对拉锯的临时缓解。它的代价在实机被看出来了：跟随模式下拾取
            // 的有效范围缩成主人周围一小圈，而战斗、柜子、座位全是以**她**为
            // 中心的十六格——同一双眼睛两种视力。拆掉之后感知回归一把尺
            // （PerceptionRange.BLOCKS）；拉锯由真正的机制管：护送只在主人赶路
            // 时合格，八十以下的打断要等承诺窗口再赢分数。她为一件远东西走出去
            // 而主人起步时，至多一个承诺窗口（一秒）就会回头，不再是来回拽。
            // 要跳才够得着的目标，"寻路失败"不可信——它本来就没有路，路在跳里。
            if (!patience.worthTrying(
                    maid,
                    candidate,
                    gameTime,
                    !TlmLooseDrop.needsAJump(maid, candidate)
            )) {
                continue;
            }
            return new EntityApproachTarget(candidate);
        }
        return null;
    }

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
                && TlmLooseDrop.collectable(maid, entityTarget.entity());
    }

    @Override
    public void whileApproaching(EntityMaid maid, ApproachTarget target) {
        if (target instanceof EntityApproachTarget entityTarget) {
            Entity aim = entityTarget.entity();
            JumpForTarget.consider(
                    maid, aim, TlmLooseDrop.needsAJump(maid, aim)
            );
        }
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
}
