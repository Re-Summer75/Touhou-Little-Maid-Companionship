package com.laixia.maidintelligence.kernel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Startup-owned service registry. Contracts may be installed once and are
 * exposed read-only through {@link ServiceRegistry}.
 */
public final class MutableServiceRegistry implements ServiceRegistry {
    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    private boolean frozen;

    public <T> MutableServiceRegistry register(Class<T> contract, T service) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(service, "service");
        if (frozen) {
            throw new IllegalStateException("Service registry is already frozen");
        }
        if (!contract.isInstance(service)) {
            throw new IllegalArgumentException(
                    service.getClass().getName() + " does not implement "
                            + contract.getName()
            );
        }
        Object previous = services.putIfAbsent(contract, service);
        if (previous != null) {
            throw new IllegalStateException(
                    "Service already registered for " + contract.getName()
            );
        }
        return this;
    }

    /**
     * Closes startup registration before the registry is published to adapters.
     */
    public ServiceRegistry freeze() {
        if (frozen) {
            throw new IllegalStateException("Service registry is already frozen");
        }
        frozen = true;
        return new FrozenRegistry(Map.copyOf(services));
    }

    @Override
    public <T> Optional<T> find(Class<T> contract) {
        Objects.requireNonNull(contract, "contract");
        return Optional.ofNullable(services.get(contract)).map(contract::cast);
    }

    private record FrozenRegistry(Map<Class<?>, Object> services)
            implements ServiceRegistry {
        @Override
        public <T> Optional<T> find(Class<T> contract) {
            Objects.requireNonNull(contract, "contract");
            return Optional.ofNullable(services.get(contract)).map(contract::cast);
        }
    }
}
