package com.laixia.maidintelligence.feature.behavior.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.GazeGestureTiming;
import com.laixia.maidintelligence.feature.behavior.domain.GazeGestureTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-authoritative owner gaze gesture; no client packet is required.
 *
 * <p>Two stages. Resting the aim on a maid makes her look back — visible,
 * immediate, and committing nothing, which is the whole interaction for simply
 * wanting to look at her. Moving that aim to a spot near yourself while she is
 * watching is what calls her over.
 *
 * <p>What was here before — hold the crosshair on her for two ticks — could not
 * tell those two wants apart, because looking at her is the same act in both.
 * This can, because someone who only wanted to look never moves their view off
 * her.
 */
public final class OwnerGazeRecallHandler {
    /**
     * Aim tolerance beyond the maid's own angular size. Her apparent width
     * already shrinks with distance, so adding a fixed angle on top keeps a
     * distant maid reachable without making a nearby one impossible to avoid —
     * which is the failing the old bounding-box ray had exactly backwards.
     */
    private static final double AIM_MARGIN_RADIANS = Math.toRadians(6.0D);

    /** Past this, aim is not "at" her however wide she looks. */
    private static final double MAX_AIM_RADIANS = Math.toRadians(35.0D);

    private static final double MIN_HALF_WIDTH = 0.3D;

    private static final double DESTINATION_RANGE = 12.0D;

    /**
     * How close to the owner a pointed-at spot must be. Until an action exists
     * that walks to an arbitrary position, the gesture means "come here", so
     * pointing across a field should do nothing rather than something almost
     * right.
     */
    private static final double DESTINATION_NEAR_OWNER = 5.0D;

    /** Refreshed every tick the gesture is open, so it lapses on its own. */
    private static final int LOOK_LOCK_TICKS = 20;

    private final MaidGazeRecallApi<Player, EntityMaid> recall;
    private final Supplier<BehaviorTuning> tuning;
    private final Map<UUID, GazeGestureTracker> gestures = new HashMap<>();

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
            gestures.remove(playerId);
            return;
        }

        EntityMaid aimedAt = findAimedAtMaid(player, current.gazeRecallRange());
        GazeGestureTracker gesture = gestures.computeIfAbsent(
                playerId,
                ignored -> new GazeGestureTracker()
        );
        // The destination ray is only cast once a gesture is open and the aim
        // has left her, so walking about never pays for one.
        long destination = gesture.acknowledged() && aimedAt == null
                ? destinationKey(player)
                : GazeGestureTracker.NO_DESTINATION;
        GazeGestureTracker.Signal signal = gesture.observe(
                aimedAt == null ? -1 : aimedAt.getId(),
                destination,
                timing(current)
        );
        switch (signal) {
            case ACKNOWLEDGED -> lookBack(player, gesture.targetId());
            case SUMMON -> summon(player, gesture.targetId());
            default -> {
            }
        }
    }

    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        gestures.remove(event.getEntity().getUUID());
    }

    private void summon(ServerPlayer player, int maidId) {
        EntityMaid maid = ownedMaid(player, maidId);
        if (maid == null || !recall.tryRecall(player, maid)) {
            // Ineligible right now: the gesture has to be made again rather
            // than sitting armed and going off later by itself.
            gestures.remove(player.getUUID());
        }
    }

    /**
     * Locks her gaze onto the owner for a moment. Refreshed rather than held,
     * so nothing has to remember to release it, and skipped while she has
     * something to fight — being watched attentively mid-combat would read as a
     * bug rather than as attention.
     */
    private void lookBack(ServerPlayer player, int maidId) {
        EntityMaid maid = ownedMaid(player, maidId);
        if (maid == null || maid.getTarget() != null) {
            return;
        }
        maid.getBrain().setMemoryWithExpiry(
                MemoryModuleType.LOOK_TARGET,
                new EntityTracker(player, true),
                LOOK_LOCK_TICKS
        );
    }

    private EntityMaid ownedMaid(ServerPlayer player, int maidId) {
        if (maidId < 0
                || !(player.level().getEntity(maidId)
                instanceof EntityMaid maid)) {
            return null;
        }
        return maid.isAlive()
                && maid.isTame()
                && player.getUUID().equals(maid.getOwnerUUID())
                ? maid
                : null;
    }

    /**
     * The maid the owner is most precisely aimed at — most centred rather than
     * nearest, because the gesture is about singling one of them out.
     */
    public static EntityMaid findAimedAtMaid(Player player, double range) {
        if (range <= 0.0D
                || !Double.isFinite(range)
                || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB searchBounds = player.getBoundingBox().inflate(range);
        List<EntityMaid> candidates = level.getEntitiesOfClass(
                EntityMaid.class,
                searchBounds,
                maid -> maid.isAlive()
                        && maid.isTame()
                        && player.getUUID().equals(maid.getOwnerUUID())
        );
        EntityMaid best = null;
        double bestCosine = -1.0D;
        for (EntityMaid maid : candidates) {
            Vec3 offset = maid.getEyePosition().subtract(eye);
            double distance = offset.length();
            if (distance < 1.0E-4D || distance > range) {
                continue;
            }
            double cosine = look.dot(offset.scale(1.0D / distance));
            if (cosine <= bestCosine
                    || cosine < allowedCosine(maid, distance)) {
                continue;
            }
            bestCosine = cosine;
            best = maid;
        }
        return best;
    }

    private static double allowedCosine(EntityMaid maid, double distance) {
        double halfWidth = Math.max(MIN_HALF_WIDTH, maid.getBbWidth() * 0.5D);
        double allowed = Math.atan2(halfWidth, distance) + AIM_MARGIN_RADIANS;
        return Math.cos(Math.min(allowed, MAX_AIM_RADIANS));
    }

    /**
     * The block the owner is pointing at, when it is somewhere she could be
     * called to.
     */
    private static long destinationKey(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(DESTINATION_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return GazeGestureTracker.NO_DESTINATION;
        }
        BlockPos pos = hit.getBlockPos();
        double reach = DESTINATION_NEAR_OWNER * DESTINATION_NEAR_OWNER;
        return pos.distToCenterSqr(player.position()) > reach
                ? GazeGestureTracker.NO_DESTINATION
                : pos.asLong();
    }

    private static GazeGestureTiming timing(BehaviorTuning tuning) {
        return GazeGestureTiming.defaults()
                .withAcknowledgeTicks(tuning.gazeRecallHoldTicks());
    }

    private BehaviorTuning currentTuning() {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }
}
