package com.laixia.maidintelligence.feature.physics.client.pose;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Registration boundary for optional procedural atmosphere animation.
 */
public final class WorldPoseDriverSources {
    private static final Map<String,
            Supplier<? extends WorldPoseDriverSource>> FACTORIES =
            new LinkedHashMap<>();

    private WorldPoseDriverSources() {
    }

    public static synchronized void register(
            String id,
            Supplier<? extends WorldPoseDriverSource> factory
    ) {
        String normalizedId = Objects.requireNonNull(id, "id").trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pose driver source id must not be blank"
            );
        }
        FACTORIES.put(
                normalizedId,
                Objects.requireNonNull(factory, "factory")
        );
    }

    public static synchronized WorldPoseDriverSource create() {
        if (FACTORIES.isEmpty()) {
            return NoPoseDriver.INSTANCE;
        }
        WorldPoseDriverSource[] sources =
                new WorldPoseDriverSource[FACTORIES.size()];
        int index = 0;
        for (Supplier<? extends WorldPoseDriverSource> factory
                : FACTORIES.values()) {
            sources[index++] = Objects.requireNonNull(
                    factory.get(),
                    "Pose driver source factory returned null"
            );
        }
        return sources.length == 1
                ? sources[0]
                : new CompositePoseDriver(sources);
    }

    private enum NoPoseDriver implements WorldPoseDriverSource {
        INSTANCE;

        @Override
        public Vector3f sampleInto(
                LivingEntity entity,
                double animationTick,
                float dt,
                boolean paused,
                Vector3f output
        ) {
            return output.zero();
        }

        @Override
        public void reset() {
        }
    }

    private static final class CompositePoseDriver
            implements WorldPoseDriverSource {
        private final WorldPoseDriverSource[] sources;
        private final Vector3f sampled = new Vector3f();

        private CompositePoseDriver(WorldPoseDriverSource[] sources) {
            this.sources = sources;
        }

        @Override
        public Vector3f sampleInto(
                LivingEntity entity,
                double animationTick,
                float dt,
                boolean paused,
                Vector3f output
        ) {
            output.zero();
            for (WorldPoseDriverSource source : sources) {
                source.sampleInto(
                        entity,
                        animationTick,
                        dt,
                        paused,
                        sampled
                );
                output.add(sampled);
            }
            return output;
        }

        @Override
        public void reset() {
            sampled.zero();
            for (WorldPoseDriverSource source : sources) {
                source.reset();
            }
        }
    }
}
