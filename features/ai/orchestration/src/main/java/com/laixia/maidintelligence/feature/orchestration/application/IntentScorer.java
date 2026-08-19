package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.port.UtilityModifierPort;

import java.util.List;

/**
 * 给一个候选定价：效用聚合、学习修正的钳制、以及剪枝下界的口径。
 *
 * <p>从 {@link IntentSelectionEngine} 按职责拆出（单文件五百行的布局纪律）：
 * 引擎回答"轮到谁、换不换手"，这里只回答"这个候选值多少分、低于多少分不必
 * 算完"。两问共用的唯一契约是：**剪枝返回的是上界**——它只在调用方丢弃踪迹
 * 且上界也赢不了的时候才是安全的，这一条写在 {@code score} 的文档里。
 */
final class IntentScorer<M> {
    private static final double MAX_UTILITY_MODIFIER = 50.0D;

    /** 见 {@link IntentDefinition#IMPERATIVE_INTERRUPT_PRIORITY}。 */
    private static final int IMPERATIVE_INTERRUPT_PRIORITY =
            IntentDefinition.IMPERATIVE_INTERRUPT_PRIORITY;

    private final UtilityModifierPort<M> modifiers;

    IntentScorer(UtilityModifierPort<M> modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * Scores one intent, optionally stopping as soon as it cannot reach
     * {@code pruneFloor}.
     *
     * <p>A pruned return is an upper bound rather than the true score. That is
     * only sound because the caller discards the trace whenever pruning is
     * enabled, and because a value that failed to reach the floor cannot go on
     * to win: both the incumbent comparison and the {@code minimum_score} gate
     * reject an over-estimate exactly as they would reject the real number.
     */
    double score(
            M subject,
            IntentCatalog.CompiledIntent intent,
            double[] facts,
            long gameTime,
            double pruneFloor
    ) {
        IntentDefinition definition = intent.definition();
        UtilityAggregation aggregation = definition.aggregation();
        /*
         * Resolved before the considerations, not after, so the bound below is
         * exact instead of needing headroom for a modifier that has not been
         * read yet. It does not consult the fact array, so the move cannot
         * change what it returns.
         */
        double modifier = modifier(subject, intent, gameTime);
        List<IntentCatalog.CompiledConsideration> considerations =
                intent.considerations();
        int count = considerations.size();
        boolean prunable = aggregation.monotonicallyNonIncreasing()
                && pruneFloor > Double.NEGATIVE_INFINITY;
        double accumulated = definition.baseScore();
        for (int index = 0; index < count; index++) {
            IntentCatalog.CompiledConsideration consideration =
                    considerations.get(index);
            accumulated = aggregation.combine(
                    accumulated,
                    aggregation.term(
                            consideration.consideration(),
                            facts[consideration.factIndex()]
                    )
            );
            if (prunable) {
                // Every remaining term can only lower `accumulated`, and
                // `finish` is non-decreasing in it, so this bounds the
                // finished score from above.
                double ceiling = aggregation.finish(accumulated, count)
                        + modifier;
                if (ceiling < pruneFloor) {
                    return ceiling;
                }
            }
        }
        return aggregation.finish(accumulated, count) + modifier;
    }

    /**
     * The score an intent must be able to beat, or
     * {@link Double#NEGATIVE_INFINITY} when it has to be scored in full.
     *
     * <p>排序交还给分数之后，这条下界对**所有** band 的候选都成立——不再有"高 band
     * 不用比分"的豁免，也不再有被调用方整段跳过的低 band。
     */
    double pruneFloor(
            IntentSelectionEngine.ScoredIntent winner,
            IntentCatalog.CompiledIntent intent,
            boolean continuation,
            boolean retainTrace
    ) {
        if (retainTrace || continuation || winner == null) {
            return Double.NEGATIVE_INFINITY;
        }
        // 强制档不剪：它们不按分数参赛（线上按等级），剪出来的上界值一旦流进
        // 同档平手比较就是错的排序依据。强制档一共两三个意图，全额算得起。
        if (intent.definition().interruptPriority()
                >= IMPERATIVE_INTERRUPT_PRIORITY) {
            return Double.NEGATIVE_INFINITY;
        }
        // Strict, because an exact tie is still resolved by priority and then
        // intent id, and an over-estimate must never reach those comparisons.
        return winner.score();
    }

    private double modifier(
            M subject,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    ) {
        double modifier;
        try {
            modifier = modifiers.modifier(subject, intent, gameTime);
        } catch (RuntimeException ignored) {
            modifier = 0.0D;
        }
        if (!Double.isFinite(modifier)) {
            return 0.0D;
        }
        return Math.max(
                -MAX_UTILITY_MODIFIER,
                Math.min(MAX_UTILITY_MODIFIER, modifier)
        );
    }
}
