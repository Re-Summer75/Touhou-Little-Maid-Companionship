package com.laixia.maidintelligence.feature.orchestration.application;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.port.IntentCatalogPort;

import java.util.Objects;

public final class MutableIntentCatalog implements IntentCatalogPort {
    private volatile IntentCatalog current = IntentCatalog.empty();

    @Override
    public IntentCatalog current() {
        return current;
    }

    public synchronized long nextGeneration() {
        long generation = current.generation();
        return generation == Long.MAX_VALUE ? 1L : generation + 1L;
    }

    public synchronized void publish(IntentCatalog catalog) {
        current = Objects.requireNonNull(catalog, "catalog");
    }
}
