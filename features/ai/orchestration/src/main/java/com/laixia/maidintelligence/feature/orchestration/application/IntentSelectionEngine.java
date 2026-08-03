package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.api.IntentTrace;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.UtilityModifierPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

final class IntentSelectionEngine<M> {
    private static final int MAX_TRACE_CANDIDATES = 8;
    private static final double MAX_UTILITY_MODIFIER = 50.0D;

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
            double score = score(
                    subject,
                    intent,
                    state.facts,
                    gameTime
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

    boolean canSwitch(
            MaidIntentRuntimeState state,
            IntentCatalog.CompiledIntent active,
            ScoredIntent candidate,
            long gameTime
    ) {
        if (candidate.intent().definition().interruptPriority()
                > active.definition().interruptPriority()) {
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

    private double score(
            M subject,
            IntentCatalog.CompiledIntent intent,
            double[] facts,
            long gameTime
    ) {
        double score = intent.definition().baseScore();
        for (IntentCatalog.CompiledConsideration consideration :
                intent.considerations()) {
            score += consideration.consideration().contribution(
                    facts[consideration.factIndex()]
            );
        }
        double modifier;
        try {
            modifier = modifiers.modifier(subject, intent, gameTime);
        } catch (RuntimeException ignored) {
            modifier = 0.0D;
        }
        if (Double.isFinite(modifier)) {
            score += Math.max(
                    -MAX_UTILITY_MODIFIER,
                    Math.min(MAX_UTILITY_MODIFIER, modifier)
            );
        }
        return score;
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
        boolean betterThan(ScoredIntent other) {
            int priorityOrder = Integer.compare(
                    intent.definition().interruptPriority(),
                    other.intent.definition().interruptPriority()
            );
            if (priorityOrder != 0) {
                return priorityOrder > 0;
            }
            int scoreOrder = Double.compare(score, other.score);
            if (scoreOrder != 0) {
                return scoreOrder > 0;
            }
            return intent.id().compareTo(other.intent.id()) < 0;
        }
    }
}
