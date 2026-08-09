package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/**
 * A measured hostile, still attached to the thing it was measured from.
 *
 * <p>The stable layer decides using {@link ThreatSample} alone, which is what
 * keeps that layer free of Minecraft. But acting on the decision needs the
 * entity back — nothing can be attacked by its distance and health. This pairs
 * the two for exactly as long as it takes to carry a verdict out.
 */
public record ScannedThreat(LivingEntity entity, ThreatSample sample) {
    public ScannedThreat {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(sample, "sample");
    }
}
