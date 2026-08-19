package com.laixia.maidintelligence.feature.orchestration;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.PlanDefinition;

import java.util.List;

import static com.laixia.maidintelligence.feature.orchestration.IntentVerificationFixture.*;

/**
 * 仲裁契约：承诺/滞回挡住普通换手，八十以上的强制档立刻拿走她。
 *
 * <p>从 {@code IntentOrchestrationVerification} 按族拆出（单文件五百行的布局
 * 纪律）——这两条钉的是同一份契约（band 只裁打断资格、分数裁一切偏好），与
 * 其余的目录/生命周期验证不是一件事。
 */
public final class ArbitrationContractVerification {
    private ArbitrationContractVerification() {
    }

    public static void main(String[] args) {
        commitmentAndHysteresisPreventThrashing();
        onlyImperativeBandsBreakCommitment();
        System.out.println(
                "Arbitration contract verification passed."
        );
    }

    private static void commitmentAndHysteresisPreventThrashing() {
        IntentOrchestrationVerification.Fixture fixture =
                new IntentOrchestrationVerification.Fixture();
        PlanDefinition plan = plan(id("plan/hysteresis"), WAIT, 100);
        IntentDefinition first = scored(
                id("intent/a"),
                plan.id(),
                FACT_A,
                20,
                10,
                0.2D
        );
        IntentDefinition second = scored(
                id("intent/b"),
                plan.id(),
                FACT_B,
                0,
                10,
                0.0D
        );
        fixture.publish(1L, List.of(first, second), List.of(plan));
        fixture.facts.put(FACT_A, 0.6D);
        fixture.facts.put(FACT_B, 0.5D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 0.75D);
        fixture.intents.tick("maid", 10L);
        require(id("intent/a").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Commitment was broken by an ordinary candidate");
        fixture.facts.put(FACT_B, 0.9D);
        fixture.intents.tick("maid", 20L);
        require(id("intent/b").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "Hysteresis did not allow a materially better candidate");
    }

    /**
     * 打断资格从 80 起：外界的要求（战斗、主人的命令）不等承诺窗口，她自己的
     * 安排之间没有插队。
     *
     * <p>这条闸门曾经钉着相反的语义——priority 20、0.5 分即可当场撕毁 priority
     * 10、1.0 分的一百 tick 承诺。那正是审计定为 RC1 的缺陷：把评估节拍调快之后
     * COMPANIONSHIP 每五 tick 就把清扫中的她拿走一次，实机是"捡东西又不连贯了"。
     * 语义连同闸门一起改，改在明处。
     */
    private static void onlyImperativeBandsBreakCommitment() {
        IntentOrchestrationVerification.Fixture fixture =
                new IntentOrchestrationVerification.Fixture();
        PlanDefinition plan = plan(id("plan/interrupt"), WAIT, 200);
        IntentDefinition normal = definition(
                id("intent/normal"),
                plan.id(),
                List.of(),
                1.0D,
                100,
                10,
                0.0D
        );
        // 她自己的另一件安排：band 更高（50>10）、分数也更高，但不到 80。
        IntentDefinition errand = definition(
                id("intent/errand"),
                plan.id(),
                List.of(condition(
                        FACT_B,
                        FactComparison.GREATER_OR_EQUAL,
                        1.0D
                )),
                2.0D,
                0,
                50,
                0.0D
        );
        // 外界的要求：主人的命令那一档。分数低都无所谓，打断不看分。
        IntentDefinition command = definition(
                id("intent/command"),
                plan.id(),
                List.of(condition(
                        FACT_A,
                        FactComparison.GREATER_OR_EQUAL,
                        1.0D
                )),
                0.5D,
                0,
                80,
                0.0D
        );
        fixture.publish(
                1L, List.of(normal, errand, command), List.of(plan)
        );
        fixture.facts.put(FACT_A, 0.0D);
        fixture.facts.put(FACT_B, 0.0D);
        fixture.intents.tick("maid", 0L);
        fixture.facts.put(FACT_B, 1.0D);
        fixture.intents.tick("maid", 1L);
        require(id("intent/normal").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "A mid-band errand broke commitment it was supposed to wait "
                        + "out — interruption below 80 must respect "
                        + "minimum_commit_ticks");
        // 承诺窗口过后，分数高的那件安排照常赢——这是效用层的胜利，不是插队。
        fixture.intents.tick("maid", 100L);
        require(id("intent/errand").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "After the commitment window a higher-scoring errand still "
                        + "could not take over");
        // 主人的命令：分数只有一半，仍然立刻拿走她。
        fixture.facts.put(FACT_A, 1.0D);
        fixture.intents.tick("maid", 101L);
        require(id("intent/command").equals(
                        fixture.intents.inspect("maid").activeIntent()),
                "An imperative band (>=80) did not preempt commitment");
    }
}
