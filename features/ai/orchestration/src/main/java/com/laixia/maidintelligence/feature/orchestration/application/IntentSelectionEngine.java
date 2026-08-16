package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.port.UtilityModifierPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

final class IntentSelectionEngine<M> {
    private static final int MAX_TRACE_CANDIDATES = 8;
    private static final double MAX_UTILITY_MODIFIER = 50.0D;

    /**
     * 见 {@link IntentDefinition#IMPERATIVE_INTERRUPT_PRIORITY}。
     *
     * <p>此前高 band 打断一律零代价，代价是量出来的：把评估节拍调快之后，
     * COMPANIONSHIP 每五 tick 就有一次机会把清扫中的她拿走，实机表现是"捡东西
     * 又不连贯了"——两条规则各自都对，合在一起互相拆台。打断有了代价，节拍才
     * 能安全地快。
     */
    private static final int IMPERATIVE_INTERRUPT_PRIORITY =
            IntentDefinition.IMPERATIVE_INTERRUPT_PRIORITY;

    private final ToLongFunction<M> identity;
    private final UtilityModifierPort<M> modifiers;

    IntentSelectionEngine(
            ToLongFunction<M> identity,
            UtilityModifierPort<M> modifiers
    ) {
        this.identity = identity;
        this.modifiers = modifiers;
    }

    Selection select(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            long gameTime,
            int maxCandidates,
            boolean retainTrace
    ) {
        List<IntentTrace.Candidate> trace = new ArrayList<>();
        ScoredIntent winner = null;
        int evaluated = 0;
        List<IntentCatalog.CompiledIntent> intents = catalog.intents();
        int intentCount = intents.size();
        int start = state.candidateCursor < 0
                ? (int) Math.floorMod(
                        mix64(identity.applyAsLong(subject)),
                        intentCount
                )
                : Math.floorMod(state.candidateCursor, intentCount);
        int budgetCursor = start;
        for (int offset = 0; offset < intentCount; offset++) {
            int index = (start + offset) % intentCount;
            IntentCatalog.CompiledIntent intent = intents.get(index);
            boolean active = intent.id().equals(state.activeIntent);
            boolean suspended = state.hasSuspended(intent.id());
            boolean continuation = active || suspended;
            boolean signalled = hasActiveSignal(intent, state, catalog);
            if (!continuation && !signalled) {
                if (evaluated >= maxCandidates) {
                    continue;
                }
                evaluated++;
                if (evaluated == maxCandidates) {
                    budgetCursor = (index + 1) % intentCount;
                }
            }
            // 这里曾有一段按 band 的整段跳过——"低于现胜者的 band 就连分都不用
            // 算"。它成立的前提是 band 先于分数排序，而那个前提正是被
            // `betterThan` 撤掉的东西：现在低 band 的候选完全可以以分数取胜，
            // 跳过它就是替它输掉。剪枝改由 `pruneFloor` 承担——不看 band，只看
            // "还能不能超过现胜者的分数"，对 PRODUCT 聚合这一样便宜。
            double score = score(
                    subject,
                    intent,
                    state.facts,
                    gameTime,
                    pruneFloor(winner, intent, continuation, retainTrace)
            );
            String blocked = blockedReason(
                    intent,
                    state,
                    gameTime,
                    catalog,
                    continuation
            );
            if (blocked != null) {
                addTrace(trace, intent.id(), score, blocked);
                continue;
            }
            if (score < intent.definition().minimumScore()) {
                addTrace(trace, intent.id(), score, "below_minimum");
                continue;
            }
            if (!continuation
                    && !signalled
                    && !intentEvaluationDue(
                            subject,
                            state,
                            intent,
                            gameTime
                    )) {
                addTrace(trace, intent.id(), score, "waiting");
                continue;
            }
            addTrace(trace, intent.id(), score, "eligible");
            ScoredIntent candidate = new ScoredIntent(intent, score);
            if (winner == null || candidate.betterThan(winner)) {
                winner = candidate;
            }
            if (active) {
                state.activeScore = score;
            }
        }
        state.candidateCursor = evaluated < maxCandidates
                ? (start + 1) % intentCount
                : budgetCursor;

        if (winner != null
                && !winner.intent().id().equals(state.activeIntent)
                && !state.hasSuspended(winner.intent().id())
                && !chancePassed(
                        subject,
                        winner.intent(),
                        gameTime
                )) {
            consumeSignals(state, catalog, winner.intent());
            return new Selection(
                    null,
                    retainTrace ? trace : List.of(),
                    "chance_failed:" + winner.intent().id()
            );
        }
        return new Selection(
                winner,
                retainTrace ? trace : List.of(),
                winner == null ? "no_candidate" : "candidate"
        );
    }

