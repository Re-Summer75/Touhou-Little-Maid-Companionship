package com.laixia.maidintelligence.feature.physics.client.pose;

import com.laixia.maidintelligence.feature.physics.engine.PhysicsMath;
import com.laixia.maidintelligence.feature.physics.port.PoseDriverPort;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Adapts entity/world wind sampling to the core's model-space driver port.
 */
public final class MaidPoseDriverPort implements PoseDriverPort {
    private final LivingEntity entity;
    private final WorldPoseDriverSource source;
    private final Vector3f worldSignal = new Vector3f();

    public MaidPoseDriverPort(LivingEntity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
        source = WorldPoseDriverSources.create();
    }

    @Override
    public void sampleInto(
            double animationTime,
            float dt,
            boolean paused,
            Vector3f output
    ) {
        source.sampleInto(
                entity,
                animationTime,
                dt,
                paused,
                worldSignal
        );
        float yaw = (float) Math.toRadians(
                PhysicsMath.wrapDegrees(entity.yBodyRot - 180.0F)
        );
        output.set(worldSignal).rotateY(yaw);
    }

    @Override
    public void reset() {
        source.reset();
        worldSignal.zero();
    }
}
