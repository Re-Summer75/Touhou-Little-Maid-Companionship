package com.laixia.maidintelligence.feature.atmosphere.client.wind;

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
                dimensionExposureFloor(dimension)
        );
        float partialTick = (float) (
                animationTick - Math.floor(animationTick)
        );
        float windStrength = EnvironmentalWindField.strength(
                dimensionBase(dimension),
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
                target
        );

        EnvironmentalWindField.smoothInto(
                filtered,
                target,
                dt,
                EnvironmentalWindField.smoothingSeconds(
                        windStrength,
                        instanceHash
                ),
                filtered
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
    }

    public static float dimensionBase(ResourceKey<Level> dimension) {
        return dimensionBase(dimension.location().toString());
    }

    /**
     * Pure lookup used by standalone verification without bootstrapping
     * Minecraft's built-in registries.
     */
    public static float dimensionBase(String dimensionLocation) {
        return switch (dimensionLocation) {
            case "minecraft:the_nether" -> 0.045F;
            case "minecraft:the_end" -> 0.090F;
            case "minecraft:overworld" -> 0.075F;
            default -> 0.060F;
        };
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

    private static float dimensionExposureFloor(
            ResourceKey<Level> dimension
    ) {
        if (Level.NETHER.equals(dimension)) {
            return 0.25F;
        }
        if (Level.END.equals(dimension)) {
            return 0.10F;
        }
        return 0.0F;
    }
}