    /**
     * 换手要么是**打断**，要么是**赢**，两条路的规矩不同。
     *
     * <p>打断：候选的 band 严格更高、且达到
     * {@link #IMPERATIVE_INTERRUPT_PRIORITY}——外界的要求（战斗、主人的命令）不等
     * 承诺窗口。这是 band 契约里"谁能打断谁"的全部含义。
     *
     * <p>赢：其余一切换手都要付两笔钱——等掉现任的 {@code minimum_commit_ticks}，
     * 再以分数超出 {@code switch_margin}。此前高 band 走的也是免费打断那条路，
     * 于是 COMPANIONSHIP(30) 可以零代价从清扫(10)手里把她拿走再被拿回来，实机
     * 就是"跟着走和捡东西打架"。band 从此不再是插队证，只是打断资格。
     */
    boolean canSwitch(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            ScoredIntent candidate,
            long gameTime
    ) {
        int challenger = candidate.intent().definition().interruptPriority();
        if (challenger > active.definition().interruptPriority()
                && challenger >= IMPERATIVE_INTERRUPT_PRIORITY) {
            return true;
        }
        return gameTime >= state.committedUntilTick
                && candidate.score() >= state.activeScore
                + active.definition().switchMargin();
    }

    static boolean eligible(
            IntentCatalog.CompiledIntent intent,
            double[] facts
    ) {
        for (IntentCatalog.CompiledCondition condition :
                intent.conditions()) {
            if (!condition.condition().test(
                    facts[condition.factIndex()]
            )) {
                return false;
            }
        }
        return true;
    }

    static boolean eligibleWhileActive(
            IntentCatalog.CompiledIntent intent,
            double[] facts,
            IntentCatalog catalog
    ) {
        for (IntentCatalog.CompiledCondition condition :
                intent.conditions()) {
            if (catalog.signalFact(condition.factIndex())) {
                continue;
            }
            if (condition.condition().entryOnly()) {
                // Decided whether she could begin; re-deciding it every tick
                // would cancel her for a distraction she has already left
                // behind.
                continue;
            }
            if (!condition.condition().test(
                    facts[condition.factIndex()]
            )) {
                return false;
            }
        }
        return true;
    }

    static void consumeSignals(
            MaidIntentRuntimeState state,
            IntentCatalog catalog,
            IntentCatalog.CompiledIntent intent
    ) {
        for (IntentCatalog.CompiledCondition condition :
                intent.conditions()) {
            if (catalog.signalFact(condition.factIndex())) {
                state.signals.remove(
                        catalog.facts().get(condition.factIndex())
                );
            }
        }
    }

    private boolean intentEvaluationDue(
            M subject,
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    ) {
        Long next = state.nextIntentEvaluation.get(intent.id());
        if (next == null) {
            int interval = intent.definition().evaluationIntervalTicks();
            // All same-cadence intents for one subject share a phase so a
            // higher-utility tier cannot be skipped by per-intent staggering.
            long hash = mix64(identity.applyAsLong(subject));
            long first = deadline(
                    gameTime,
                    Math.floorMod(hash, interval)
            );
            state.nextIntentEvaluation.put(intent.id(), first);
            next = first;
        }
        if (gameTime < next) {
            return false;
        }
        state.nextIntentEvaluation.put(
                intent.id(),
                deadline(
                        gameTime,
                        intent.definition().evaluationIntervalTicks()
                )
        );
        return true;
    }

    private boolean chancePassed(
            M subject,
            IntentCatalog.CompiledIntent intent,
            long gameTime
    ) {
        double chance = intent.definition().activationChance();
        if (chance >= 1.0D) {
            return true;
        }
        if (chance <= 0.0D) {
            return false;
        }
        long mixed = mix64(
                identity.applyAsLong(subject)
                        ^ Long.rotateLeft(intent.id().hashCode(), 21)
                        ^ gameTime
        );
        double sample = (mixed >>> 11) * 0x1.0p-53;
        return sample < chance;
    }

