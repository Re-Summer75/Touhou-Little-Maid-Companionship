package com.laixia.maidintelligence.compat.tlm;

import java.util.List;
import java.util.Objects;

/**
 * Bridges TLM's reflection-created extension to the distribution-owned adapter
 * modules. The immutable module list may be installed exactly once.
 */
public final class TlmAdapterRegistry {
    private static volatile List<TlmFeatureModule> modules;

    private TlmAdapterRegistry() {
    }

    public static synchronized void install(
            List<? extends TlmFeatureModule> installedModules
    ) {
        Objects.requireNonNull(installedModules, "installedModules");
        if (modules != null) {
            throw new IllegalStateException("TLM adapters are already installed");
        }
        modules = List.copyOf(installedModules);
    }

    public static List<TlmFeatureModule> modules() {
        List<TlmFeatureModule> current = modules;
        if (current == null) {
            throw new IllegalStateException("TLM adapters have not been installed");
        }
        return current;
    }
}
