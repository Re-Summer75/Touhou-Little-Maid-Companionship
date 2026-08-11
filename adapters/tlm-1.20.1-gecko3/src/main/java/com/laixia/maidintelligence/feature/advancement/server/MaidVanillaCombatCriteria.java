package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidCombatAdvancementTriggers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Instance-scoped adapter for vanilla combat criteria.
 */
public final class MaidVanillaCombatCriteria
        implements MaidCombatAdvancementTriggers {
    private final MaidAdvancementManager manager;

    public MaidVanillaCombatCriteria(MaidAdvancementManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    private void fire(EntityMaid maid, Consumer<MaidMirrorPlayer> action) {
        manager.fire(maid, action);
    }

    @Override
    public void shotCrossbow(EntityMaid maid, ItemStack crossbow) {
        // Through manager.fire like every other trigger here, which is the
        // only safe way to touch the mirror: it owns the binding, the state
        // sync, the recursion cap and the exception boundary. The 0.0.3 crash
        // chain came from reaching the mirror outside that path.
        fire(maid, mirror ->
                CriteriaTriggers.SHOT_CROSSBOW.trigger(mirror, crossbow));
    }

    @Override
    public void killedByCrossbow(
            EntityMaid maid,
            java.util.Collection<Entity> victims
    ) {
        if (victims.isEmpty()) {
            return;
        }
        fire(maid, mirror ->
                CriteriaTriggers.KILLED_BY_CROSSBOW.trigger(mirror, victims));
    }

    @Override
    public void usedTotem(EntityMaid maid, ItemStack totem) {
        fire(maid, mirror ->
                CriteriaTriggers.USED_TOTEM.trigger(mirror, totem));
    }

    @Override
    public void killedEntity(
            EntityMaid maid,
            Entity victim,
            DamageSource source
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(
                        mirror,
                        victim,
                        source
                ));
    }

    @Override
    public void killedByEntity(
            EntityMaid maid,
            Entity killer,
            DamageSource source
    ) {
        fire(maid, mirror ->
                CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger(
                        mirror,
                        killer,
                        source
                ));
    }

    @Override
    public void hurtEntity(
            EntityMaid maid,
            Entity victim,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    ) {
        fire(maid, mirror -> CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(
                mirror,
                victim,
                source,
                dealt,
                taken,
                blocked
        ));
    }

    @Override
    public void hurtByEntity(
            EntityMaid maid,
            DamageSource source,
            float dealt,
            float taken,
            boolean blocked
    ) {
        fire(maid, mirror -> CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(
                mirror,
                source,
                dealt,
                taken,
                blocked
        ));
    }
}
