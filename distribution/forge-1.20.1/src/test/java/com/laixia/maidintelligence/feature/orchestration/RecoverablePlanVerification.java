package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.api.IntentMetrics;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

@SuppressWarnings("null")
public final class RecoverablePlanVerification {
    private RecoverablePlanVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        restartStepQuiescesAndResumesWithoutNewActivation();
        checkpointPolicyResumesSemanticCheckpoint();
        replanSuffixFallsBackToInitialStep();
        atomicPolicyHaltsInsteadOfSuspending();
        aRefusedResumeStartsOverInsteadOfStalling();
    }

    /**
     * 恢复被拒，就从头开始——不是什么都不做。
     *
     * <p>这里曾经是：恢复失败 → 记二十 tick 冷却 → 返回 ABORTED，这一 tick 什么
     * 都不跑。**那个退避保护的循环并不存在**：挂起帧在尝试恢复时就已经被
     * {@code takeSuspended} 摘掉了，所以"反复去恢复同一个坏帧"从来不会发生，退避
     * 只是让一个刚被选中的意图继续停摆一秒。
     *
     * <p>代价是量出来的。四个卫道士的基准里，战斗意图恢复失败之后编排器依次报
     * {@code resume_aborted}、{@code 1.00/cooldown}、{@code 1.00/eligible}——满分、
     * 条件全满足、优先级一百、无人竞争，而她连续二十三 tick 一个意图都没有，握着
     * 能用的弓、十三支箭、看得见目标，站在四把斧头中间被打死。修好之后同一基准
     * 存活 0–2/4 → 5/8，其中四局满清，且 198 条 required 连跑两轮全绿。
     *
     * <p>断言的是"她有意图在跑"，不是"转移原因等于某个字符串"：前者是规则，后者
     * 是这一次的实现。
     */
    private static void aRefusedResumeStartsOverInsteadOfStalling() {
        RecoveryFixture fixture = new RecoveryFixture(
                singleStepPlan(ResumePolicy.RESTART_STEP)
        );
        fixture.runNormalThenUrgent();
        // 让恢复被拒：动作声明它挂起的那一步已经不成立了。
        fixture.actions.invalidOnResume = WAIT;
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 2L);

        require(
                fixture.intents.inspect("maid").activeIntent() != null,
                "恢复被拒之后她一个意图都没有——挂起帧已经被摘掉，本来就该当作"
                        + "第一次激活重新开始"
        );
        require(
                fixture.intents.inspect("maid").lastTransition()
                        .startsWith("activated:"),
                "恢复被拒之后没有走全新激活那条路，转移原因是 "
                        + fixture.intents.inspect("maid").lastTransition()
        );
    }

    private static void restartStepQuiescesAndResumesWithoutNewActivation() {
        RecoveryFixture fixture = new RecoveryFixture(
                singleStepPlan(ResumePolicy.RESTART_STEP)
        );
        fixture.runNormalThenUrgent();
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 2L);

        require(fixture.actions.quiesces == 1,
                "Recoverable action was not quiesced");
        require(fixture.actions.halts == 0,
                "Recoverable action was destructively halted");
        require(fixture.intents.inspect("maid").lastTransition()
                        .startsWith("resumed:"),
                "Suspended plan did not resume");
        IntentMetrics metrics = fixture.intents.metrics();
        require(metrics.activations() == 2L,
                "Resume was incorrectly counted as a fresh activation");
        require(fixture.intents.observations("maid", 2L)
                        .outcomes().stream()
                        .anyMatch(outcome -> outcome.detail()
                                .startsWith("suspended:")),
                "Quiesced operation did not record a terminal outcome");
    }

    private static void checkpointPolicyResumesSemanticCheckpoint() {
        RecoveryFixture fixture = new RecoveryFixture(checkpointPlan());
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 2L);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 3L);

        require("checkpoint".equals(
                        fixture.intents.inspect("maid").activeState()),
                "Checkpoint policy resumed the wrong state");
    }

    private static void replanSuffixFallsBackToInitialStep() {
        RecoveryFixture fixture = new RecoveryFixture(replanPlan());
        fixture.actions.invalidOnResume = WAIT;
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 2L);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 3L);

        require(fixture.actions.succeedExecutions == 3,
                "Replan suffix did not execute the valid prefix again");
    }

    private static void atomicPolicyHaltsInsteadOfSuspending() {
        RecoveryFixture fixture = new RecoveryFixture(
                singleStepPlan(ResumePolicy.ATOMIC)
        );
        fixture.runNormalThenUrgent();
        require(fixture.actions.quiesces == 0,
                "Atomic action was suspended");
        require(fixture.actions.halts == 1,
                "Atomic action was not halted during preemption");
    }

    private static PlanDefinition singleStepPlan(ResumePolicy policy) {
        return new PlanDefinition(
                id("plan/recovery"),
                "start",
                Map.of("start", state(
                        WAIT,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE,
                        100
                )),
                policy
        );
    }

    private static PlanDefinition checkpointPlan() {
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        states.put("prepare", state(
                SUCCEED,
                "checkpoint",
                PlanDefinition.FAILURE,
                20
        ));
        states.put("checkpoint", state(
                WAIT,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE,
                100
        ));
        return new PlanDefinition(
                id("plan/recovery"),
                "prepare",
                states,
                ResumePolicy.RESUME_CHECKPOINT,
                Set.of("checkpoint"),
                100
        );
    }

    private static PlanDefinition replanPlan() {
        Map<String, PlanDefinition.State> states = new LinkedHashMap<>();
        states.put("prepare", state(
                SUCCEED,
                "work",
                PlanDefinition.FAILURE,
                20
        ));
        states.put("work", state(
                WAIT,
                PlanDefinition.SUCCESS,
                PlanDefinition.FAILURE,
                100
        ));
        return new PlanDefinition(
                id("plan/recovery"),
                "prepare",
                states,
                ResumePolicy.REPLAN_SUFFIX,
                Set.of(),
                100
        );
    }

    private static final class RecoveryFixture {
        private final MutableIntentCatalog catalog =
                new MutableIntentCatalog();
        private final Map<OrchestrationId, Double> facts =
                new HashMap<>();
        private final RecoveryActions actions = new RecoveryActions();
        private final MaidIntentApi<String> intents;

        private RecoveryFixture(PlanDefinition normalPlan) {
            PlanDefinition urgentPlan = plan(
                    id("plan/urgent"),
                    SUCCEED,
                    20
            );
            IntentDefinition normal = definition(
                    id("intent/normal"),
                    normalPlan.id(),
                    List.of(),
                    1.0D,
                    0,
                    0,
                    0.0D
            );
            IntentDefinition urgent = definition(
                    id("intent/urgent"),
                    urgentPlan.id(),
                    List.of(condition(
                            FACT_B,
                            FactComparison.GREATER_OR_EQUAL,
                            1.0D
                    )),
                    1.0D,
                    0,
                    10,
                    0.0D
            );
            catalog.publish(IntentCatalog.compile(
                    1L,
                    List.of(normal, urgent),
                    List.of(normalPlan, urgentPlan),
                    VOCABULARY
            ));
            intents = new DefaultMaidIntentOrchestrator<>(
                    catalog,
                    (subject, gameTime, requested, output) -> {
                        for (int index = 0;
                             index < requested.size();
                             index++) {
                            output[index] = facts.getOrDefault(
                                    requested.get(index),
                                    Double.NaN
                            );
                        }
                    },
                    actions,
                    String::hashCode,
                    () -> true,
                    () -> 1
            );
        }

        private void runNormalThenUrgent() {
            facts.put(FACT_B, 0.0D);
            intents.tick("maid", 0L);
            facts.put(FACT_B, 1.0D);
            intents.tick("maid", 1L);
        }
    }

    private static final class RecoveryActions
            implements IntentActionPort<String> {
        private int quiesces;
        private int halts;
        private int succeedExecutions;
        private OrchestrationId invalidOnResume;

        @Override
        public ActionResult execute(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters,
                long gameTime,
                int elapsedTicks
        ) {
            if (action.equals(SUCCEED)) {
                succeedExecutions++;
                return ActionResult.SUCCEEDED;
            }
            return ActionResult.RUNNING;
        }

        @Override
        public void quiesce(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters
        ) {
            quiesces++;
        }

        @Override
        public void halt(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters,
                String reason
        ) {
            halts++;
        }

        @Override
        public boolean revalidate(
                String subject,
                OrchestrationId action,
                Map<String, String> parameters,
                long gameTime
        ) {
            return !action.equals(invalidOnResume);
        }
    }
}
