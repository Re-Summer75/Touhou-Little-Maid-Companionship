package com.laixia.maidintelligence.feature.physics.metadata;

import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Optional per-model authoring data. Names/paths here are explicit references,
 * not naming conventions: an author can call a bone anything and bind it
 * exactly once in the sidecar.
 */
public final class PhysicsMetadata {
    public static final PhysicsMetadata EMPTY = new PhysicsMetadata(
            Mode.AUTO,
            List.of(),
            Set.of(),
            "none"
    );

    private final Mode mode;
    private final List<Chain> chains;
    private final Set<String> excludes;
    private final String origin;

    private PhysicsMetadata(
            Mode mode,
            List<Chain> chains,
            Set<String> excludes,
            String origin
    ) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.chains = List.copyOf(chains);
        this.excludes = Set.copyOf(excludes);
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public Mode mode() {
        return mode;
    }

    public List<Chain> chains() {
        return chains;
    }

    public Set<String> excludes() {
        return excludes;
    }

    public String origin() {
        return origin;
    }

    public boolean isPresent() {
        return this != EMPTY && (!chains.isEmpty() || !excludes.isEmpty() || mode == Mode.EXPLICIT);
    }

    /**
     * Adapter codecs use this factory after validating a versioned sidecar.
     */
    public static PhysicsMetadata of(
            Mode mode,
            List<Chain> chains,
            Set<String> excludes,
            String origin
    ) {
        return new PhysicsMetadata(mode, chains, excludes, origin);
    }

    public enum Mode {
        AUTO,
        EXPLICIT
    }

    public record Chain(
            String id,
            PhysicsBoneSelectionPlan.PartType type,
            List<String> roots,
            List<String> bones,
            Set<String> excludes,
            boolean includeDescendants,
            PhysicsBoneSelectionPlan.SpringProfile profile,
            PhysicsBoneSelectionPlan.ConstraintProfile constraints
    ) {
        public Chain {
            id = Objects.requireNonNull(id, "id");
            type = Objects.requireNonNull(type, "type");
            roots = List.copyOf(roots);
            bones = List.copyOf(bones);
            excludes = Set.copyOf(excludes);
            profile = Objects.requireNonNull(profile, "profile");
            constraints = Objects.requireNonNull(constraints, "constraints");
        }
    }
}
