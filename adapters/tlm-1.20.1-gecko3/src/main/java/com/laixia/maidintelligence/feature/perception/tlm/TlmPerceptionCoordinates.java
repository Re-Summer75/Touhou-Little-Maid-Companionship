package com.laixia.maidintelligence.feature.perception.tlm;

import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordancePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class TlmPerceptionCoordinates {
    private TlmPerceptionCoordinates() {
    }

    static AffordancePosition at(ServerLevel level, BlockPos position) {
        return new AffordancePosition(
                dimension(level),
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
    }

    static AffordancePosition at(Entity entity) {
        return new AffordancePosition(
                dimension((ServerLevel) entity.level()),
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
    }

    static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    static long deadline(long gameTime, long duration) {
        return gameTime > Long.MAX_VALUE - duration
                ? Long.MAX_VALUE
                : gameTime + duration;
    }
}
