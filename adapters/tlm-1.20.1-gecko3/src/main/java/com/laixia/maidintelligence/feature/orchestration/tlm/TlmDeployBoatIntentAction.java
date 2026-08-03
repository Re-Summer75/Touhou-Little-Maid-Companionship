package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationRequest;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmOwnerCoordinationGroups;
import com.laixia.maidintelligence.feature.orchestration.api.CoordinationClaimService;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimRequest;
import com.laixia.maidintelligence.feature.orchestration.domain.claim.CoordinationClaimToken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.Map;

final class TlmDeployBoatIntentAction {
    private static final int CLAIM_TICKS = 40;
    private static final double REUSE_RADIUS = 8.0D;
    private static final int PLACEMENT_RADIUS = 4;

    private final MaidAbilityApi<EntityMaid> abilities;

    TlmDeployBoatIntentAction(MaidAbilityApi<EntityMaid> abilities) {
        this.abilities = java.util.Objects.requireNonNull(
                abilities,
                "abilities"
        );
    }

    ActionResult execute(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)
                || maid.isPassenger()
                || maid.isDeadOrDying()) {
            return ActionResult.FAILED;
        }
        OrchestrationId ability = ability(parameters);
        if (!CompanionAbilityIds.DEPLOY_BOAT.equals(ability)) {
            return ActionResult.FAILED;
        }
        AbilityActivationRequest request = abilities.activeRequest(
                maid,
                ability,
                gameTime
        ).orElse(null);
        if (request == null) {
            return ActionResult.FAILED;
        }
        if (request.source() == AbilityActivationSource.AUTONOMOUS
                && !TlmOwnerCoordinationGroups.assigned(
                maid,
                request.requestId(),
                gameTime
        )) {
            abilities.complete(
                    maid,
                    request.requestId(),
                    false,
                    gameTime
            );
            return ActionResult.FAILED;
        }
        if (!abilities.beginExecution(
                maid,
                request.requestId(),
                gameTime
        )) {
            return ActionResult.FAILED;
        }

        CoordinationClaimService claims =
                TlmCoordinationClaims.service(level);
        CoordinationClaimToken requestClaim = claims.tryClaim(
                new CoordinationClaimRequest(
                        TlmCoordinationClaims.request(
                                level,
                                request.requestId()
                        ),
                        maid.getUUID(),
                        request.requestId(),
                        CLAIM_TICKS
                ),
                gameTime
        ).orElse(null);
        if (requestClaim == null) {
            abilities.complete(
                    maid,
                    request.requestId(),
                    false,
                    gameTime
            );
            return ActionResult.FAILED;
        }

        boolean succeeded = false;
        CoordinationClaimToken placementClaim = null;
        try {
            if (reusableBoat(level, maid) != null) {
                succeeded = true;
                return ActionResult.SUCCEEDED;
            }
            BoatItemTarget item = boatItem(maid);
            BlockPos placement = placement(level, maid);
            if (item == null || placement == null) {
                return ActionResult.FAILED;
            }
            placementClaim = claims.tryClaim(
                    new CoordinationClaimRequest(
                            TlmCoordinationClaims.placement(
                                    level,
                                    placement
                            ),
                            maid.getUUID(),
                            request.requestId(),
                            CLAIM_TICKS
                    ),
                    gameTime
            ).orElse(null);
            if (placementClaim == null
                    || !claims.occupy(
                    requestClaim,
                    gameTime,
                    CLAIM_TICKS
            )
                    || !claims.occupy(
                    placementClaim,
                    gameTime,
                    CLAIM_TICKS
            )
                    || !claims.owns(requestClaim, gameTime)
                    || !claims.owns(placementClaim, gameTime)
                    || !item.valid(maid)) {
                return ActionResult.FAILED;
            }
            Boat boat = createBoat(level, maid, placement, item.variant());
            if (!level.noCollision(boat, boat.getBoundingBox())
                    || !level.addFreshEntity(boat)) {
                return ActionResult.FAILED;
            }

            // Entity creation is the commit point. Consumption follows it so
            // a failed spawn never destroys the maid's item.
            maid.getAvailableInv(false).extractItem(item.slot(), 1, false);
            succeeded = true;
            return ActionResult.SUCCEEDED;
        } finally {
            if (placementClaim != null) {
                claims.release(
                        placementClaim,
                        gameTime,
                        succeeded ? "deployed" : "deploy_failed"
                );
            }
            claims.release(
                    requestClaim,
                    gameTime,
                    succeeded ? "completed" : "deploy_failed"
            );
            abilities.complete(
                    maid,
                    request.requestId(),
                    succeeded,
                    gameTime
            );
        }
    }

    boolean revalidate(
            EntityMaid maid,
            Map<String, String> parameters,
            long gameTime
    ) {
        OrchestrationId ability = ability(parameters);
        return CompanionAbilityIds.DEPLOY_BOAT.equals(ability)
                && abilities.activeRequest(
                maid,
                ability,
                gameTime
        ).filter(request ->
                request.source() != AbilityActivationSource.AUTONOMOUS
                        || TlmOwnerCoordinationGroups.assigned(
                        maid,
                        request.requestId(),
                        gameTime
                )).isPresent();
    }

    private static Boat reusableBoat(
            ServerLevel level,
            EntityMaid maid
    ) {
        Boat nearby = nearestBoat(
                level,
                maid,
                maid.getBoundingBox().inflate(REUSE_RADIUS)
        );
        if (nearby != null || maid.getOwner() == null) {
            return nearby;
        }
        return nearestBoat(
                level,
                maid,
                maid.getOwner().getBoundingBox().inflate(REUSE_RADIUS)
        );
    }

    private static Boat nearestBoat(
            ServerLevel level,
            EntityMaid maid,
            AABB bounds
    ) {
        return level.getEntitiesOfClass(
                Boat.class,
                bounds,
                boat -> boat.isAlive() && boat.getPassengers().isEmpty()
        ).stream().min(Comparator.comparingDouble(
                maid::distanceToSqr
        )).orElse(null);
    }

    private static BoatItemTarget boatItem(EntityMaid maid) {
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            Boat.Type variant = variant(stack);
            if (variant != null) {
                return new BoatItemTarget(
                        slot,
                        stack.copyWithCount(1),
                        variant
                );
            }
        }
        return null;
    }

    private static Boat.Type variant(ItemStack stack) {
        String itemPath = BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .getPath();
        for (Boat.Type variant : Boat.Type.values()) {
            String material = variant.name()
                    .toLowerCase(java.util.Locale.ROOT);
            if (itemPath.equals(material + "_boat")
                    || itemPath.equals(material + "_raft")) {
                return variant;
            }
        }
        return null;
    }

    private static BlockPos placement(
            ServerLevel level,
            EntityMaid maid
    ) {
        BlockPos origin = maid.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int yOffset = -2; yOffset <= 1; yOffset++) {
            for (int x = -PLACEMENT_RADIUS;
                 x <= PLACEMENT_RADIUS;
                 x++) {
                for (int z = -PLACEMENT_RADIUS;
                     z <= PLACEMENT_RADIUS;
                     z++) {
                    BlockPos water = origin.offset(x, yOffset, z);
                    BlockPos spawn = water.above();
                    if (!level.hasChunkAt(water)
                            || !level.getFluidState(water)
                            .is(FluidTags.WATER)
                            || !level.getBlockState(spawn)
                            .getCollisionShape(level, spawn)
                            .isEmpty()) {
                        continue;
                    }
                    double distance = maid.distanceToSqr(
                            spawn.getX() + 0.5D,
                            spawn.getY(),
                            spawn.getZ() + 0.5D
                    );
                    if (distance < bestDistance) {
                        best = spawn.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static Boat createBoat(
            ServerLevel level,
            EntityMaid maid,
            BlockPos placement,
            Boat.Type variant
    ) {
        Boat boat = new Boat(
                level,
                placement.getX() + 0.5D,
                placement.getY(),
                placement.getZ() + 0.5D
        );
        boat.setVariant(variant);
        boat.setYRot(maid.getYRot());
        return boat;
    }

    private static OrchestrationId ability(
            Map<String, String> parameters
    ) {
        try {
            return OrchestrationId.parse(parameters.getOrDefault(
                    "ability_id",
                    ""
            ));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record BoatItemTarget(
            int slot,
            ItemStack fingerprint,
            Boat.Type variant
    ) {
        private boolean valid(EntityMaid maid) {
            var inventory = maid.getAvailableInv(false);
            return slot >= 0
                    && slot < inventory.getSlots()
                    && !inventory.getStackInSlot(slot).isEmpty()
                    && ItemStack.isSameItemSameTags(
                    fingerprint,
                    inventory.getStackInSlot(slot)
            );
        }
    }
}
