package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskCrossBowAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskTridentAttack;
import com.laixia.maidintelligence.feature.behavior.domain.combat.WeaponCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmWeaponScanner;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
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
public final class FreedomMaidTask implements IRangedAttackTask {
    public static final ResourceLocation UID =
            new ResourceLocation(ModResources.MOD_ID, "freedom");

    /*
     * Firing is delegated to the host's own ranged tasks rather than rewritten.
     * What this mod wants to own is the decision — which weapon, at what range,
     * whether to fight at all — and none of that lives in arrow velocity,
     * durability accounting or the bauble hooks these already call correctly.
     * They hold no state, so a shared instance is safe.
     */
    private static final TaskBowAttack BOW = new TaskBowAttack();
    private static final TaskCrossBowAttack CROSSBOW = new TaskCrossBowAttack();
    private static final TaskTridentAttack TRIDENT = new TaskTridentAttack();

    /** Stateless, and consulted every tick to answer whether she panics. */
    private static final TlmWeaponScanner WEAPONS =
            new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

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

    /**
     * She flees only when she has nothing to fight with.
     *
     * <p>Panic is not a mood here, it is a Brain activity that seizes control:
     * {@code MaidPanicTask} fires on {@code isHurt || hasHostile} — merely
     * having a hostile nearby is enough — and on activation it erases
     * {@code WALK_TARGET} and {@code LOOK_TARGET}. With this returning a
     * constant true, the combat action wrote a movement target every tick and
     * panic deleted it every tick, so an armed maid facing a zombie did
     * nothing at all.
     *
     * <p>Answering from her inventory keeps both behaviours intact and makes
     * them exclusive: armed, she fights and panic never starts; unarmed,
     * fleeing is the correct answer and nothing is competing for her feet.
     * Whether an armed maid should nonetheless run from a fight she cannot win
     * is decided later and separately, by the risk policy, which can choose to
     * withdraw without the Brain overriding her.
     */
    @Override
    public boolean enablePanic(EntityMaid maid) {
        for (WeaponCandidate candidate : WEAPONS.scan(maid)) {
            if (candidate.usable()) {
                return false;
            }
        }
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
     * What she can notice, which is the ground every behaviour stands on.
     *
     * <p>This box is scanned by {@code MaidNearestLivingEntitySensor} to fill
     * the visible-entity memory. It used to be overridden to her own bounding
     * box, on the reasoning that a task searching for nothing needs no search
     * box — true until she gained the ability to fight, at which point it was
     * the single thing stopping her. She perceived no entity, hostile pressure
     * was permanently zero, and the combat intent could never fire. She was not
     * refusing to fight; she could not see anything to fight.
     *
     * <p>The inherited default is her restriction radius, and that is not right
     * either: this mod widens that radius to twenty-four blocks while her owner
     * stands still, so perception would silently widen with it and she would
     * leave to deal with things her owner cannot see. Radius decides where she
     * may go; perception decides what she knows about. Only the first is
     * allowed to grow.
     */
    @Override
    public AABB searchDimension(EntityMaid maid) {
        double radius = PerceptionRange.clamp(maid.getRestrictRadius());
        AABB anchor = maid.hasRestriction()
                ? new AABB(maid.getRestrictCenter())
                : maid.getBoundingBox();
        return anchor.inflate(radius, VERTICAL_SEARCH_RANGE, radius);
    }

    /**
     * Fire whatever she is actually holding.
     *
     * <p>The host reaches this method only when the current task implements
     * {@link IRangedAttackTask}, and it is the single door to ranged combat:
     * without it a maid told to shoot silently does nothing at all. The host's
     * own ranged tasks each cover one weapon because a player picks the task
     * per weapon; deciding per shot instead, this dispatches on what is in her
     * hand.
     */
    @Override
    public void performRangedAttack(
            EntityMaid shooter,
            LivingEntity target,
            float distanceFactor
    ) {
        ItemStack weapon = shooter.getMainHandItem();
        if (weapon.getItem() instanceof BowItem) {
            BOW.performRangedAttack(shooter, target, distanceFactor);
        } else if (weapon.getItem() instanceof CrossbowItem) {
            CROSSBOW.performRangedAttack(shooter, target, distanceFactor);
            // Firing empties the bolt list but leaves the "Charged" flag set —
            // vanilla's own crossbow behaviour clears it as a separate step,
            // and skipping that step makes the weapon claim it is still loaded
            // forever. She then re-reads it as ready every tick, pulls the
            // trigger on nothing, and re-cranks: the visible result is a maid
            // twitching at high frequency who never fires.
            CrossbowItem.setCharged(shooter.getMainHandItem(), false);
        } else if (weapon.getItem() instanceof TridentItem) {
            TRIDENT.performRangedAttack(shooter, target, distanceFactor);
        }
        // Anything else is a weapon this mod has no firing routine for. Silence
        // beats guessing: a wrong projectile is worse than none.
    }

    /**
     * Whether she would fight with this, which is broader than melee damage.
     *
     * <p>The host's melee task answers by looking for an attack modifier, so a
     * bow is not a weapon to it. Under free mode a bow very much is one, and
     * saying otherwise here would have the host's own target validity checks
     * disarm her the moment she drew it.
     */
    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem
                || IRangedAttackTask.super.isWeapon(maid, stack);
    }

    @Override
    public String getMaidActionSummary() {
        return "Follow her own judgement instead of a work routine";
    }
}
