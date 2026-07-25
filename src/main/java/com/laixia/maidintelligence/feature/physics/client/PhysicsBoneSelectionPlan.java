package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private final Map<AnimatedGeoBone, BoneKinematics.Metrics> kinematics;

    private PhysicsBoneSelectionPlan(
            String modelId,
            IdentityHashMap<AnimatedGeoBone, Decision> decisions,
            IdentityHashMap<AnimatedGeoBone, String> paths,
            IdentityHashMap<AnimatedGeoBone, BoneKinematics.Metrics> kinematics
    ) {
        this.modelId = modelId;
        this.decisions = Collections.unmodifiableMap(new IdentityHashMap<>(decisions));
        this.paths = Collections.unmodifiableMap(new IdentityHashMap<>(paths));
        this.kinematics = Collections.unmodifiableMap(new IdentityHashMap<>(kinematics));
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

    public BoneKinematics.Metrics kinematics(AnimatedGeoBone bone) {
        return kinematics.get(bone);
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

    public enum StructureRole {
        NONE,
        RIGID_ATTACHMENT_BASE,
        FLEXIBLE_CHAIN_SEGMENT,
        DANGLING_ACCESSORY,
        COMPOUND_SINGLE_BONE
    }

    public record ChainSegment(
            String rootPath,
            int index,
            int count
    ) {
        private static final ChainSegment NONE =
                new ChainSegment("", 0, 0);

        public ChainSegment {
            rootPath = rootPath == null ? "" : rootPath;
            count = Math.max(0, count);
            index = count == 0
                    ? 0
                    : Math.max(0, Math.min(index, count - 1));
        }

        public static ChainSegment none() {
            return NONE;
        }

        public boolean present() {
            return count > 0;
        }

        public float normalizedPosition() {
            return count <= 1 ? 0.0F : (float) index / (count - 1);
        }
    }

    public enum SimulationSpace {
        AUTO,
        HEAD_LOCAL,
        BODY_LOCAL,
        MODEL;

        static SimulationSpace parse(
                String value,
                SimulationSpace fallback
        ) {
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

    public record SwingLimits(
            float left,
            float right,
            float outward,
            float inward
    ) {
        private static final float MAX_LIMIT = 1.55F;

        public SwingLimits {
            left = clampAngle(left);
            right = clampAngle(right);
            outward = clampAngle(outward);
            inward = clampAngle(inward);
        }

        private static SwingLimits defaults(PartType type) {
            float inward = switch (type) {
                case HEAD_SHELL -> 0.08F;
                case HAIR -> 0.28F;
                case EAR -> 0.22F;
                case RIBBON -> 0.45F;
                default -> 0.80F;
            };
            return new SwingLimits(0.80F, 0.80F, 0.80F, inward);
        }

        private static float clampAngle(float value) {
            if (!Float.isFinite(value)) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(MAX_LIMIT, value));
        }
    }

    /**
     * Author-authored collision coordinates in Gecko pixels. Conversion into
     * solver units happens when a runtime collision layout is built.
     */
    public record CollisionVector(float x, float y, float z) {
        public CollisionVector {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)) {
                throw new IllegalArgumentException(
                        "Collision vector components must be finite"
                );
            }
        }
    }

    public sealed interface CollisionShape
            permits CollisionShape.Plane,
            CollisionShape.Sphere,
            CollisionShape.Capsule {
        record Plane(
                CollisionVector point,
                CollisionVector normal
        ) implements CollisionShape {
            public Plane {
                point = Objects.requireNonNull(point, "point");
                normal = Objects.requireNonNull(normal, "normal");
                if (normal.x() == 0.0F
                        && normal.y() == 0.0F
                        && normal.z() == 0.0F) {
                    throw new IllegalArgumentException(
                            "Collision plane normal must be non-zero"
                    );
                }
            }
        }

        record Sphere(
                CollisionVector center,
                float radius
        ) implements CollisionShape {
            public Sphere {
                center = Objects.requireNonNull(center, "center");
                requireRadius(radius);
            }
        }

        record Capsule(
                CollisionVector start,
                CollisionVector end,
                float radius
        ) implements CollisionShape {
            public Capsule {
                start = Objects.requireNonNull(start, "start");
                end = Objects.requireNonNull(end, "end");
                requireRadius(radius);
            }
        }

        private static void requireRadius(float radius) {
            if (!Float.isFinite(radius) || radius < 0.0F) {
                throw new IllegalArgumentException(
                        "Collision radius must be finite and non-negative"
                );
            }
        }
    }

    public record CollisionProxySpec(
            String reference,
            CollisionShape shape,
            Optional<Float> hitRadius
    ) {
        public CollisionProxySpec {
            reference = Objects.requireNonNull(reference, "reference").trim();
            if (reference.isEmpty()) {
                throw new IllegalArgumentException(
                        "Collision reference must not be blank"
                );
            }
            shape = Objects.requireNonNull(shape, "shape");
            hitRadius = hitRadius == null ? Optional.empty() : hitRadius;
            if (hitRadius.isPresent()) {
                float radius = hitRadius.get();
                if (!Float.isFinite(radius) || radius < 0.0F) {
                    throw new IllegalArgumentException(
                            "Collision hit radius must be finite and non-negative"
                    );
                }
            }
        }
    }

    public record CollisionProfile(
            boolean auto,
            boolean segmented,
            List<CollisionProxySpec> proxies
    ) {
        private static final CollisionProfile DEFAULT =
                new CollisionProfile(true, true, List.of());
        private static final CollisionProfile LEGACY =
                new CollisionProfile(true, false, List.of());

        public CollisionProfile(
                boolean auto,
                List<CollisionProxySpec> proxies
        ) {
            this(auto, true, proxies);
        }

        public CollisionProfile {
            proxies = proxies == null ? List.of() : List.copyOf(proxies);
        }

        public static CollisionProfile defaults() {
            return DEFAULT;
        }

        public static CollisionProfile legacyAutomatic() {
            return LEGACY;
        }
    }

    public record ConstraintProfile(
            SimulationSpace simulationSpace,
            float rotationInertiaScale,
            SwingLimits swingLimits,
            boolean backstop,
            boolean headCollision,
            float hitRadiusScale,
            CollisionProfile collision,
            boolean enabled
    ) {
        public ConstraintProfile(
                SimulationSpace simulationSpace,
                float rotationInertiaScale,
                SwingLimits swingLimits,
                boolean backstop,
                boolean headCollision,
                float hitRadiusScale,
                boolean enabled
        ) {
            this(
                    simulationSpace,
                    rotationInertiaScale,
                    swingLimits,
                    backstop,
                    headCollision,
                    hitRadiusScale,
                    CollisionProfile.legacyAutomatic(),
                    enabled
            );
        }

        public ConstraintProfile {
            simulationSpace = simulationSpace == null
                    ? SimulationSpace.AUTO
                    : simulationSpace;
            rotationInertiaScale = clamp01(rotationInertiaScale);
            swingLimits = swingLimits == null
                    ? SwingLimits.defaults(PartType.GENERIC)
                    : swingLimits;
            hitRadiusScale = Float.isFinite(hitRadiusScale)
                    ? Math.max(0.0F, Math.min(4.0F, hitRadiusScale))
                    : 1.0F;
            collision = collision == null
                    ? CollisionProfile.defaults()
                    : collision;
        }

        public static ConstraintProfile defaults(PartType type) {
            float rotationInertia = switch (type) {
                case HEAD_SHELL -> 0.05F;
                case HAIR -> 0.35F;
                case EAR -> 0.15F;
                case RIBBON -> 0.45F;
                case SKIRT, CAPE -> 0.65F;
                default -> 1.0F;
            };
            boolean collideWithHead = type == PartType.HAIR
                    || type == PartType.EAR
                    || type == PartType.RIBBON;
            return new ConstraintProfile(
                    SimulationSpace.AUTO,
                    rotationInertia,
                    SwingLimits.defaults(type),
                    collideWithHead,
                    collideWithHead,
                    1.0F,
                    CollisionProfile.defaults(),
                    true
            );
        }

        public static ConstraintProfile legacy() {
            return new ConstraintProfile(
                    SimulationSpace.MODEL,
                    1.0F,
                    new SwingLimits(1.55F, 1.55F, 1.55F, 1.55F),
                    false,
                    false,
                    1.0F,
                    CollisionProfile.defaults(),
                    false
            );
        }

        private static float clamp01(float value) {
            if (!Float.isFinite(value)) {
                return 1.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, value));
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
                        2.20F, 0.0F, 1.50F, 0.25F, 0.25F, 0.28F, 0.45F
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
            ConstraintProfile constraints,
            StructureRole structureRole,
            ChainSegment chainSegment,
            String reason
    ) {
        public Decision(
                boolean driven,
                PartType type,
                Source source,
                String chainId,
                double confidence,
                SpringProfile profile,
                ConstraintProfile constraints,
                String reason
        ) {
            this(
                    driven,
                    type,
                    source,
                    chainId,
                    confidence,
                    profile,
                    constraints,
                    driven
                            ? StructureRole.FLEXIBLE_CHAIN_SEGMENT
                            : StructureRole.NONE,
                    ChainSegment.none(),
                    reason
            );
        }

        private static final Decision REJECTED = new Decision(
                false,
                PartType.GENERIC,
                Source.NONE,
                "",
                0.0D,
                SpringProfile.defaults(PartType.GENERIC),
                ConstraintProfile.defaults(PartType.GENERIC),
                StructureRole.NONE,
                ChainSegment.none(),
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
            return driven(
                    type,
                    source,
                    chainId,
                    confidence,
                    profile,
                    ConstraintProfile.defaults(type),
                    StructureRole.FLEXIBLE_CHAIN_SEGMENT,
                    reason
            );
        }

        public static Decision driven(
                PartType type,
                Source source,
                String chainId,
                double confidence,
                SpringProfile profile,
                ConstraintProfile constraints,
                String reason
        ) {
            return driven(
                    type,
                    source,
                    chainId,
                    confidence,
                    profile,
                    constraints,
                    StructureRole.FLEXIBLE_CHAIN_SEGMENT,
                    reason
            );
        }

        public static Decision driven(
                PartType type,
                Source source,
                String chainId,
                double confidence,
                SpringProfile profile,
                ConstraintProfile constraints,
                StructureRole structureRole,
                String reason
        ) {
            return new Decision(
                    true,
                    type,
                    source,
                    chainId,
                    confidence,
                    profile,
                    constraints,
                    structureRole == null
                            ? StructureRole.FLEXIBLE_CHAIN_SEGMENT
                            : structureRole,
                    ChainSegment.none(),
                    reason
            );
        }

        public static Decision rejected(
                PartType type,
                Source source,
                double confidence,
                String reason
        ) {
            return rejected(
                    type,
                    source,
                    confidence,
                    StructureRole.NONE,
                    reason
            );
        }

        public static Decision rejected(
                PartType type,
                Source source,
                double confidence,
                StructureRole structureRole,
                String reason
        ) {
            return new Decision(
                    false,
                    type,
                    source,
                    "",
                    confidence,
                    SpringProfile.defaults(type),
                    ConstraintProfile.defaults(type),
                    structureRole == null ? StructureRole.NONE : structureRole,
                    ChainSegment.none(),
                    reason
            );
        }

        public Decision withDynamics(
                SpringProfile updatedProfile,
                ConstraintProfile updatedConstraints,
                StructureRole updatedRole,
                ChainSegment updatedSegment
        ) {
            return new Decision(
                    driven,
                    type,
                    source,
                    chainId,
                    confidence,
                    updatedProfile == null ? profile : updatedProfile,
                    updatedConstraints == null
                            ? constraints
                            : updatedConstraints,
                    updatedRole == null ? structureRole : updatedRole,
                    updatedSegment == null ? chainSegment : updatedSegment,
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
        private final IdentityHashMap<AnimatedGeoBone, BoneKinematics.Metrics> kinematics =
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

        public Builder kinematics(
                AnimatedGeoBone bone,
                BoneKinematics.Metrics metrics
        ) {
            if (metrics != null) {
                kinematics.put(bone, metrics);
            }
            return this;
        }

        public Decision current(AnimatedGeoBone bone) {
            return decisions.get(bone);
        }

        public String pathOf(AnimatedGeoBone bone) {
            return paths.getOrDefault(bone, bone.getName());
        }

        public PhysicsBoneSelectionPlan build() {
            return new PhysicsBoneSelectionPlan(
                    modelId,
                    decisions,
                    paths,
                    kinematics
            );
        }
    }
}
