package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.recovery.SuspendedPlanFrame;

import java.util.Objects;

final class IntentActivationCoordinator<M> {
    private final IntentPlanExecutor<M> executor;
    private final RuntimeObservability<M> observability;

    IntentActivationCoordinator(
            IntentPlanExecutor<M> executor,
            RuntimeObservability<M> observability
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.observability = Objects.requireNonNull(
                observability,
                "observability"
        );
    }

    StartResult start(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            IntentSelectionEngine.ScoredIntent selected,
            long gameTime
    ) {
        IntentCatalog.CompiledIntent intent = selected.intent();
        SuspendedPlanFrame frame = state.takeSuspended(intent.id());
        if (frame != null) {
            if (executor.resume(
                    subject,
                    state,
                    intent,
                    frame,
                    catalog.generation(),
                    gameTime
            )) {
                state.activeScore = selected.score();
                return StartResult.RESUMED;
            }
            // 恢复不了就从头开始，不退避。
            //
            // 退避原本防的是"反复去恢复同一个坏帧"，可那个循环从一开始就不存在：
            // 上面的 takeSuspended 已经把帧摘掉了，下一次进来走的必然是下面这条
            // 全新启动的路。所以退避买不到任何保护，只是让一个刚刚被选中的意图
            // 再停摆一秒。
            //
            // 而那段停摆是致命的。实测四个卫道士的基准里，战斗意图恢复失败之后
            // 编排器报 `resume_aborted`、随即 `1.00/cooldown`——满分、条件全满足、
            // 优先级一百，她却连续几十 tick 一个意图都没有，握着能用的弓站在四把
            // 斧头中间被打死。那一秒正是这里买的。
            //
            // 从头开始是安全的：resume 的每一条失败路径都在写 activeIntent 之前
            // 返回，状态没有被动过，与第一次激活面对的是同一个局面。
        }

        state.activeIntent = intent.id();
        state.activePlan = intent.plan().id();
        state.activeState = intent.plan().initialState();
        state.activeSinceTick = gameTime;
        state.stateSinceTick = gameTime;
        state.committedUntilTick = IntentRuntimeTrace.deadline(
                gameTime,
                intent.definition().minimumCommitTicks()
        );
        state.activeScore = selected.score();
        if (intent.plan().checkpoints().contains(state.activeState)) {
            state.lastCheckpointStateId = intent.plan()
                    .states()
                    .get(state.activeState)
                    .id();
        }
        observability.startCorrelation(
                subject,
                state,
                intent.id(),
                gameTime
        );
        IntentSelectionEngine.consumeSignals(state, catalog, intent);
        return StartResult.ACTIVATED;
    }

    InterruptResult interrupt(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            IntentCatalog catalog,
            long gameTime,
            String reason
    ) {
        if (active == null) {
            return InterruptResult.NONE;
        }
        if (executor.suspendActive(
                subject,
                state,
                active,
                catalog.generation(),
                gameTime,
                reason
        )) {
            return InterruptResult.SUSPENDED;
        }
        return executor.cancelActive(subject, state, reason)
                ? InterruptResult.CANCELLED
                : InterruptResult.NONE;
    }

    /**
     * 启动的两种结局。
     *
     * <p>曾经有第三种 {@code ABORTED}——恢复失败就什么都不做并退避一秒。它被删掉
     * 是因为它保护的东西不存在：挂起帧在尝试恢复时就已经被摘掉了，所以"反复恢复
     * 同一个坏帧"这个循环从来不会发生，而退避本身让一个刚被选中的意图继续停摆。
     */
    enum StartResult {
        ACTIVATED,
        RESUMED
    }

    enum InterruptResult {
        SUSPENDED,
        CANCELLED,
        NONE
    }
}
