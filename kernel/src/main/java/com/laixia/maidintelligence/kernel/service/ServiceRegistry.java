package com.laixia.maidintelligence.kernel.service;

import java.util.NoSuchElementException;
import java.util.Optional;

public interface ServiceRegistry {
    <T> Optional<T> find(Class<T> contract);

    default <T> T require(Class<T> contract) {
        return find(contract).orElseThrow(() -> new NoSuchElementException(
                "No service registered for " + contract.getName()
        ));
    }
}
