package com.laixia.maidintelligence.kernel.identity;

import java.util.Objects;
import java.util.UUID;

public record MaidId(UUID value) {
    public MaidId {
        Objects.requireNonNull(value, "value");
    }
}
