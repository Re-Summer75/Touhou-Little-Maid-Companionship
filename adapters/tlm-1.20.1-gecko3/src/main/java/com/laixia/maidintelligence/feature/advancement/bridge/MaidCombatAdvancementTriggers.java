package com.laixia.maidintelligence.feature.advancement.bridge;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * Reports maid combat outcomes to vanilla advancement criteria.
 */
public interface MaidCombatAdvancementTriggers {
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
