package com.laixia.maidintelligence.feature.orchestration.tlm.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMovement;
import com.laixia.maidintelligence.feature.behavior.tlm.MaidCommandSeatBridge;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmActionParameters;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Go there, hold it, do it.
 *
 * <p>Every errand a maid runs has the same shape — check she is free, pick
 * something, reserve it, walk over, and only then do the one thing that makes
 * this errand different from the others. Written once per errand, the first
 * four steps came to roughly two hundred and fifty duplicated lines apiece,
 * and the duplication was the dangerous part rather than the wasteful one:
 * three copies of "release the walk target only if it is still ours" agree
 * until somebody improves one of them.
 *
 * <p>So this is the only place in the mod that writes a maid's walk target for
 * a companion errand, and the only place that reserves what she is heading
 * toward. An {@link Errand} supplies the two genuinely varying pieces.
 *
 * <p>One instance per errand, holding that errand's own progress, so two kinds
 * of errand cannot be confused for one another mid-walk.
 */
public final class ApproachAndCommitAction {
    private static final int CLAIM_LEASE_TICKS = 100;
    private static final int COMMIT_LEASE_TICKS = 20;

    private final Errand errand;
    private final Map<EntityMaid, Progress> progress = new WeakHashMap<>();

    public ApproachAndCommitAction(Errand errand) {
        this.errand = Objects.requireNonNull(errand, "errand");
    }

    public ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        errand.prepare(maid);
        if (!eligible(maid)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        int closeEnough = TlmActionParameters.integer(
                parameters,
                "close_distance",
                2,
                1,
                4
        );
        float speed = TlmActionParameters.number(
                parameters,
                "speed",
                0.55F,
                0.1F,
                2.0F
        );
        // 计划里那个数是标称值，差事可以按趟给一个倍率——散步用它把恒定速度打散。
        // 夹在同一组上下限里，所以倍率再离谱也走不出计划允许的范围。
        float paced = (float) Math.max(
                0.1D, Math.min(2.0D, speed * errand.paceFactor(maid))
        );
        // 一 tick 一趟。这里试过"收下脚边这件之后，同一 tick 内再转向下一件"，
        // 想省掉重新寻路的那一拍——量下来清扫自己的空转只从十一 tick 降到十，
        // 属于噪声，却打红了另外三条测试。收益不抵代价，去掉了。
        ActionResult result = approach(maid, gameTime, closeEnough, paced);
        return result == null ? ActionResult.RUNNING : result;
    }

