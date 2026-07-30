package com.laixia.maidintelligence.feature.atmosphere.application;

import com.laixia.maidintelligence.feature.atmosphere.domain.WindVector;
import com.laixia.maidintelligence.feature.atmosphere.port.MutableWindVectorPort;

enum DefaultWindVectorPort implements MutableWindVectorPort<WindVector> {
    INSTANCE;

    @Override
    public WindVector zero(WindVector output) {
        return output.zero();
    }

    @Override
    public WindVector set(
            WindVector output,
            float x,
            float y,
            float z
    ) {
        return output.set(x, y, z);
    }

    @Override
    public WindVector copy(WindVector output, WindVector source) {
        return output.set(source);
    }

    @Override
    public WindVector lerp(
            WindVector output,
            WindVector target,
            float amount
    ) {
        return output.lerp(target, amount);
    }
}
