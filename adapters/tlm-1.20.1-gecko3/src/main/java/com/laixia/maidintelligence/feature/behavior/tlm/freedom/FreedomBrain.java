package com.laixia.maidintelligence.feature.behavior.tlm.freedom;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAwaitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBreathAirStopTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBreathAirTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearSleepTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClimbTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidInteractWithDoor;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidSwimJumpTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.mixin.tlm.ai.ExtraMaidBrainAccessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.schedule.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * The brain a free-mode maid gets: a body, and us.
 *
 * <p>Free mode is meant to be a blank slate — every judgement hers (ours), none
 * of them the host's. It was not one. The host registers two batches of
 * behaviour that no work mode can opt out of, and they went on deciding when to
 * flee, when to follow, when to eat, when to wander and when to switch activity,
 * while our intent layer decided the same things from the other side. Two
 * deciders for one maid, reconciled by a movement arbiter — which is why the
 * combat timeline kept showing a retreat losing its legs to {@code
 * RANDOM_STROLL}. That was never an arbitration bug.
 *
 * <p>So the split happens where the brain is built rather than inside a dozen
 * behaviours. {@link #register} replaces the host's registration <em>only</em>
 * for free mode; every other mode never reaches this class at all. "Other modes
 * stay vanilla" is then a property of the call graph rather than a promise we
 * re-check in fourteen places and forgot in thirteen of them.
 *
 * <h2>What is kept</h2>
 *
 * <p>Body and execution, not judgement. Swimming, climbing, breathing and doors
 * are reflexes; {@link MoveToTargetSink} is the only thing in the game that
 * turns a {@code WALK_TARGET} into steps, so without it nothing we decide can
 * happen at all; {@link MaidAwaitTask} is the player holding her in place, which
 * outranks anything she might think. Everything else is a decision and comes to
 * us.
 *
 * <h2>Version surface</h2>
 *
 * <p>This is deliberately the <em>only</em> file that names the host's
 * behaviour classes. Porting to another Touhou Little Maid version means
 * reading this list against theirs and nothing else.
 *
 * <p>It is also a keep-list rather than a drop-list, which is the property that
 * matters across versions: a host that adds a new behaviour tomorrow adds it to
 * the modes that want it, and free mode ignores it without anyone noticing it
 * appeared. A drop-list would silently let it in.
 */
public final class FreedomBrain {
    // The host's own priorities, kept identical so a reflex still outranks a
    // decision by the same margin it always did.
    private static final int REFLEX_PRIORITY = 0;
    private static final int AWAIT_PRIORITY = 1;
    private static final int EXECUTION_PRIORITY = 2;
    private static final int CLEANUP_PRIORITY = 99;

    /** Matches the host's, so a maid does not swim differently in free mode. */
    private static final float SWIM_JUMP_CHANCE = 0.8F;

    // LookAtTargetSink's turn rates, likewise copied rather than chosen.
    private static final int LOOK_MINIMUM = 45;
    private static final int LOOK_MAXIMUM = 90;

    private FreedomBrain() {
    }

    /**
     * Build the free-mode brain in place of the host's.
     *
     * <p>Only {@code CORE} carries anything. {@code IDLE} is registered empty
     * because the host makes it the default activity and an activity with no
     * behaviours is the honest way to say "nothing here decides for her" —
     * leaving it unregistered would instead leave her default activity
     * pointing at nothing, which is a different and less obvious state.
     *
     * <p>{@code WORK}, {@code REST}, {@code PANIC} and the ride variants are
     * absent on purpose, and so is the schedule that switches between them:
     * when she works, rests or runs is a decision, and decisions are ours here.
     */
    public static void register(Brain<EntityMaid> brain, EntityMaid maid) {
        brain.addActivity(Activity.CORE, ImmutableList.copyOf(core()));
        brain.addActivity(Activity.IDLE, ImmutableList.of());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    private static List<Pair<Integer, BehaviorControl<? super EntityMaid>>>
            core() {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> behaviors =
                new ArrayList<>();
        behaviors.add(Pair.of(
                REFLEX_PRIORITY, new MaidSwimJumpTask(SWIM_JUMP_CHANCE)
        ));
        behaviors.add(Pair.of(REFLEX_PRIORITY, new MaidClimbTask()));
        behaviors.add(Pair.of(REFLEX_PRIORITY, new MaidBreathAirTask()));
        behaviors.add(Pair.of(REFLEX_PRIORITY, new MaidBreathAirStopTask()));
        behaviors.add(Pair.of(
                REFLEX_PRIORITY, new LookAtTargetSink(LOOK_MINIMUM, LOOK_MAXIMUM)
        ));
        behaviors.add(Pair.of(AWAIT_PRIORITY, new MaidAwaitTask()));
        behaviors.add(Pair.of(
                EXECUTION_PRIORITY, MaidInteractWithDoor.create()
        ));
        behaviors.add(Pair.of(EXECUTION_PRIORITY, new MoveToTargetSink()));
        behaviors.add(Pair.of(CLEANUP_PRIORITY, new MaidClearSleepTask()));
        // Other mods' brain extensions stay. They are something the player
        // installed on purpose, which is not what "free of the host's rules"
        // was about — and our own intent orchestrator arrives through this same
        // door, so dropping extensions would drop the entire point.
        for (IExtraMaidBrain extra
                : ExtraMaidBrainAccessor.maidIntelligence$extensions()) {
            behaviors.addAll(extra.getCoreBehaviors());
        }
        return behaviors;
    }
}
