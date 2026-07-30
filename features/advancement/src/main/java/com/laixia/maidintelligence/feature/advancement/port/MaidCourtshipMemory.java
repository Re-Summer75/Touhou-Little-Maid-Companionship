package com.laixia.maidintelligence.feature.advancement.port;

/**
 * Mixin-facing port for the animal-feeding advancement gap.
 */
@FunctionalInterface
public interface MaidCourtshipMemory<S> {
    void rememberCourtedAnimal(S subject, int animalEntityId);
}
