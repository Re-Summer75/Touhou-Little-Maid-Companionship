package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;
import com.laixia.maidintelligence.feature.orchestration.domain.recovery.SuspendedPlanFrame;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class IntentPlanExecutor<M> {
    private static final int MAX_SUSPENDED_PLANS = 4;
    private static final UUID NO_CORRELATION = new UUID(0L, 0L);
    private final IntentActionPort<M> actions;
    private final RuntimeObservability<M> observability;

    IntentPlanExecutor(
            IntentActionPort<M> actions,
            RuntimeObservability<M> observability
    ) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.observability = Objects.requireNonNull(
                observability,
                "observability"
        );
    }

    AdvanceResult advance(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            long gameTime
    ) {
        IntentCatalog.CompiledState step =
                active.plan().states().get(state.activeState);
        state.activePlan = active.plan().id();
        state.activeStateId = step.id();
        state.activeAction = step.action();
        state.activeParameters = step.parameters();
        observability.ensureOperation(state, step.id(), gameTime);

        long elapsed = Math.max(0L, gameTime - state.stateSinceTick);
        boolean timedOut = elapsed >= step.timeoutTicks();
        ActionResult result;
        String detail;
        if (timedOut) {
            actions.cancel(subject, step.action(), step.parameters());
            result = ActionResult.FAILED;
            detail = "timeout";
        } else {
            result = actions.execute(
                    subject,
                    step.action(),
                    step.parameters(),
                    gameTime,
                    (int) Math.min(Integer.MAX_VALUE, elapsed)
            );
            detail = "action";
        }
        if (result == ActionResult.RUNNING) {
            IntentRuntimeTrace.active(state, active, step.id());
            return AdvanceResult.ACTIVE;
        }

        observability.recordOutcome(
                subject,
                state,
                active.id(),
                active.plan().id(),
                step.id(),
                step.action(),
                result,
                gameTime,
                detail
        );
        int transition = transition(step, result);
        if (transition == IntentCatalog.TERMINAL_SUCCESS) {
            finish(state, active, gameTime, true);
            return AdvanceResult.COMPLETED;
        }
        if (transition == IntentCatalog.TERMINAL_FAILURE) {
            finish(state, active, gameTime, false);
            return AdvanceResult.FAILED;
        }
        state.activeState = transition;
        state.stateSinceTick = gameTime;
        clearOperation(state);
        IntentCatalog.CompiledState next =
                active.plan().states().get(transition);
        if (active.plan().checkpoints().contains(transition)) {
            state.lastCheckpointStateId = next.id();
        }
        IntentRuntimeTrace.active(state, active, next.id());
        return AdvanceResult.ACTIVE;
    }

    boolean cancelActive(
            M subject,
            MaidIntentRuntimeState state,
            String reason
    ) {
        if (state.activeIntent == null) {
            return false;
        }
        if (state.activeAction != null) {
            actions.halt(
                    subject,
                    state.activeAction,
                    state.activeParameters,
                    reason
            );
            observability.recordOutcome(
                    subject,
                    state,
                    state.activeIntent,
                    state.activePlan,
                    state.activeStateId,
                    state.activeAction,
                    ActionResult.CANCELLED,
                    Math.max(state.lastTick, state.operationStartedAtTick),
                    reason
            );
        }
        IntentRuntimeTrace.cancelled(state, reason);
        return true;
    }

    boolean suspendActive(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            long catalogGeneration,
            long gameTime,
            String reason
    ) {
        ResumePolicy policy = active.plan().resumePolicy();
        if (!policy.suspendable()) {
            return false;
        }
        IntentCatalog.CompiledState step =
                active.plan().states().get(state.activeState);
        if (state.activeOperationId != null) {
            actions.quiesce(subject, step.action(), step.parameters());
            observability.recordOutcome(
                    subject,
                    state,
                    active.id(),
                    active.plan().id(),
                    step.id(),
                    step.action(),
                    ActionResult.CANCELLED,
                    gameTime,
                    "suspended:" + reason
            );
        }
        SuspendedPlanFrame frame = new SuspendedPlanFrame(
                active.id(),
                active.plan().id(),
                step.id(),
                state.lastCheckpointStateId,
                step.action(),
                step.parameters(),
                policy,
                catalogGeneration,
                state.activeSinceTick,
                gameTime,
                IntentRuntimeTrace.deadline(
                        gameTime,
                        active.plan().maximumSuspendTicks()
                ),
                state.activeScore,
                state.correlationId == null
                        ? NO_CORRELATION
                        : state.correlationId,
                reason
        );
        state.suspendedPlans.addFirst(frame);
        while (state.suspendedPlans.size() > MAX_SUSPENDED_PLANS) {
            state.suspendedPlans.removeLast();
        }
        IntentRuntimeTrace.suspended(state, reason);
        return true;
    }

    boolean resume(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent intent,
            SuspendedPlanFrame frame,
            long catalogGeneration,
            long gameTime
    ) {
        if (!frame.activeAt(gameTime, catalogGeneration)
                || !frame.plan().equals(intent.plan().id())) {
            return false;
        }
        int stateIndex = resumeState(intent.plan(), frame);
        if (stateIndex < 0) {
            return false;
        }
        IntentCatalog.CompiledState step =
                intent.plan().states().get(stateIndex);
        if (!actions.revalidate(
                subject,
                step.action(),
                step.parameters(),
                gameTime
        )) {
            if (frame.policy() != ResumePolicy.REPLAN_SUFFIX) {
                return false;
            }
            stateIndex = intent.plan().initialState();
            step = intent.plan().states().get(stateIndex);
            if (!actions.revalidate(
                    subject,
                    step.action(),
                    step.parameters(),
                    gameTime
            )) {
                return false;
            }
        }

        state.activeIntent = intent.id();
        state.activePlan = intent.plan().id();
        state.activeState = stateIndex;
        state.activeSinceTick = frame.activeSinceTick();
        state.stateSinceTick = gameTime;
        state.committedUntilTick = gameTime;
        state.activeScore = frame.score();
        state.correlationId = frame.correlationId();
        state.lastCheckpointStateId =
                intent.plan().checkpoints().contains(stateIndex)
                        ? step.id()
                        : frame.checkpoint();
        state.activeAction = null;
        state.activeParameters = Map.of();
        state.activeOperationId = null;
        state.operationStartedAtTick = -1L;
        return true;
    }

    void pruneSuspended(
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime
    ) {
        Iterator<SuspendedPlanFrame> iterator =
                state.suspendedPlans.iterator();
        while (iterator.hasNext()) {
            SuspendedPlanFrame frame = iterator.next();
            IntentCatalog.CompiledIntent intent =
                    catalog.intent(frame.intent());
            if (!frame.activeAt(gameTime, catalog.generation())
                    || intent == null
                    || !intent.plan().id().equals(frame.plan())) {
                iterator.remove();
            }
        }
    }

    void abortSuspended(MaidIntentRuntimeState state) {
        state.suspendedPlans.clear();
    }

    private static int transition(
            IntentCatalog.CompiledState step,
            ActionResult result
    ) {
        return switch (result) {
            case SUCCEEDED -> step.successState();
            case FAILED -> step.failureState();
            case CANCELLED -> step.cancellationState();
            case RUNNING -> throw new IllegalStateException(
                    "Running action reached terminal transition"
            );
        };
    }

    private static int resumeState(
            IntentCatalog.CompiledPlan plan,
            SuspendedPlanFrame frame
    ) {
        String stateId = frame.policy() == ResumePolicy.RESUME_CHECKPOINT
                && !frame.checkpoint().isEmpty()
                ? frame.checkpoint()
                : frame.state();
        for (int index = 0; index < plan.states().size(); index++) {
            if (plan.states().get(index).id().equals(stateId)) {
                return index;
            }
        }
        return frame.policy() == ResumePolicy.REPLAN_SUFFIX
                ? plan.initialState()
                : -1;
    }

    private static void finish(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            long gameTime,
            boolean succeeded
    ) {
        state.cooldowns.put(
                active.id(),
                IntentRuntimeTrace.deadline(
                        gameTime,
                        active.definition().cooldownTicks()
                )
        );
        String transition = succeeded
                ? "completed:" + active.id()
                : "failed:" + active.id();
        IntentRuntimeTrace.terminal(state, transition);
    }

    private static void clearOperation(MaidIntentRuntimeState state) {
        state.activeAction = null;
        state.activeParameters = Map.of();
        state.activeOperationId = null;
        state.operationStartedAtTick = -1L;
        state.activeStateId = "";
    }

    enum AdvanceResult {
        ACTIVE,
        COMPLETED,
        FAILED
    }
}
