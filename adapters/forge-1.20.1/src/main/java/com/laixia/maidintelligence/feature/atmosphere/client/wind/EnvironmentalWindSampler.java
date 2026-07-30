package com.laixia.maidintelligence.feature.atmosphere.client.wind;

import com.laixia.maidintelligence.feature.atmosphere.application.EnvironmentalWindField;
import com.laixia.maidintelligence.feature.atmosphere.domain.DimensionWindProfile;
import com.laixia.maidintelligence.feature.physics.client.pose.WorldPoseDriverSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Low-frequency environmental probe plus continuous per-frame wind sampling.
 */
public final class EnvironmentalWindSampler
        implements WorldPoseDriverSource {
    private static final long ENVIRONMENT_INTERVAL_TICKS = 15L;
    private static final int SKY_PROBE_RADIUS = 3;

    private final BlockPos.MutableBlockPos skyProbe =
            new BlockPos.MutableBlockPos();
    private final Vector3f target = new Vector3f();
    private final Vector3f filtered = new Vector3f();

    private long lastEnvironmentTick = Long.MIN_VALUE;
    private ResourceKey<Level> lastDimension;
    private int lastProbeX;
    private int lastProbeY;
    private int lastProbeZ;
    private float exposure;
    private float dimensionBase;
    private float dimensionExposureFloor;

    @Override
    public Vector3f sampleInto(
            LivingEntity entity,
            double animationTick,
            float dt,
            boolean paused,
            Vector3f output
    ) {
        if (paused || !Float.isFinite(dt) || dt <= 0.0F) {
            return output.set(filtered);
        }
        if (!(entity.level() instanceof ClientLevel level)) {
            reset();
            return output.zero();
        }

        ResourceKey<Level> dimension = level.dimension();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            String dimensionId = dimension.location().toString();
            // Profile conversion is cached so the frame path keeps no strings.
            dimensionBase = DimensionWindProfile.baseStrength(dimensionId);
            dimensionExposureFloor =
                    DimensionWindProfile.minimumExposure(dimensionId);
            lastEnvironmentTick = Long.MIN_VALUE;
            target.zero();
            filtered.zero();
        }
        int x = Mth.floor(entity.getX());
        int y = Mth.floor(entity.getEyeY());
        int z = Mth.floor(entity.getZ());
        long gameTime = level.getGameTime();
        if (needsEnvironmentProbe(gameTime, x, y, z)) {
            exposure = sampleSkyExposure(level, x, y, z);
            lastEnvironmentTick = gameTime;
            lastProbeX = x;
            lastProbeY = y;
            lastProbeZ = z;
        }

        float effectiveExposure = Math.max(
                exposure,
                dimensionExposureFloor
        );
        float partialTick = (float) (
                animationTick - Math.floor(animationTick)
        );
        float windStrength = EnvironmentalWindField.strength(
                dimensionBase,
                level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick),
                (float) (entity.getEyeY() - level.getSeaLevel()),
                effectiveExposure,
                entity.isInWaterOrBubble() || entity.isInLava()
        );
        int instanceHash = entity.getUUID().hashCode();
        EnvironmentalWindField.targetInto(
                entity.getX(),
                entity.getZ(),
                gameTime + partialTick,
                dimension.location().hashCode(),
                instanceHash,
                windStrength,
                target,
                JomlWindVectorPort.INSTANCE
        );

        EnvironmentalWindField.smoothInto(
                filtered,
                target,
                dt,
                EnvironmentalWindField.smoothingSeconds(
                        windStrength,
                        instanceHash
                ),
                filtered,
                JomlWindVectorPort.INSTANCE
        );
        return output.set(filtered);
    }

    @Override
    public void reset() {
        target.zero();
        filtered.zero();
        lastEnvironmentTick = Long.MIN_VALUE;
        lastDimension = null;
        exposure = 0.0F;
        dimensionBase = 0.0F;
        dimensionExposureFloor = 0.0F;
    }

    private boolean needsEnvironmentProbe(
            long gameTime,
            int x,
            int y,
            int z
    ) {
        return lastEnvironmentTick == Long.MIN_VALUE
                || gameTime < lastEnvironmentTick
                || gameTime - lastEnvironmentTick
                >= ENVIRONMENT_INTERVAL_TICKS
                || Math.abs(x - lastProbeX) > 1
                || Math.abs(y - lastProbeY) > 1
                || Math.abs(z - lastProbeZ) > 1;
    }

    private float sampleSkyExposure(
            ClientLevel level,
            int x,
            int y,
            int z
    ) {
        int visible = 0;
        visible += canSeeSky(level, x, y, z);
        visible += canSeeSky(level, x + SKY_PROBE_RADIUS, y, z);
        visible += canSeeSky(level, x - SKY_PROBE_RADIUS, y, z);
        visible += canSeeSky(level, x, y, z + SKY_PROBE_RADIUS);
        visible += canSeeSky(level, x, y, z - SKY_PROBE_RADIUS);
        return visible * 0.2F;
    }

    private int canSeeSky(ClientLevel level, int x, int y, int z) {
        skyProbe.set(x, y, z);
        return level.canSeeSky(skyProbe) ? 1 : 0;
    }
}
