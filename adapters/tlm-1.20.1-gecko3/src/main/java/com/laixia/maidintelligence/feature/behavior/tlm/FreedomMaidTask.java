package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

import java.util.List;

/**
 * The task that hands the wheel over.
 *
 * <p>Every other task fills a maid's brain with behaviours that decide where
 * she goes and what she reaches for. Companion intents have to contend with
 * those, and the arbitration that makes contending safe — leases, fail-open,
 * occupancy levels — necessarily yields to them, because a work task is the
 * player's explicit instruction and this mod's guesses are not.
 *
 * <p>So rather than fight for room inside a task built for something else,
 * this one brings nothing of its own. {@link #createBrainTasks} is empty by
 * design: under it the only thing proposing what she should do is the
 * companion orchestrator, and nothing has to be pre-empted for it to act.
 *
 * <p>What is deliberately kept: she still panics, still eats, and still looks
 * around and wanders when nothing better is going on. Removing those would not
 * make her freer, only emptier — a maid frozen in place between decisions reads
 * as broken rather than as idle.
 */
public final class FreedomMaidTask implements IMaidTask {
    public static final ResourceLocation UID =
            new ResourceLocation(ModResources.MOD_ID, "freedom");

    /**
     * A compass: she is the one deciding which way to go. The vanilla idle task
     * already holds the feather.
     */
    @Override
    public ItemStack getIcon() {
        return Items.COMPASS.getDefaultInstance();
    }

    /**
     * Silent. The idle chatter belongs to a maid with nothing to do, and one
     * deciding for herself is not the same thing — a line every few seconds
     * would read as her being bored rather than occupied.
     */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * Empty, and that is the entire point of this task. Anything returned here
     * would be a second opinion competing with the orchestrator's.
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>>
            createBrainTasks(EntityMaid maid) {
        return List.of();
    }

    /** Idling visibly beats standing still between decisions. */
    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return true;
    }

    /** Fleeing danger is not a decision worth taking away from her. */
    @Override
    public boolean enablePanic(EntityMaid maid) {
        return true;
    }

    /** Required for her to eat at all, including from the floor. */
    @Override
    public boolean enableEating(EntityMaid maid) {
        return true;
    }

    /** There is no work point, because there is no work. */
    @Override
    public boolean workPointTask(EntityMaid maid) {
        return false;
    }

    /**
     * Nothing here searches, so the usual work-radius box would only be a
     * misleading thing to draw. Kept to her own footprint.
     */
    @Override
    public AABB searchDimension(EntityMaid maid) {
        return maid.getBoundingBox();
    }

    @Override
    public String getMaidActionSummary() {
        return "Follow her own judgement instead of a work routine";
    }
}