    private static String blockedReason(
            IntentCatalog.CompiledIntent intent,
            MaidIntentRuntimeState state,
            long gameTime,
            IntentCatalog catalog,
            boolean active
    ) {
        for (IntentCatalog.CompiledCondition condition :
                intent.conditions()) {
            if (active && catalog.signalFact(condition.factIndex())) {
                continue;
            }
            if (!condition.condition().test(
                    state.facts[condition.factIndex()]
            )) {
                return "blocked:" + condition.condition().fact();
            }
        }
        Long cooldown = state.cooldowns.get(intent.id());
        if (cooldown != null && gameTime < cooldown) {
            return "cooldown";
        }
        return null;
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
    private double score(
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

    /**
     * The score an intent must be able to beat, or
     * {@link Double#NEGATIVE_INFINITY} when it has to be scored in full.
     *
     * <p>排序交还给分数之后，这条下界对**所有** band 的候选都成立——不再有"高 band
     * 不用比分"的豁免，也不再有被调用方整段跳过的低 band。
     */
    private double pruneFloor(
            ScoredIntent winner,
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

    private static boolean hasActiveSignal(
            IntentCatalog.CompiledIntent intent,
            MaidIntentRuntimeState state,
            IntentCatalog catalog
    ) {
        for (IntentCatalog.CompiledCondition condition :
                intent.conditions()) {
            if (catalog.signalFact(condition.factIndex())
                    && state.facts[condition.factIndex()] > 0.0D) {
                return true;
            }
        }
        return false;
    }

    private static void addTrace(
            List<IntentTrace.Candidate> trace,
            OrchestrationId intent,
            double score,
            String status
    ) {
        if (trace.size() < MAX_TRACE_CANDIDATES) {
            trace.add(new IntentTrace.Candidate(intent, score, status));
        }
    }

    private static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        return mixed ^ mixed >>> 33;
    }

    record Selection(
            ScoredIntent winner,
            List<IntentTrace.Candidate> trace,
            String status
    ) {
    }

    record ScoredIntent(
            IntentCatalog.CompiledIntent intent,
            double score
    ) {
        /**
         * 分界线以上按等级，以下按分数。
         *
         * <p>此前是全场先比 band 再比分数：band 30 的 0.51 永远赢 band 10 的
         * 0.99——效用层在跨 band 时被结构性关掉，正是 {@code CompanionBand} 的
         * javadoc 警告的那件事（"把偏好写回这些数字，等于关掉效用层"）。护送与
         * 清扫的拉锯从这里来：不是分数没调好，是分数根本没被比较过。
         *
         * <p>但"全场只看分数"也试过，立刻在闸门上翻车：强制档（战斗、主人的命令）
         * 的存在意义就是**不看分**——0.5 分的命令也要立刻拿走她，而按分数排它连
         * 胜者都当不上，{@code canSwitch} 根本见不到它。所以线上线下两种法则：
         * 线上（>= {@code IMPERATIVE_INTERRUPT_PRIORITY}）是外界的要求，压过她
         * 自己的一切安排，彼此间按等级；线下是她自己的安排，只按分数，band 至多
         * 做平手裁决。
         */
        boolean betterThan(ScoredIntent other) {
            boolean imperative = intent.definition().interruptPriority()
                    >= IMPERATIVE_INTERRUPT_PRIORITY;
            boolean otherImperative =
                    other.intent.definition().interruptPriority()
                            >= IMPERATIVE_INTERRUPT_PRIORITY;
            if (imperative != otherImperative) {
                return imperative;
            }
            if (imperative) {
                int priorityOrder = Integer.compare(
                        intent.definition().interruptPriority(),
                        other.intent.definition().interruptPriority()
                );
                if (priorityOrder != 0) {
                    return priorityOrder > 0;
                }
            }
            int scoreOrder = Double.compare(score, other.score);
            if (scoreOrder != 0) {
                return scoreOrder > 0;
            }
            int priorityOrder = Integer.compare(
                    intent.definition().interruptPriority(),
                    other.intent.definition().interruptPriority()
            );
            if (priorityOrder != 0) {
                return priorityOrder > 0;
            }
            return intent.id().compareTo(other.intent.id()) < 0;
        }
    }
}
