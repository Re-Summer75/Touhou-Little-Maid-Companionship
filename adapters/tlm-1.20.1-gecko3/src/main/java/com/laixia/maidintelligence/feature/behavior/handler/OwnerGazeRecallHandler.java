package com.laixia.maidintelligence.feature.behavior.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.ContinuousLookTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-authoritative owner gaze detector; no client packet is required.
 */
public final class OwnerGazeRecallHandler {
    private static final double AIM_BOX_INFLATE = 0.2D;

    private final MaidGazeRecallApi<Player, EntityMaid> recall;
    private final Supplier<BehaviorTuning> tuning;
    private final Map<UUID, ContinuousLookTracker> lookTrackers =
            new HashMap<>();

    public OwnerGazeRecallHandler(
            MaidGazeRecallApi<Player, EntityMaid> recall,
            Supplier<BehaviorTuning> tuning
    ) {
        this.recall = Objects.requireNonNull(recall, "recall");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
    }

    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        BehaviorTuning current = currentTuning();
        if (!current.enabled()
                || !player.isAlive()
                || player.isSpectator()) {
            lookTrackers.remove(playerId);
            return;
        }

        EntityMaid lookedAt = findLookedAtMaid(
                player,
                current.gazeRecallRange()
        );
        ContinuousLookTracker tracker = lookTrackers.computeIfAbsent(
                playerId,
                ignored -> new ContinuousLookTracker()
        );
        int targetId = lookedAt == null ? -1 : lookedAt.getId();
        if (tracker.observe(targetId, current.gazeRecallHoldTicks())
                && !recall.tryRecall(player, lookedAt)) {
            // Failed eligibility must require another complete hold interval.
            tracker.reset();
        }
    }

    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lookTrackers.remove(event.getEntity().getUUID());
    }

    public static EntityMaid findLookedAtMaid(
            Player player,
            double range
    ) {
        if (range <= 0.0D
                || !Double.isFinite(range)
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }

        Vec3 start = player.getEyePosition();
        Vec3 ray = player.getLookAngle().normalize().scale(range);
        Vec3 end = start.add(ray);
        AABB searchBounds = player.getBoundingBox()
                .expandTowards(ray)
                .inflate(AIM_BOX_INFLATE);
        EntityMaid nearest = null;
        double nearestDistanceSquared = range * range;

        for (EntityMaid maid : level.getEntitiesOfClass(
                EntityMaid.class,
                searchBounds,
                maid -> maid.isAlive()
                        && maid.isTame()
                        && player.getUUID().equals(maid.getOwnerUUID())
        )) {
            // The gesture intentionally tracks the aim ray through blocks.
            Vec3 hit = maid.getBoundingBox()
                    .inflate(AIM_BOX_INFLATE)
                    .clip(start, end)
                    .orElse(null);
            if (hit == null) {
                continue;
            }
            double distanceSquared = start.distanceToSqr(hit);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = maid;
            }
        }
        return nearest;
    }

    private BehaviorTuning currentTuning() {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }
}
