package com.laixia.maidintelligence.feature.advancement.bridge;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Deeds vanilla performs for any entity and only ever credits to a player.
 *
 * <p>Gathered apart from the world and combat ports because they share one
 * shape rather than one subject: in every case below the mechanic itself is
 * written against {@code Entity} or {@code LivingEntity} and works for her
 * already — she really is levitating, really is struck by lightning, really
 * does hit the target block — and the only thing inside an {@code instanceof
 * ServerPlayer} branch is the line that writes it down.
 *
 * <p>That distinction is the one this bridge is organised around. Criteria left
 * unhooked are the ones with nothing to hook: brewing, enchanting, trading and
 * the rest need a {@code Player} in the mechanic, not merely at the end of it,
 * so a listener for them could never fire. Everything that has a hook point is
 * hooked, whether or not she does it today.
 */
public interface MaidFeatAdvancementTriggers {
    /** She rode out a levitation effect from where it started. */
    void levitated(EntityMaid maid, Vec3 start, int ticks);

    /** Lightning came down beside her. */
    void struckByLightning(
            EntityMaid maid,
            LightningBolt bolt,
            List<Entity> nearby
    );

    /** Her channelling trident called it down on something. */
    void channeledLightning(EntityMaid maid, Collection<Entity> victims);

    /** One of her projectiles hit a target block. */
    void hitTarget(EntityMaid maid, Entity projectile, Vec3 at, int signal);

    /** She killed something within a sculk catalyst's notice. */
    void killedNearSculkCatalyst(
            EntityMaid maid,
            Entity victim,
            DamageSource source
    );

    /** She got on top of something. */
    void startedRiding(EntityMaid maid);

    /**
     * She went to bed.
     *
     * <p>Hooked on the host rather than on vanilla: a maid does not go through
     * { Player#startSleeping}, so Forge sleep events never see her, but
     * { EntityMaid#startSleeping} is the same moment by another name.
     */
    void sleptInBed(EntityMaid maid);

    /**
     * She is riding something through lava, having set out from here.
     *
     * <p>A distance criterion like levitation, not an event: it compares where
     * the ride began against where she is now, so it wants the start and gets
     * re-asked while the ride lasts.
     */
    void rodeInLava(EntityMaid maid, Vec3 from);

    /**
     * She used something on a creature — feeding an animal, most of the time.
     *
     * <p>Vanilla runs this from {@code Player#interactOn}, which she never goes
     * through; the host feeds animals from its own task instead. The deed is
     * the same one either way.
     */
    void interactedWith(EntityMaid maid, ItemStack used, Entity target);

}