    /**
     * 一趟：找目标、预订、到了就动手、没到就走。
     *
     * <p>返回 {@code null} 表示"这一件收下了，而这趟差事还没完"。
     */
    private ActionResult approach(
            EntityMaid maid,
            long gameTime,
            int closeEnough,
            float paced
    ) {
        ApproachTarget target = errand.find(maid, gameTime);
        if (target == null || !target.valid()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        Progress held = claim(maid, target, gameTime);
        if (held == null) {
            /*
             * Somebody else holds it. Failing rather than waiting hands the
             * decision back to the engine, which may well rank a different
             * errand highest now that this one is spoken for.
             */
            return ActionResult.FAILED;
        }
        if (arrived(maid, target, closeEnough)) {
            ActionResult committed = commit(maid, held, target, gameTime);
            if (committed == ActionResult.RUNNING && errand.sweeps()) {
                return null;
            }
            return committed;
        }
        if (!maid.canBrainMoving()) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        errand.whileApproaching(maid, target);
        return walk(maid, held, target, paced, closeEnough);
    }

    /**
     * Whether the step may continue. Called between ticks, when the world has
     * had a chance to move on without telling anyone.
     */
    public boolean revalidate(EntityMaid maid, long gameTime) {
        Progress held = progress.get(maid);
        if (held == null) {
            // 清扫在两件之间就是这个样子：刚收下一件，预订已经放掉、进度已经清空，
            // 下一个目标要到下一 tick 的 execute 才找。对这种差事答否，等于每收一
            // 件就把整趟取消一次——正是它要消灭的那一停。
            return errand.sweeps();
        }
        if (!eligible(maid)
                || !errand.stillWorthwhile(maid, held.target(), gameTime)
                || (held.claim() != null
                        && !held.claims().owns(held.claim(), gameTime))) {
            cancel(maid);
            return false;
        }
        return true;
    }

    public void cancel(EntityMaid maid) {
        release(maid, progress.remove(maid), "cancelled");
    }

    private ActionResult commit(
            EntityMaid maid,
            Progress held,
            ApproachTarget target,
            long gameTime
    ) {
        if (!errand.sweeps()) {
            clearMovement(maid, held);
        }
        // Upgrading the reservation before touching anything is what stops two
        // maids who both arrived from both succeeding.
        boolean occupied = held.claim() == null
                || (held.claims().occupy(
                        held.claim(),
                        gameTime,
                        COMMIT_LEASE_TICKS
                ) && held.claims().owns(held.claim(), gameTime));
        boolean done = occupied && errand.commit(maid, target, gameTime);
        if (done && errand.sweeps()) {
            // 放掉预订，**留着路径**。
            //
            // 留路径是"零间隔"的全部：在这里抹掉它，写回就要等下一 tick，中间隔着
            // 一次导航更新——她走到跟前刹住、收完再重新起步，实机看就是那一停。
            // 留着的话，下一 tick 的 walk 会直接把它改写成下一件东西的位置，
            // 任何一个 tick 结束时她手上都有目标。
            //
            // 而租约必须照常放：量过，留着它下一 tick 就 tryClaim 不到下一件，
            // 整趟当场 FAILED——她收下第一件之后再也不动了。
            if (progress.get(maid) == held) {
                progress.remove(maid);
            }
            releaseClaim(maid, held, errand.name());
            return ActionResult.RUNNING;
        }
        if (progress.get(maid) == held) {
            progress.remove(maid);
        }
        release(maid, held, done ? errand.name() : "commit_failed");
        return done ? ActionResult.SUCCEEDED : ActionResult.FAILED;
    }

    private ActionResult walk(
            EntityMaid maid,
            Progress held,
            ApproachTarget target,
            float speed,
            int closeEnough
    ) {
        if (alreadyHeading(maid, target)) {
            return ActionResult.RUNNING;
        }
        clearMovement(maid, held);
        PositionTracker tracker = target.tracker();
        WalkTarget written = new WalkTarget(tracker, speed, closeEnough);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        // Refused only while the host is walking her to air. Taking a refusal
        // as success would leave her standing still with a plan that believes
        // it is under way; the check used to guard against a whole arbiter and
        // now guards against the one writer that still outranks us.
        if (!FreedomMovement.write(maid, written)) {
            cancel(maid);
            return ActionResult.FAILED;
        }
        progress.put(maid, held.withWalkTarget(written));
        return ActionResult.RUNNING;
    }

    private Progress claim(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return null;
        }
        if (!errand.requiresClaim()) {
            // Nothing to contend for, so progress is only about which walk
            // target is ours to withdraw.
            Progress current = progress.get(maid);
            if (current != null
                    && current.target().identity().equals(target.identity())) {
                return current;
            }
            cancel(maid);
            Progress unclaimed = new Progress(target, null, null, null);
            progress.put(maid, unclaimed);
            return unclaimed;
        }
        Progress current = progress.get(maid);
        if (current != null
                && current.target().identity().equals(target.identity())) {
            if (current.claims().renew(
                    current.claim(),
                    gameTime,
                    CLAIM_LEASE_TICKS
            )) {
                // Same errand, refreshed target: keep the walk already written.
                Progress refreshed = current.withTarget(target);
                progress.put(maid, refreshed);
                return refreshed;
            }
            cancel(maid);
        } else if (current != null) {
            cancel(maid);
        }

        CoordinationClaimService claims =
                TlmCoordinationClaims.service(level);
        CoordinationClaimToken token = claims.tryClaim(
                new CoordinationClaimRequest(
                        target.claimKey(level),
                        maid.getUUID(),
                        TlmCoordinationClaims.operation(
                                maid,
                                errand.name() + "/" + target.identity()
                        ),
                        CLAIM_LEASE_TICKS
                ),
                gameTime
        ).orElse(null);
        if (token == null) {
            return null;
        }
        Progress claimed = new Progress(target, null, token, claims);
        progress.put(maid, claimed);
        return claimed;
    }

