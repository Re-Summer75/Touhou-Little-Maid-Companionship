package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.ResumePolicy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_A;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FACT_B;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.FAIL;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.SUCCEED;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.WAIT;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.condition;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.definition;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.id;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.plan;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.require;
import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.state;

/**
 * 每条退出路径都要把摊子收干净——这一条从"作者自觉"升级成闸门。
 *
 * <p>审计（{@code docs/architecture/ai-defect-audit.md}）RC5 记的是同一个模式反复
 * 发生：写入路径有人写，对称的清理路径没人管，然后账在很远的地方爆。恢复失败的
 * 退避保护过一个不存在的循环（意图停摆 119 tick）、被捡走的广告挂满 TTL 吃光
 * 查询预算（她对满地东西视而不见）、本体的隐藏槽假定"存/还"必然成对（战斗打断
 * 进食就把武器扔在地上）。这里把编排器自己的四条退出路径逐一钉死。
 */
public final class LifecycleHygieneVerification {
    private LifecycleHygieneVerification() {
    }

    public static void main(String[] args) {
        aCompletedIntentFreesHerTheVeryNextTick();
        aFailedIntentFreesHerTheVeryNextTick();
        aBlockedActiveIsReplacedTheSameTick();
        preemptionCleansUpTheLoserExactlyOnce();
        anExpiredSuspensionStartsOverInsteadOfResuming();
        aFreshSuspensionStillResumes();
        System.out.println("Lifecycle hygiene verification passed.");
    }

