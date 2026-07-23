package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable, model-local answer to "which bones receive secondary motion".
 *
 * <p>The keys deliberately use bone identity rather than names. Gecko creates a
 * fresh animated skeleton when a maid changes model, and names are neither
 * unique across arbitrary packs nor sufficient to describe a flexible part.
 */
public final class PhysicsBoneSelectionPlan {
    private final String modelId;
    private final Map<AnimatedGeoBone, Decision> decisions;
    private final Map<AnimatedGeoBone, String> paths;

    private PhysicsBoneSelectionPlan(
            String modelId,
            IdentityHashMap<AnimatedGeoBone, Decision> decisions,
            IdentityHashMap<AnimatedGeoBone, String> paths
    ) {
        this.modelId = modelId;
        this.decisions = Collections.unmodifiableMap(new IdentityHashMap<>(decisions));
        this.paths = Collections.unmodifiableMap(new IdentityHashMap<>(paths));
    }

    public String modelId() {
        return modelId;
    }

    public Decision decision(AnimatedGeoBone bone) {
        return decisions.getOrDefault(bone, Decision.REJECTED);
    }

    public boolean isDriven(AnimatedGeoBone bone) {
        return decision(bone).driven();
    }

    public String path(AnimatedGeoBone bone) {
        return paths.getOrDefault(bone, bone.getName());
    }

    public static Builder builder(String modelId) {
        return new Builder(modelId);
    }

    public enum PartType {
        HEAD_SHELL,
        HAIR,
        TAIL,
        EAR,
        SKIRT,
        RIBBON,
        CAPE,
        WING,
        GENERIC;

        static PartType parse(String value, PartType fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public enum Source {
        METADATA("META"),
        AUTO("AUTO"),
        NONE("NONE");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Multipliers over the solver's global constants. Large roots and wings
     * need much smaller angular motion than a thin tail segment.
     */
    public record SpringProfile(
            float stiffnessScale,
            float gravityScale,
            float dragScale,
            float inertiaScale,
            float turnScale,
            float angleScale,
            float tipDisplacementScale
    ) {
        public static SpringProfile defaults(PartType type) {
            return switch (type) {
                case HEAD_SHELL -> new SpringProfile(
                        2.20F, 0.03F, 1.50F, 0.25F, 0.25F, 0.28F, 0.45F
                );
                case HAIR -> new SpringProfile(
                        1.00F, 1.00F, 1.00F, 1.00F, 1.00F, 1.00F, 1.00F
                );
                case TAIL -> new SpringProfile(
                        0.85F, 0.70F, 0.85F, 1.20F, 1.20F, 1.10F, 1.20F
                );
                case EAR -> new SpringProfile(
                        1.40F, 0.35F, 1.25F, 0.60F, 0.70F, 0.55F, 0.65F
                );
                case SKIRT -> new SpringProfile(
                        1.20F, 0.70F, 1.10F, 0.70F, 0.80F, 0.70F, 1.00F
                );
                case RIBBON -> new SpringProfile(
                        0.90F, 0.45F, 0.90F, 1.20F, 1.30F, 1.00F, 0.80F
                );
                case CAPE -> new SpringProfile(
                        0.85F, 0.80F, 0.95F, 1.00F, 1.00F, 0.85F, 1.10F
                );
                case WING -> new SpringProfile(
                        1.60F, 0.15F, 1.30F, 0.45F, 0.80F, 0.40F, 0.70F
                );
                case GENERIC -> new SpringProfile(
                        1.00F, 0.70F, 1.00F, 0.85F, 0.85F, 0.75F, 0.90F
                );
            };
        }

        public SpringProfile multiply(SpringProfile override) {
            return new SpringProfile(
                    stiffnessScale * override.stiffnessScale,
                    gravityScale * override.gravityScale,
                    dragScale * override.dragScale,
                    inertiaScale * override.inertiaScale,
                    turnScale * override.turnScale,
                    angleScale * override.angleScale,
                    tipDisplacementScale * override.tipDisplacementScale
            );
        }

        public static SpringProfile identity() {
            return new SpringProfile(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public record Decision(
            boolean driven,
            PartType type,
            Source source,
            String chainId,
            double confidence,
            SpringProfile profile,
            String reason
    ) {
        private static final Decision REJECTED = new Decision(
                false,
                PartType.GENERIC,
                Source.NONE,
                "",
                0.0D,
                SpringProfile.defaults(PartType.GENERIC),
                "not selected"
        );

        public static Decision driven(
                PartType type,
                Source source,
                String chainId,
                double confidence,
                SpringProfile profile,
                String reason
        ) {
            return new Decision(
                    true,
                    type,
                    source,
                    chainId,
                    confidence,
                    profile,
                    reason
            );
        }

        public static Decision rejected(
                PartType type,
                Source source,
                double confidence,
                String reason
        ) {
            return new Decision(
                    false,
                    type,
                    source,
                    "",
                    confidence,
                    SpringProfile.defaults(type),
                    reason
            );
        }
    }

    public static final class Builder {
        private final String modelId;
        private final IdentityHashMap<AnimatedGeoBone, Decision> decisions =
                new IdentityHashMap<>();
        private final IdentityHashMap<AnimatedGeoBone, String> paths =
                new IdentityHashMap<>();

        private Builder(String modelId) {
            this.modelId = modelId;
        }

        public Builder path(AnimatedGeoBone bone, String path) {
            paths.put(bone, path);
            return this;
        }

        public Builder decide(AnimatedGeoBone bone, Decision decision) {
            decisions.put(bone, decision);
            return this;
        }

        public Decision current(AnimatedGeoBone bone) {
            return decisions.get(bone);
        }

        public String pathOf(AnimatedGeoBone bone) {
            return paths.getOrDefault(bone, bone.getName());
        }

        public PhysicsBoneSelectionPlan build() {
            return new PhysicsBoneSelectionPlan(modelId, decisions, paths);
        }
    }
}
