package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidFeatAdvancementTriggers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Instance-scoped adapter for the criteria vanilla gates on being a player.
 *
 * <p>Every one of these goes through {@link MaidAdvancementManager#fire}, which
 * owns the binding, the state sync, the recursion cap and the exception
 * boundary. Reaching the mirror outside that path is what produced the 0.0.3
 * crash chain, and nothing here is worth reopening it for.
 */
public final class MaidVanillaFeatCriteria
        implements MaidFeatAdvancementTriggers {
    private final MaidAdvancementManager manager;

    public MaidVanillaFeatCriteria(MaidAdvancementManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    private void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        manager.fire(maid, action);
    }

    @Override
    public void levitated(EntityMaid maid, Vec3 start, int ticks) {
        fire(maid, mirror ->
                CriteriaTriggers.LEVITATION.trigger(mirror, start, ticks));
    }

    @Override
    public void struckByLightning(
            EntityMaid maid,
            LightningBolt bolt,
            List<Entity> nearby
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.LIGHTNING_STRIKE.trigger(mirror, bolt, nearby));
    }

    @Override
    public void channeledLightning(
            EntityMaid maid,
            Collection<Entity> victims
    ) {
        if (victims.isEmpty()) {
            return;
        }
        fire(maid, mirror ->
                CriteriaTriggers.CHANNELED_LIGHTNING.trigger(mirror, victims));
    }

    @Override
    public void hitTarget(
            EntityMaid maid,
            Entity projectile,
            Vec3 at,
            int signal
    ) {
        fire(maid, mirror -> CriteriaTriggers.TARGET_BLOCK_HIT.trigger(
                mirror, projectile, at, signal
        ));
    }

    @Override
    public void killedNearSculkCatalyst(
            EntityMaid maid,
            Entity victim,
            DamageSource source
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST.trigger(
                        mirror, victim, source
                ));
    }

    @Override
    public void startedRiding(EntityMaid maid) {
        fire(maid, CriteriaTriggers.START_RIDING_TRIGGER::trigger);
    }

    @Override
    public void interactedWith(
            EntityMaid maid,
            ItemStack used,
            Entity target
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(
                        mirror, used, target
                ));
    }

    @Override
    public void sleptInBed(EntityMaid maid) {
        fire(maid, CriteriaTriggers.SLEPT_IN_BED::trigger);
    }

    @Override
    public void rodeInLava(EntityMaid maid, Vec3 from) {
        // DistanceTrigger，和 levitation 同一形状：给起点，它自己和当前位置比。
        fire(maid, mirror ->
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(mirror, from));
    }


}
