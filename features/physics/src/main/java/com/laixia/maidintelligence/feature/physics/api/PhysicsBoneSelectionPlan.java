package com.laixia.maidintelligence.feature.physics.api;

import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.BoneKinematics;

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
    private final Map<BoneModelSnapshot.Bone, Decision> decisions;
    private final Map<BoneModelSnapshot.Bone, String> paths;
    private final Map<BoneModelSnapshot.Bone, BoneKinematics.Metrics> kinematics;

    PhysicsBoneSelectionPlan(
            String modelId,
            IdentityHashMap<BoneModelSnapshot.Bone, Decision> decisions,
            IdentityHashMap<BoneModelSnapshot.Bone, String> paths,
            IdentityHashMap<BoneModelSnapshot.Bone, BoneKinematics.Metrics> kinematics
    ) {
        this.modelId = modelId;
        this.decisions = Collections.unmodifiableMap(
                new IdentityHashMap<>(decisions)
        );
        this.paths = Collections.unmodifiableMap(new IdentityHashMap<>(paths));
        this.kinematics = Collections.unmodifiableMap(
                new IdentityHashMap<>(kinematics)
        );
    }

    public String modelId() {
        return modelId;
    }
    public Decision decision(BoneModelSnapshot.Bone bone) {
        return decisions.getOrDefault(bone, Decision.REJECTED);
    }
    public boolean isDriven(BoneModelSnapshot.Bone bone) {
        return decision(bone).driven();
    }
    public String path(BoneModelSnapshot.Bone bone) {
        return paths.getOrDefault(bone, bone.getName());
    }
    public BoneKinematics.Metrics kinematics(BoneModelSnapshot.Bone bone) {
        return kinematics.get(bone);
    }
    public static Builder builder(String modelId) {
        return new Builder(modelId);
    }

    public enum PartType {
        HEAD_SHELL, HAIR, TAIL, EAR, SKIRT, RIBBON, CAPE, WING, GENERIC;

        public static PartType parse(String value, PartType fallback) {
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
        METADATA("META"), AUTO("AUTO"), NONE("NONE");

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

    public record ChainSegment(String rootPath, int index, int count) {
        private static final ChainSegment NONE = new ChainSegment("", 0, 0);

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
        AUTO, HEAD_LOCAL, BODY_LOCAL, MODEL;

        static SimulationSpace parse(String value, SimulationSpace fallback) {
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
            float left, float right, float outward, float inward
    ) {
        private static final float MAX_LIMIT = 1.55F;

        public SwingLimits {
            left = clampAngle(left); right = clampAngle(right);
            outward = clampAngle(outward); inward = clampAngle(inward);
        }

        private static SwingLimits defaults(PartType type) {
            return PhysicsProfileDefaults.swingLimits(type);
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
            PhysicsCollisionValidation.vector(x, y, z);
        }
    }

    public sealed interface CollisionShape
            permits CollisionShape.Plane, CollisionShape.Sphere,
            CollisionShape.Capsule {
        record Plane(
                CollisionVector point,
                CollisionVector normal
        ) implements CollisionShape {
            public Plane {
                point = Objects.requireNonNull(point, "point");
                normal = Objects.requireNonNull(normal, "normal");
                PhysicsCollisionValidation.normal(normal);
            }
        }

        record Sphere(
                CollisionVector center,
                float radius
        ) implements CollisionShape {
            public Sphere {
                center = Objects.requireNonNull(center, "center");
                PhysicsCollisionValidation.radius(
                        radius,
                        "Collision radius must be finite and non-negative"
                );
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
                PhysicsCollisionValidation.radius(
                        radius,
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
            reference = PhysicsCollisionValidation.reference(reference);
            shape = Objects.requireNonNull(shape, "shape");
            hitRadius = PhysicsCollisionValidation.hitRadius(hitRadius);
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

        public CollisionProfile(boolean auto, List<CollisionProxySpec> proxies) {
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
            boolean backstop, boolean headCollision, float hitRadiusScale,
            CollisionProfile collision, boolean enabled
    ) {
        public ConstraintProfile(
                SimulationSpace simulationSpace,
                float rotationInertiaScale,
                SwingLimits swingLimits,
                boolean backstop, boolean headCollision,
                float hitRadiusScale, boolean enabled
        ) {
            this(
                    simulationSpace, rotationInertiaScale, swingLimits,
                    backstop, headCollision, hitRadiusScale,
                    CollisionProfile.legacyAutomatic(), enabled
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
            collision = collision == null ? CollisionProfile.defaults() : collision;
        }

        public static ConstraintProfile defaults(PartType type) {
            return PhysicsProfileDefaults.constraints(type);
        }

        public static ConstraintProfile legacy() {
            return PhysicsProfileDefaults.legacyConstraints();
        }

        private static float clamp01(float value) {
            if (!Float.isFinite(value)) {
                return 1.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }

    /**
     * Multipliers over the solver's global constants. Mass is kept separate
     * from aerodynamic coupling so heavy parts can lag without weakening the
     * wind field itself.
     */
    public record SpringProfile(
            float stiffnessScale, float gravityScale, float windScale,
            float massScale, float dragScale, float inertiaScale,
            float turnScale, float angleScale, float tipDisplacementScale
    ) {
        public SpringProfile(
                float stiffnessScale, float gravityScale, float dragScale,
                float inertiaScale, float turnScale, float angleScale,
                float tipDisplacementScale
        ) {
            this(
                    stiffnessScale, gravityScale, 1.0F, 1.0F, dragScale,
                    inertiaScale, turnScale, angleScale, tipDisplacementScale
            );
        }

        public SpringProfile(
                float stiffnessScale, float gravityScale, float windScale,
                float dragScale, float inertiaScale, float turnScale,
                float angleScale, float tipDisplacementScale
        ) {
            this(
                    stiffnessScale, gravityScale, windScale, 1.0F, dragScale,
                    inertiaScale, turnScale, angleScale, tipDisplacementScale
            );
        }

        public SpringProfile {
            windScale = Float.isFinite(windScale)
                    ? Math.max(0.0F, Math.min(4.0F, windScale))
                    : 1.0F;
            massScale = Float.isFinite(massScale)
                    ? Math.max(0.25F, Math.min(4.0F, massScale))
                    : 1.0F;
        }

        /** Solver-facing name for the optional procedural pose channel. */
        public float poseDriveScale() {
            return windScale;
        }

        public static SpringProfile defaults(PartType type) {
            return PhysicsProfileDefaults.spring(type);
        }

        public SpringProfile multiply(SpringProfile override) {
            return new SpringProfile(
                    stiffnessScale * override.stiffnessScale,
                    gravityScale * override.gravityScale,
                    windScale * override.windScale,
                    massScale * override.massScale,
                    dragScale * override.dragScale,
                    inertiaScale * override.inertiaScale,
                    turnScale * override.turnScale,
                    angleScale * override.angleScale,
                    tipDisplacementScale * override.tipDisplacementScale
            );
        }

        public static SpringProfile identity() {
            return new SpringProfile(
                    1.0F, 1.0F, 1.0F, 1.0F, 1.0F,
                    1.0F, 1.0F, 1.0F, 1.0F
            );
        }
    }

    public record Decision(
            boolean driven, PartType type, Source source, String chainId,
            double confidence, SpringProfile profile,
            ConstraintProfile constraints, StructureRole structureRole,
            ChainSegment chainSegment, String reason
    ) {
        private static final Decision REJECTED =
                PhysicsDecisionFactory.rejectedDefault();

        public Decision(
                boolean driven, PartType type, Source source, String chainId,
                double confidence, SpringProfile profile,
                ConstraintProfile constraints, String reason
        ) {
            this(
                    driven, type, source, chainId, confidence, profile,
                    constraints,
                    driven
                            ? StructureRole.FLEXIBLE_CHAIN_SEGMENT
                            : StructureRole.NONE,
                    ChainSegment.none(),
                    reason
            );
        }

        public static Decision driven(
                PartType type, Source source, String chainId,
                double confidence, SpringProfile profile, String reason
        ) {
            return driven(
                    type, source, chainId, confidence, profile,
                    ConstraintProfile.defaults(type),
                    StructureRole.FLEXIBLE_CHAIN_SEGMENT,
                    reason
            );
        }

        public static Decision driven(
                PartType type, Source source, String chainId,
                double confidence, SpringProfile profile,
                ConstraintProfile constraints, String reason
        ) {
            return driven(
                    type, source, chainId, confidence, profile, constraints,
                    StructureRole.FLEXIBLE_CHAIN_SEGMENT, reason
            );
        }

        public static Decision driven(
                PartType type, Source source, String chainId,
                double confidence, SpringProfile profile,
                ConstraintProfile constraints, StructureRole structureRole,
                String reason
        ) {
            return PhysicsDecisionFactory.driven(
                    type, source, chainId, confidence, profile, constraints,
                    structureRole, reason
            );
        }

        public static Decision rejected(
                PartType type, Source source, double confidence, String reason
        ) {
            return rejected(
                    type, source, confidence, StructureRole.NONE, reason
            );
        }

        public static Decision rejected(
                PartType type, Source source, double confidence,
                StructureRole structureRole, String reason
        ) {
            return PhysicsDecisionFactory.rejected(
                    type, source, confidence, structureRole, reason
            );
        }

        public Decision withDynamics(
                SpringProfile updatedProfile,
                ConstraintProfile updatedConstraints,
                StructureRole updatedRole, ChainSegment updatedSegment
        ) {
            return PhysicsDecisionFactory.withDynamics(
                    this, updatedProfile, updatedConstraints,
                    updatedRole, updatedSegment
            );
        }
    }

    public static final class Builder {
        private final PhysicsSelectionPlanBuilderState state;

        private Builder(String modelId) {
            state = new PhysicsSelectionPlanBuilderState(modelId);
        }

        public Builder path(BoneModelSnapshot.Bone bone, String path) {
            state.path(bone, path);
            return this;
        }

        public Builder decide(BoneModelSnapshot.Bone bone, Decision decision) {
            state.decide(bone, decision);
            return this;
        }

        public Builder kinematics(
                BoneModelSnapshot.Bone bone, BoneKinematics.Metrics metrics
        ) {
            state.kinematics(bone, metrics);
            return this;
        }

        public Decision current(BoneModelSnapshot.Bone bone) {
            return state.current(bone);
        }

        public String pathOf(BoneModelSnapshot.Bone bone) {
            return state.pathOf(bone);
        }

        public PhysicsBoneSelectionPlan build() {
            return state.build();
        }
    }
}