    /**
     * 完成不留死窗口：这 tick 收工，下 tick 就有人接班。
     *
     * <p>盯的是 {@code finish} 路径上的 dirty 位。少了它，接班要等编排器下一个
     * 评估节拍——实机就是"她吃完饭站一会儿才想起下一件事"。
     */
    private static void aCompletedIntentFreesHerTheVeryNextTick() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/errand"), id("plan/done"),
                                List.of(), 1.0D, 0, 10, 0.0D, 1_000),
                        definition(id("intent/idle"), id("plan/wait"),
                                List.of(), 0.5D, 0, 10, 0.0D)
                ),
                List.of(
                        plan(id("plan/done"), SUCCEED, 100),
                        plan(id("plan/wait"), WAIT, 100)
                )
        );
        fixture.intents.tick("maid", 0L);
        require(
                fixture.intents.inspect("maid").activeIntent() == null,
                "The one-shot errand did not complete on its first tick"
        );
        fixture.intents.tick("maid", 1L);
        require(
                id("intent/idle").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Completion left a dead window: nothing took over on the "
                        + "very next tick"
        );
    }

    /** 失败与完成同权：同样不留死窗口。 */
    private static void aFailedIntentFreesHerTheVeryNextTick() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/errand"), id("plan/flop"),
                                List.of(), 1.0D, 0, 10, 0.0D, 1_000),
                        definition(id("intent/idle"), id("plan/wait"),
                                List.of(), 0.5D, 0, 10, 0.0D)
                ),
                List.of(
                        plan(id("plan/flop"), FAIL, 100),
                        plan(id("plan/wait"), WAIT, 100)
                )
        );
        fixture.intents.tick("maid", 0L);
        fixture.intents.tick("maid", 1L);
        require(
                id("intent/idle").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Failure left a dead window: nothing took over on the very "
                        + "next tick"
        );
    }

    /**
     * 条件失效的现任**当 tick** 就被替换，且她的清理钩子确实跑了。
     *
     * <p>打断置 dirty、同一 tick 内重新评估——这条链断在哪儿，实机都是她愣着。
     */
    private static void aBlockedActiveIsReplacedTheSameTick() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/gated"), id("plan/wait"),
                                List.of(condition(FACT_A,
                                        FactComparison.GREATER_OR_EQUAL,
                                        1.0D)),
                                1.0D, 0, 10, 0.0D),
                        definition(id("intent/idle"), id("plan/wait"),
                                List.of(), 0.5D, 0, 10, 0.0D)
                ),
                List.of(plan(id("plan/wait"), WAIT, 100))
        );
        fixture.facts.put(FACT_A, 1.0D);
        fixture.intents.tick("maid", 0L);
        require(
                id("intent/gated").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "The gated errand never started"
        );
        int cleanups = fixture.actions.cancellations;
        fixture.facts.put(FACT_A, 0.0D);
        fixture.intents.tick("maid", 1L);
        require(
                id("intent/idle").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "A blocked active was not replaced on the same tick"
        );
        require(
                fixture.actions.cancellations == cleanups + 1,
                "The blocked errand's cleanup ran "
                        + (fixture.actions.cancellations - cleanups)
                        + " times instead of once"
        );
    }

    /**
     * 强制打断把输家清理**恰好一次**——零次是泄漏（移动目标、租约没人放），
     * 两次是重复退款（同一份状态被两条路径各清一遍，第二遍清的是别人的）。
     */
    private static void preemptionCleansUpTheLoserExactlyOnce() {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/errand"), id("plan/wait"),
                                List.of(), 1.0D, 100, 10, 0.0D),
                        definition(id("intent/command"), id("plan/wait"),
                                List.of(condition(FACT_B,
                                        FactComparison.GREATER_OR_EQUAL,
                                        1.0D)),
                                0.5D, 0, 80, 0.0D)
                ),
                List.of(plan(id("plan/wait"), WAIT, 200))
        );
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        int cleanups = fixture.actions.cancellations;
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 1L);
        require(
                id("intent/command").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "The imperative command did not take over"
        );
        require(
                fixture.actions.cancellations == cleanups + 1,
                "Preemption cleaned the loser up "
                        + (fixture.actions.cancellations - cleanups)
                        + " times instead of once"
        );
    }

    /**
     * 挂起帧随时限过期：过了 {@code maximumSuspendTicks} 再回来，是从头开始，
     * 不是从帧里爬出来。
     *
     * <p>帧是存储状态，而存储状态的每一条都要有过期路——审计里三个"账在远处爆"
     * 的案例（退避、死广告、隐藏槽）全是没有过期路的存储。
     */
    private static void anExpiredSuspensionStartsOverInsteadOfResuming() {
        IntentVerificationFixture fixture = suspendScenario(50);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 1L);
        // 强占者一直干到挂起时限之后。
        for (long tick = 2L; tick <= 60L; tick++) {
            fixture.intents.tick("maid", tick);
        }
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 61L);
        require(
                id("intent/errand").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "The suspended errand never came back at all"
        );
        String transition = fixture.intents.inspect("maid").lastTransition();
        require(
                transition.startsWith("activated"),
                "An expired suspension resumed from its stale frame: "
                        + transition
        );
    }

    /** 对照：时限内回来的，确实要走恢复。 */
    private static void aFreshSuspensionStillResumes() {
        IntentVerificationFixture fixture = suspendScenario(500);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 1L);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 20L);
        require(
                id("intent/errand").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "The suspended errand never came back"
        );
        String transition = fixture.intents.inspect("maid").lastTransition();
        require(
                transition.startsWith("resumed"),
                "A suspension well inside its window restarted from scratch: "
                        + transition
        );
    }

    /** 可挂起的差事 + 一个 imperative 强占者，挂起时限由调用方定。 */
    private static IntentVerificationFixture suspendScenario(
            int maximumSuspendTicks
    ) {
        IntentVerificationFixture fixture = new IntentVerificationFixture();
        PlanDefinition suspendable = new PlanDefinition(
                id("plan/suspendable"),
                "start",
                Map.of("start", state(
                        WAIT,
                        PlanDefinition.SUCCESS,
                        PlanDefinition.FAILURE,
                        1_000
                )),
                ResumePolicy.RESTART_STEP,
                Set.of(),
                maximumSuspendTicks
        );
        fixture.publish(
                1L,
                List.of(
                        definition(id("intent/errand"), suspendable.id(),
                                List.of(), 1.0D, 0, 10, 0.0D),
                        definition(id("intent/command"), id("plan/wait"),
                                List.of(condition(FACT_B,
                                        FactComparison.GREATER_OR_EQUAL,
                                        1.0D)),
                                0.5D, 0, 80, 0.0D)
                ),
                List.of(suspendable, plan(id("plan/wait"), WAIT, 1_000))
        );
        return fixture;
    }

}
