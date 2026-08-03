package com.laixia.maidintelligence.feature.behavior.application.ability;

import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.port.AbilityCatalogPort;

import java.util.Objects;

public final class MutableAbilityCatalog implements AbilityCatalogPort {
    private volatile AbilityCatalog current = AbilityCatalog.empty();

    @Override
    public AbilityCatalog current() {
        return current;
    }

    public synchronized long nextGeneration() {
        long generation = current.generation();
        return generation == Long.MAX_VALUE ? 1L : generation + 1L;
    }

    public synchronized void publish(AbilityCatalog catalog) {
        current = Objects.requireNonNull(catalog, "catalog");
    }
}
