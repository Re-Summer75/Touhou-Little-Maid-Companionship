package com.laixia.maidintelligence.platform.runtime;

import com.laixia.maidintelligence.kernel.service.ServiceRegistry;

import java.util.Objects;

/**
 * Runtime boundary for static Forge packet and Mixin entrypoints that cannot
 * receive constructor injection.
 */
public final class AdapterRuntime {
    private static volatile ServiceRegistry services;

    private AdapterRuntime() {
    }

    public static synchronized void install(ServiceRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (services != null) {
            throw new IllegalStateException("Adapter runtime is already installed");
        }
        services = registry;
    }

    public static ServiceRegistry services() {
        ServiceRegistry current = services;
        if (current == null) {
            throw new IllegalStateException("Adapter runtime has not been installed");
        }
        return current;
    }

    public static <T> T require(Class<T> contract) {
        return services().require(contract);
    }
}
