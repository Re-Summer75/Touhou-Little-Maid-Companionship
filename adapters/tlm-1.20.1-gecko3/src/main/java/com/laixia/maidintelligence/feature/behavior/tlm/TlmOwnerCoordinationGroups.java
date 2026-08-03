package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.OwnerCoordinationService;
import com.laixia.maidintelligence.feature.behavior.application.coordination.DefaultOwnerCoordinationService;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationAssignment;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationBid;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationGroupId;
import com.laixia.maidintelligence.feature.behavior.domain.coordination.OwnerCoordinationRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Adapts loaded TLM maids into dimension-local owner auctions.
 */
public final class TlmOwnerCoordinationGroups {
    private static final Map<ServerLevel, OwnerCoordinationService> SERVICES =
            new WeakHashMap<>();
    private static final Map<ServerLevel, Map<UUID, Long>> FALLBACKS =
            new WeakHashMap<>();

    private TlmOwnerCoordinationGroups() {
    }

    public static Decision decide(
            EntityMaid requester,
            OrchestrationId purpose,
            int priority,
            int fanOut,
            int episodeTicks,
            long gameTime,
            Predicate<EntityMaid> eligible,
            ToDoubleFunction<EntityMaid> utility
    ) {
        if (!(requester.level() instanceof ServerLevel level)
                || requester.getOwnerUUID() == null
                || episodeTicks < 1
                || episodeTicks
                > OwnerCoordinationRequest.MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException(
                    "A loaded owner and bounded episode are required"
            );
        }
        UUID ownerId = requester.getOwnerUUID();
        String dimension = level.dimension().location().toString();
        long episode = Math.floorDiv(gameTime, episodeTicks);
        long createdAt = multiplySaturated(episode, episodeTicks);
        long expiresAt = addSaturated(createdAt, episodeTicks - 1L);
        UUID requestId = requestId(
                ownerId,
                dimension,
                purpose,
                episode
        );
        OwnerCoordinationRequest request =
                new OwnerCoordinationRequest(
                        requestId,
                        new OwnerCoordinationGroupId(ownerId, dimension),
                        purpose,
                        priority,
                        fanOut,
                        createdAt,
                        expiresAt
                );
        List<OwnerCoordinationBid> bids = bids(
                level,
                ownerId,
                eligible,
                utility
        );
        try {
            OwnerCoordinationAssignment assignment = service(level).assign(
                    request,
                    bids,
                    gameTime
            );
            return new Decision(
                    requestId,
                    assignment.assigned(requester.getUUID()),
                    assignment.responders()
            );
        } catch (IllegalStateException exception) {
            // A coordinator fault fails open for liveness. Every fallback
            // responder still shares requestId, so the Claim remains safe.
            rememberFallback(level, requestId, expiresAt);
            return new Decision(
                    requestId,
                    true,
                    List.of(requester.getUUID())
            );
        }
    }

    public static synchronized boolean assigned(
            EntityMaid maid,
            UUID requestId,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)
                || maid.getOwnerUUID() == null) {
            return false;
        }
        Map<UUID, Long> fallbacks = FALLBACKS.get(level);
        if (fallbacks != null) {
            fallbacks.entrySet().removeIf(entry ->
                    entry.getValue() < gameTime);
            if (fallbacks.containsKey(requestId)) {
                return true;
            }
        }
        OwnerCoordinationService service = SERVICES.get(level);
        if (service == null) {
            return false;
        }
        OwnerCoordinationGroupId group = new OwnerCoordinationGroupId(
                maid.getOwnerUUID(),
                level.dimension().location().toString()
        );
        return service.activeAssignments(group, gameTime).stream()
                .filter(assignment -> assignment.request()
                        .requestId()
                        .equals(requestId))
                .anyMatch(assignment ->
                        assignment.assigned(maid.getUUID()));
    }

    public static synchronized OwnerCoordinationService service(
            ServerLevel level
    ) {
        return SERVICES.computeIfAbsent(
                level,
                ignored -> new DefaultOwnerCoordinationService()
        );
    }

    public static synchronized void release(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level) {
            OwnerCoordinationService service = SERVICES.get(level);
            if (service != null) {
                service.releaseCandidate(
                        maid.getUUID(),
                        level.getGameTime()
                );
            }
        }
    }

    public static synchronized void unload(ServerLevel level) {
        OwnerCoordinationService service = SERVICES.remove(level);
        FALLBACKS.remove(level);
        if (service != null) {
            service.clear();
        }
    }

    public static synchronized void reload() {
        SERVICES.values().forEach(OwnerCoordinationService::clear);
        FALLBACKS.clear();
    }

    private static List<OwnerCoordinationBid> bids(
            ServerLevel level,
            UUID ownerId,
            Predicate<EntityMaid> eligible,
            ToDoubleFunction<EntityMaid> utility
    ) {
        List<OwnerCoordinationBid> bids = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)
                    || !maid.isAlive()
                    || !maid.isTame()
                    || !ownerId.equals(maid.getOwnerUUID())) {
                continue;
            }
            LivingEntity owner = maid.getOwner();
            double distance = owner == null
                    ? Double.MAX_VALUE
                    : Math.sqrt(maid.distanceToSqr(owner));
            if (!Double.isFinite(distance)) {
                distance = 1_000_000.0D;
            }
            double bidUtility = utility.applyAsDouble(maid);
            if (!Double.isFinite(bidUtility)) {
                bidUtility = -1_000.0D;
            }
            bids.add(new OwnerCoordinationBid(
                    maid.getUUID(),
                    eligible.test(maid),
                    bidUtility,
                    distance,
                    load(maid)
            ));
        }
        return List.copyOf(bids);
    }

    private static int load(EntityMaid maid) {
        int load = 0;
        if (maid.getBrain().hasMemoryValue(
                MemoryModuleType.ATTACK_TARGET
        )) {
            load += 80;
        }
        if (maid.isUsingItem()) {
            load += 20;
        }
        if (maid.isPassenger()) {
            load += 10;
        }
        return Math.min(100, load);
    }

    private static UUID requestId(
            UUID ownerId,
            String dimension,
            OrchestrationId purpose,
            long episode
    ) {
        return UUID.nameUUIDFromBytes((
                ownerId
                        + "\u001f" + dimension
                        + "\u001f" + purpose
                        + "\u001f" + episode
        ).getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized void rememberFallback(
            ServerLevel level,
            UUID requestId,
            long expiresAt
    ) {
        Map<UUID, Long> requests = FALLBACKS.computeIfAbsent(
                level,
                ignored -> new java.util.HashMap<>()
        );
        requests.put(requestId, expiresAt);
        while (requests.size()
                > DefaultOwnerCoordinationService.MAX_REQUESTS_PER_GROUP) {
            UUID oldest = requests.entrySet().stream()
                    .min(Map.Entry.<UUID, Long>comparingByValue()
                            .thenComparing(Map.Entry::getKey))
                    .orElseThrow()
                    .getKey();
            requests.remove(oldest);
        }
    }

    private static long multiplySaturated(long value, int factor) {
        try {
            return Math.multiplyExact(value, (long) factor);
        } catch (ArithmeticException exception) {
            return value < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long addSaturated(long value, long amount) {
        if (amount > 0L && value > Long.MAX_VALUE - amount) {
            return Long.MAX_VALUE;
        }
        if (amount < 0L && value < Long.MIN_VALUE - amount) {
            return Long.MIN_VALUE;
        }
        return value + amount;
    }

    public record Decision(
            UUID requestId,
            boolean assigned,
            List<UUID> responders
    ) {
        public Decision {
            responders = List.copyOf(responders);
        }
    }
}
