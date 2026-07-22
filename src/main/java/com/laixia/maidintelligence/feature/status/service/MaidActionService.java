package com.laixia.maidintelligence.feature.status.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

public final class MaidActionService {
    public static final int HUNGER_PRIORITY = 1;
    public static final int TOOL_PRIORITY = 2;

    private static final int ATTENTION_DURATION_TICKS = 60;
    private static final double OWNER_RANGE_SQUARED = 8.0 * 8.0;

    private final Map<EntityMaid, AttentionState> activeAttention = new WeakHashMap<>();

    public void tick(EntityMaid maid) {
        AttentionState state = activeAttention.get(maid);
        if (state == null) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        long gameTime = maid.level().getGameTime();
        if (gameTime >= state.endGameTime
                || maid.isDeadOrDying()
                || maid.isSleeping()
                || owner == null
                || !owner.isAlive()
                || maid.distanceToSqr(owner) > OWNER_RANGE_SQUARED) {
            stopOwnedAttention(maid);
        }
    }

    public void requestAttention(EntityMaid maid, int priority) {
        if (!(maid.level() instanceof ServerLevel)
                || maid.isDeadOrDying()
                || maid.isSleeping()
                || maid.isUsingItem()) {
            return;
        }

        LivingEntity owner = maid.getOwner();
        if (owner == null || !owner.isAlive() || maid.distanceToSqr(owner) > OWNER_RANGE_SQUARED) {
            return;
        }

        long endGameTime = maid.level().getGameTime() + ATTENTION_DURATION_TICKS;
        AttentionState current = activeAttention.get(maid);
        if (current != null) {
            if (priority >= current.priority) {
                current.priority = priority;
                current.endGameTime = endGameTime;
            }
            maid.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            return;
        }

        if (maid.isBegging()) {
            return;
        }
        maid.getNavigation().stop();
        maid.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        maid.setBegging(true);
        activeAttention.put(maid, new AttentionState(priority, endGameTime));
    }

    private void stopOwnedAttention(EntityMaid maid) {
        AttentionState removed = activeAttention.remove(maid);
        if (removed != null && maid.isBegging()) {
            maid.setBegging(false);
        }
    }

    private static final class AttentionState {
        private int priority;
        private long endGameTime;

        private AttentionState(int priority, long endGameTime) {
            this.priority = priority;
            this.endGameTime = endGameTime;
        }
    }
}
