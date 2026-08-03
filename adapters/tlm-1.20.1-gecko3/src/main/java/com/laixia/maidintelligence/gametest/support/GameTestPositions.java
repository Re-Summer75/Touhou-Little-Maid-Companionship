package com.laixia.maidintelligence.gametest.support;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps runtime fixtures inside their allocated GameTest structure.
 */
@SuppressWarnings("null")
public final class GameTestPositions {
    private GameTestPositions() {
    }

    public static Vec3 center(
            GameTestHelper helper,
            int x,
            int y,
            int z
    ) {
        return Vec3.atBottomCenterOf(
                helper.absolutePos(new BlockPos(x, y, z))
        );
    }
}
