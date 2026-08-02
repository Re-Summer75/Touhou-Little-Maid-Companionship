package com.laixia.maidintelligence.feature.orchestration.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record OrchestrationId(String namespace, String path)
        implements Comparable<OrchestrationId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public OrchestrationId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "Invalid orchestration namespace: " + namespace
            );
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "Invalid orchestration path: " + path
            );
        }
    }

    public static OrchestrationId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1
                || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "Orchestration id must be namespace:path: " + value
            );
        }
        return new OrchestrationId(
                value.substring(0, separator),
                value.substring(separator + 1)
        );
    }

    @Override
    public int compareTo(OrchestrationId other) {
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0
                ? namespaceOrder
                : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
