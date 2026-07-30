package com.laixia.maidintelligence.feature.advancement.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Loader-independent resource identifier.
 */
public record ResourceId(String namespace, String path)
        implements Comparable<ResourceId> {
    private static final Pattern NAMESPACE =
            Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH =
            Pattern.compile("[a-z0-9/._-]+");

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()
                || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException(
                    "invalid resource identifier: " + namespace + ":" + path
            );
        }
    }

    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "resource identifier must contain namespace:path"
            );
        }
        return new ResourceId(
                value.substring(0, separator),
                value.substring(separator + 1)
        );
    }

    @Override
    public int compareTo(ResourceId other) {
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
