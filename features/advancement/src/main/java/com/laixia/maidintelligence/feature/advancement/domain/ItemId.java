package com.laixia.maidintelligence.feature.advancement.domain;

import java.util.Objects;

/**
 * Resource identifier constrained to the item-statistics domain.
 */
public record ItemId(ResourceId resource) implements Comparable<ItemId> {
    public ItemId {
        Objects.requireNonNull(resource, "resource");
    }

    public static ItemId of(String namespace, String path) {
        return new ItemId(new ResourceId(namespace, path));
    }

    public static ItemId parse(String value) {
        return new ItemId(ResourceId.parse(value));
    }

    @Override
    public int compareTo(ItemId other) {
        return resource.compareTo(other.resource);
    }

    @Override
    public String toString() {
        return resource.toString();
    }
}
