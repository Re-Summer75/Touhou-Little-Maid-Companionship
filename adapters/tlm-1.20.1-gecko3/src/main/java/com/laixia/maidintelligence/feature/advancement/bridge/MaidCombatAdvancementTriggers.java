package com.laixia.maidintelligence.feature.advancement.bridge;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Reports maid combat outcomes to vanilla advancement criteria.
 */
public interface MaidCombatAdvancementTriggers {
    /**
     * She loosed a crossbow.
     *
     * <p>Separate from the damage reports below because a shot is not a hit —
     * vanilla's own {@code shot_crossbow} fires the moment the bolt leaves,
     * whether or not it lands. It is here rather than absent because firing a
     * crossbow is something she genuinely does, through this mod's own ranged
     * code; the criteria this bridge deliberately leaves unhooked are the ones
     * she has no behaviour for at all, which is a different thing from a
     * capability she has and cannot be credited with.
     */
    void shotCrossbow(EntityMaid maid, ItemStack crossbow);

    /**
     * What one crossbow shot has killed so far.
     *
     * <p>The whole tally each time rather than the newest victim, because the
     * criterion counts distinct types within a single shot — five of them is
     * {@code arbalistic} — and it is the shot that is the unit, not the kill.
     * A multishot crossbow puts three bolts in the air and they all belong to
     * the same shot.
     */
    void killedByCrossbow(EntityMaid maid, java.util.Collection<Entity> victims);

    /**
     * A totem brought her back.
     *
     * <p>The clearest capability gap of the lot: vanilla's death protection is
     * written against {@code LivingEntity}, so a maid holding a totem already
     * cheats death exactly as a player does — the health reset, the effects and
     * the particles all happen for her. Only the criterion sits inside an
     * {@code instanceof ServerPlayer} branch, so the one thing she did not get
     * was the credit for it.
     */
    void usedTotem(EntityMaid maid, ItemStack totem);

    void killedEntity(
            EntityMaid maid,
            Entity victim,
            DamageSource source
    );

    void killedByEntity(
            EntityMaid maid,
            Entity killer,
            DamageSource source
    );

    void hurtEntity(
            EntityMaid maid,
            Entity victim,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    );

    void hurtByEntity(
            EntityMaid maid,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    );
}
