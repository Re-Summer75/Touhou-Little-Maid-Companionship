package com.laixia.maidintelligence.platform.geometry;

import com.laixia.maidintelligence.shared.geometry.Vec3d;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Explicit conversion boundary between reusable geometry and Minecraft.
 */
public final class MinecraftGeometryAdapter {
    private MinecraftGeometryAdapter() {
    }

    public static Vec3d toCore(Vec3 vector) {
        Objects.requireNonNull(vector, "vector");
        return new Vec3d(vector.x, vector.y, vector.z);
    }

    public static Vec3 toMinecraft(Vec3d vector) {
        Objects.requireNonNull(vector, "vector");
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
