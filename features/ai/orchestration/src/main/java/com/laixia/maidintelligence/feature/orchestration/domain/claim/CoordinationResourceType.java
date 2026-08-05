package com.laixia.maidintelligence.feature.orchestration.domain.claim;

public enum CoordinationResourceType {
    CONTAINER_SLOT,
    SEAT,
    PLACEMENT_POINT,
    REQUEST,
    /**
     * A single item lying in the world. Two maids reaching for the same drop
     * is the one race a container slot claim cannot cover, because the item is
     * the resource rather than a place inside one.
     */
    ITEM_ENTITY
}