    private static boolean eligible(EntityMaid maid) {
        return !maid.isOrderedToSit()
                && !maid.isMaidInSittingPose()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && !maid.isUsingItem()
                && !maid.isBegging()
                && !maid.isPassenger()
                && !maid.getBrain().hasMemoryValue(
                        MemoryModuleType.ATTACK_TARGET
                )
                && !maid.getBrain().isActive(Activity.PANIC)
                && !MaidCommandSeatBridge.isSeatProtected(maid);
    }

    /**
     * 她到了没有——按**导航的口径**问，不是自己另算一遍。
     *
     * <p>这两把尺此前不是同一把，而差额足以让她永远动不了手：{@code WalkTarget}
     * 交给原版的 {@code MoveToTargetSink}，它判到达用的是**方块坐标上的曼哈顿
     * 距离** ≤ {@code closeEnough}，到了就停；而这里原先问的是欧氏距离平方
     * ≤ {@code closeEnough²}。曼哈顿距离为一时欧氏距离可以接近二——**她已经停下，
     * 却始终不满足动手的条件，于是就那么站着。**
     *
     * <p>实测：二十件散落的掉落物，六百 tick 里她站着不动五百九十一 tick，其中
     * 五百三十一 tick 清扫意图正活动着——不是别的意图抢了她，是她自己到了却不动手。
     * 只收到一件。
     *
     * <p>此前之所以没暴露，是因为每收一件计划都会结束再重来，重来时会重新寻路，
     * 顺手把她蹭进欧氏范围。把路径留住以消除那一停之后，这份意外的救济也没了——
     * 一个 bug 盖住另一个 bug。
     *
     * <p>欧氏那一条仍然保留并取或：目标就在脚下时曼哈顿是零，两条都成立；而
     * 有些接近目标（容器槽位）给的位置不落在方块中心上，欧氏更贴近"够得着"。
     */
    private static boolean arrived(
            EntityMaid maid,
            ApproachTarget target,
            int closeEnough
    ) {
        if (target.distanceToSqr(maid) <= (double) closeEnough * closeEnough) {
            return true;
        }
        return maid.blockPosition().distManhattan(
                target.tracker().currentBlockPosition()
        ) <= closeEnough;
    }

    private static boolean alreadyHeading(
            EntityMaid maid,
            ApproachTarget target
    ) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(target::matches)
                .isPresent();
    }

    private void release(EntityMaid maid, Progress held, String reason) {
        if (held == null) {
            return;
        }
        clearMovement(maid, held);
        releaseClaim(maid, held, reason);
    }

    /**
     * 只把预订放掉，路径留着。
     *
     * <p>清扫在两件之间走的就是这一条：**租约必须照常释放**，否则下一 tick 为下
     * 一件东西 {@code tryClaim} 会拿不到，整趟当场 FAILED——量到的样子是她收下第
     * 一件、走了九个 tick，然后就再也没有移动目标了。而路径必须留着，那才是"不
     * 刹车"。两件事此前捆在同一个方法里，所以只能同生共死。
     */
    private static void releaseClaim(
            EntityMaid maid,
            Progress held,
            String reason
    ) {
        if (held == null || held.claim() == null) {
            return;
        }
        held.claims().release(
                held.claim(),
                maid.level().getGameTime(),
                reason
        );
    }

    /**
     * Erases only our own walk target. Something else may have taken the wheel
     * since it was written, and clearing that would be a silent override.
     */
    private static void clearMovement(EntityMaid maid, Progress held) {
        WalkTarget current = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (current == null) {
            return;
        }
        if (held.walkTarget() == null || current != held.walkTarget()) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    private record Progress(
            ApproachTarget target,
            WalkTarget walkTarget,
            CoordinationClaimToken claim,
            CoordinationClaimService claims
    ) {
        Progress withWalkTarget(WalkTarget written) {
            return new Progress(target, written, claim, claims);
        }

        Progress withTarget(ApproachTarget refreshed) {
            return new Progress(refreshed, walkTarget, claim, claims);
        }
    }
}
