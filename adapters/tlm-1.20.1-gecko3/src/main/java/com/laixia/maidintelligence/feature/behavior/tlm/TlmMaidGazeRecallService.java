package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * TLM implementation of the explicit gaze-recall behavior.
 */
public final class TlmMaidGazeRecallService
        implements MaidGazeRecallApi<Player, EntityMaid> {
    private static final float APPROACH_SPEED = 0.6F;

    private final GazeRecallPolicy policy;

    public TlmMaidGazeRecallService(GazeRecallPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public boolean tryRecall(Player owner, EntityMaid maid) {
        if (!validPair(owner, maid)) {
            return false;
        }

        boolean passiveSeat = isPassiveTlmSeat(maid.getVehicle());
        boolean canMove = !maid.isMaidInSittingPose()
                && !maid.isOrderedToSit()
                && !maid.isSleeping()
                && !maid.isLeashed()
                && (!maid.isPassenger() || passiveSeat);
        if (!policy.eligible(
                maid.getFavorabilityManager().getLevel(),
                !maid.isHomeModeEnable(),
                canMove
        )) {
            return false;
        }

        if (passiveSeat) {
            maid.stopRiding();
        }
        if (!maid.canBrainMoving()) {
            return false;
        }

        int closeEnough = policy.closeEnoughDistance();
        if (maid.distanceToSqr(owner) <= closeEnough * closeEnough) {
            return true;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        BehaviorUtils.setWalkAndLookTargetMemories(
                maid,
                owner,
                APPROACH_SPEED,
                closeEnough
        );
        return true;
    }

    private static boolean validPair(Player owner, EntityMaid maid) {
        return owner != null
                && maid != null
                && owner.isAlive()
                && !owner.isSpectator()
                && maid.isAlive()
                && owner.level() == maid.level()
                && maid.isTame()
                && owner.getUUID().equals(maid.getOwnerUUID());
    }

    private static boolean isPassiveTlmSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }
}
