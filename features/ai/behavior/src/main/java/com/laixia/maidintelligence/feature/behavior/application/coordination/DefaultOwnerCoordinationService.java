package com.laixia.maidintelligence.feature.behavior.application.coordination;

import com.laixia.maidintelligence.feature.behavior.api.OwnerCoordinationService;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationAssignment;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationBid;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationGroupId;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread, deterministic auctions for owner-scoped shared requests.
 */
public final class DefaultOwnerCoordinationService
        implements OwnerCoordinationService {
    public static final int MAX_GROUPS = 128;
    public static final int MAX_REQUESTS_PER_GROUP = 128;
    public static final int MAX_CANDIDATES_PER_GROUP = 64;

    private static final double MAX_UTILITY = 1_000.0D;
    private static final double MAX_FAIRNESS_CREDIT = 2_000.0D;
    private static final double FAIRNESS_AGING_STEP = 25.0D;
    private static final long STALE_GROUP_TICKS = 12_000L;

    private final Thread ownerThread = Thread.currentThread();
    private final Map<OwnerCoordinationGroupId, GroupState> groups =
            new HashMap<>();

    @Override
    public synchronized OwnerCoordinationAssignment assign(
            OwnerCoordinationRequest request,
            List<OwnerCoordinationBid> bids,
            long gameTime
    ) {
        ensureOwnerThread();
        if (!request.active(gameTime)) {
            throw new IllegalArgumentException(
                    "Cannot assign an inactive coordination request"
            );
        }
        List<OwnerCoordinationBid> candidates = normalize(bids);
        prune(gameTime);
        GroupState group = groups.computeIfAbsent(
                request.group(),
                ignored -> createGroup(gameTime)
        );
        group.lastAccessTick = gameTime;

        OwnerCoordinationAssignment existing =
                group.assignments.get(request.requestId());
        if (existing != null) {
            if (!existing.request().equals(request)) {
                throw new IllegalArgumentException(
                        "Request id was reused with different semantics"
                );
            }
            if (!existing.responders().isEmpty()
                    && respondersRemainEligible(existing, candidates)) {
                return existing;
            }
        }

        OwnerCoordinationAssignment resolved = resolve(
                group,
                request,
                candidates,
                gameTime
        );
        group.assignments.put(request.requestId(), resolved);
        trimAssignments(group);
        return resolved;
    }

    @Override
    public synchronized List<OwnerCoordinationAssignment> activeAssignments(
            OwnerCoordinationGroupId groupId,
            long gameTime
    ) {
        ensureOwnerThread();
        prune(gameTime);
        GroupState group = groups.get(groupId);
        if (group == null) {
            return List.of();
        }
        return group.assignments.values().stream()
                .filter(assignment ->
                        assignment.request().active(gameTime))
                .sorted(Comparator
                        .comparingLong(OwnerCoordinationAssignment::decidedAtTick)
                        .thenComparing(assignment ->
                                assignment.request().requestId()))
                .toList();
    }

    @Override
    public synchronized void releaseCandidate(
            UUID maidId,
            long gameTime
    ) {
        ensureOwnerThread();
        for (GroupState group : groups.values()) {
            group.fairness.remove(maidId);
            group.assignments.entrySet().removeIf(entry ->
                    entry.getValue().assigned(maidId));
            group.lastAccessTick = Math.max(
                    group.lastAccessTick,
                    gameTime
            );
        }
    }

    @Override
    public synchronized void clear() {
        ensureOwnerThread();
        groups.clear();
    }

    private OwnerCoordinationAssignment resolve(
            GroupState group,
            OwnerCoordinationRequest request,
            List<OwnerCoordinationBid> candidates,
            long gameTime
    ) {
        List<ScoredBid> scored = new ArrayList<>();
        for (OwnerCoordinationBid bid : candidates) {
            if (!bid.eligible()) {
                continue;
            }
            FairnessState fairness = group.fairness.computeIfAbsent(
                    bid.maidId(),
                    ignored -> new FairnessState(gameTime)
            );
            fairness.lastSeenTick = gameTime;
            scored.add(new ScoredBid(
                    bid,
                    score(
                            request,
                            bid,
                            fairness.credit,
                            activeLoad(
                                    group,
                                    bid.maidId(),
                                    request.requestId(),
                                    gameTime
                            )
                    )
            ));
        }
        scored.sort(Comparator
                .comparingDouble(ScoredBid::score)
                .reversed()
                .thenComparing(candidate ->
                        candidate.bid().maidId()));

        int responderCount = Math.min(request.fanOut(), scored.size());
        List<UUID> responders = scored.stream()
                .limit(responderCount)
                .map(candidate -> candidate.bid().maidId())
                .toList();
        Map<UUID, Double> scores = new HashMap<>();
        scored.forEach(candidate ->
                scores.put(candidate.bid().maidId(), candidate.score()));
        ageFairness(group, scored, new HashSet<>(responders), gameTime);
        trimFairness(group);
        return new OwnerCoordinationAssignment(
                request,
                responders,
                scores,
                gameTime
        );
    }

    private static double score(
            OwnerCoordinationRequest request,
            OwnerCoordinationBid bid,
            double fairnessCredit,
            int activeAssignments
    ) {
        double utility = Math.max(
                -MAX_UTILITY,
                Math.min(MAX_UTILITY, bid.utility())
        );
        double distancePenalty = Math.min(1_000.0D, bid.distance() * 2.0D);
        int effectiveLoad = Math.min(
                100,
                bid.load() + activeAssignments * 10
        );
        return request.priority() * 10_000.0D
                + utility * 10.0D
                - distancePenalty
                - effectiveLoad * 50.0D
                + fairnessCredit;
    }

    private static int activeLoad(
            GroupState group,
            UUID maidId,
            UUID excludedRequest,
            long gameTime
    ) {
        return (int) group.assignments.values().stream()
                .filter(assignment ->
                        !assignment.request().requestId()
                                .equals(excludedRequest))
                .filter(assignment ->
                        assignment.request().active(gameTime))
                .filter(assignment -> assignment.assigned(maidId))
                .count();
    }

    private static void ageFairness(
            GroupState group,
            List<ScoredBid> candidates,
            Set<UUID> responders,
            long gameTime
    ) {
        for (ScoredBid candidate : candidates) {
            FairnessState fairness =
                    group.fairness.get(candidate.bid().maidId());
            fairness.lastSeenTick = gameTime;
            if (responders.contains(candidate.bid().maidId())) {
                fairness.credit = 0.0D;
                fairness.wins++;
            } else {
                fairness.credit = Math.min(
                        MAX_FAIRNESS_CREDIT,
                        fairness.credit + FAIRNESS_AGING_STEP
                );
            }
        }
    }

    private static boolean respondersRemainEligible(
            OwnerCoordinationAssignment assignment,
            List<OwnerCoordinationBid> candidates
    ) {
        Set<UUID> eligible = new HashSet<>();
        candidates.stream()
                .filter(OwnerCoordinationBid::eligible)
                .map(OwnerCoordinationBid::maidId)
                .forEach(eligible::add);
        return eligible.containsAll(assignment.responders());
    }

    private static List<OwnerCoordinationBid> normalize(
            List<OwnerCoordinationBid> bids
    ) {
        List<OwnerCoordinationBid> copy = List.copyOf(bids);
        Set<UUID> seen = new HashSet<>();
        for (OwnerCoordinationBid bid : copy) {
            if (!seen.add(bid.maidId())) {
                throw new IllegalArgumentException(
                        "One maid submitted multiple bids"
                );
            }
        }
        return copy.stream()
                .sorted(Comparator.comparing(OwnerCoordinationBid::maidId))
                .limit(MAX_CANDIDATES_PER_GROUP)
                .toList();
    }

    private void prune(long gameTime) {
        groups.entrySet().removeIf(entry -> {
            GroupState group = entry.getValue();
            group.assignments.entrySet().removeIf(assignment ->
                    !assignment.getValue().request().active(gameTime));
            group.fairness.entrySet().removeIf(candidate ->
                    gameTime - candidate.getValue().lastSeenTick
                            > STALE_GROUP_TICKS);
            return group.assignments.isEmpty()
                    && gameTime - group.lastAccessTick > STALE_GROUP_TICKS;
        });
    }

    private GroupState createGroup(long gameTime) {
        if (groups.size() >= MAX_GROUPS) {
            groups.entrySet().stream()
                    .min(Comparator.<Map.Entry<
                                    OwnerCoordinationGroupId,
                                    GroupState>>comparingLong(entry ->
                                    entry.getValue().lastAccessTick)
                            .thenComparing(Map.Entry::getKey))
                    .ifPresent(entry -> groups.remove(entry.getKey()));
        }
        return new GroupState(gameTime);
    }

    private static void trimAssignments(GroupState group) {
        while (group.assignments.size() > MAX_REQUESTS_PER_GROUP) {
            UUID oldest = group.assignments.values().stream()
                    .min(Comparator.<OwnerCoordinationAssignment>comparingLong(
                                    assignment ->
                                    assignment.request().expiresAtTick())
                            .thenComparing(assignment ->
                                    assignment.request().requestId()))
                    .orElseThrow()
                    .request()
                    .requestId();
            group.assignments.remove(oldest);
        }
    }

    private static void trimFairness(GroupState group) {
        while (group.fairness.size() > MAX_CANDIDATES_PER_GROUP) {
            UUID stalest = group.fairness.entrySet().stream()
                    .min(Comparator.<Map.Entry<
                                    UUID,
                                    FairnessState>>comparingLong(entry ->
                                    entry.getValue().lastSeenTick)
                            .thenComparing(Map.Entry::getKey))
                    .orElseThrow()
                    .getKey();
            group.fairness.remove(stalest);
        }
    }

    private void ensureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "Owner coordination must run on its owner thread"
            );
        }
    }

    private record ScoredBid(OwnerCoordinationBid bid, double score) {
    }

    private static final class FairnessState {
        private double credit;
        private long lastSeenTick;
        private long wins;

        private FairnessState(long gameTime) {
            lastSeenTick = gameTime;
        }
    }

    private static final class GroupState {
        private final Map<UUID, OwnerCoordinationAssignment> assignments =
                new HashMap<>();
        private final Map<UUID, FairnessState> fairness = new HashMap<>();
        private long lastAccessTick;

        private GroupState(long gameTime) {
            lastAccessTick = gameTime;
        }
    }
}
