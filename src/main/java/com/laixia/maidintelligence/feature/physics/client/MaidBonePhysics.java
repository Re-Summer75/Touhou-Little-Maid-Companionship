package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionSignalSampler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-bone spring-bone physics for the maid's free-swinging chains (tail, hair,
 * ears), following the VRM {@code VRMC_springBone} model. Each bone stores the
 * simulated direction of its tail and integrates it with Verlet:
 *
 * <ul>
 *     <li><b>inertia</b> — carries {@code current - prev} momentum with
 *     time-corrected drag;</li>
 *     <li><b>stiffness</b> — pulls the tail back toward its <em>animation rest
 *     direction</em>, so the bone always returns to the pose the animator gave
 *     it;</li>
 *     <li><b>gravity + movement</b> — a small external force added on top.</li>
 * </ul>
 *
 * <p>Because stiffness targets the animation pose (not world-down) and gravity
 * is only a small perturbation, the resting sag is bounded by
 * {@code gravityPower / stiffness} and the rest direction follows the head:
 * looking up carries the hair up with it instead of letting gravity flip it
 * backward. Each bone measures the force in its own animated orientation, so a
 * hair sheet or tail bends and lags per bone rather than as one rigid block.
 */
@OnlyIn(Dist.CLIENT)
public final class MaidBonePhysics {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Stiffness pulls each bone back to its animation rest direction; the
    // resting gravity sag is roughly gravityPower / stiffness, so keep gravity
    // small relative to stiffness for a gentle droop that never deforms the
    // pose.
    private static final double STIFFNESS = 6.0D;
    private static final double GRAVITY_POWER = 0.9D;
    // Inertia damping (VRM dragForce, 0..1): higher settles faster.
    private static final double DRAG = 0.35D;
    // Movement-driven sway: linear acceleration and turn rate as pseudo-forces.
    private static final double INERTIA_GAIN = 7.0D;
    private static final double TURN_GAIN = 3.0D;
    // Change in delta-movement per second above which nonlinear saturation
    // prevents a teleport or lag spike from flinging the chain.
    private static final double MAX_ACCEL = 0.5D;

    // Total deflection cap from the rest pose, plus per-local-axis caps that
    // stand in for model collision: tighten the axis that drives geometry into
    // the body. Order matches the bone's local X/Y/Z.
    private static final float MAX_ANGLE = 0.8F;
    private static final float MAX_DEFLECT_X = 0.6F;
    private static final float MAX_DEFLECT_Y = 0.6F;
    private static final float MAX_DEFLECT_Z = 0.6F;

    // Lever-arm normalisation: a bone's visible swing is angle times the
    // distance from its pivot to its geometry, so a big slab like a skullcap
    // hair sweeps far for the same angle. Cap each bone's angle so its tip
    // never travels more than this many model units, derived from geometry so
    // nothing is hard-coded per bone.
    private static final float MAX_TIP_DISPLACEMENT = 3.0F;

    private static final float REFERENCE_DELTA_SECONDS = 1.0F / 60.0F;
    private static final double MAX_DELTA_SECONDS = 0.1D;
    private static final double MAX_CLOCK_GAP_SECONDS = 0.25D;
    private static final double MAX_RENDER_DISTANCE_SQR = 24.0D * 24.0D;
    private static final double EPSILON = 1.0E-5D;

    private static final int DIAGNOSTIC_FRAMES = 120;
    private static final int DIAGNOSTIC_WINDOWS = 20;

    private static final Map<LivingEntity, MaidState> STATES = new WeakHashMap<>();
    // Last model each maid rendered with, so the debug stick can dump its live
    // skeleton on demand from the client main thread.
    private static final Map<LivingEntity, AnimatedGeoModel> LAST_MODELS =
            new WeakHashMap<>();
    private static final Map<LivingEntity, PhysicsBoneSelectionPlan> LAST_PLANS =
            new WeakHashMap<>();

    private MaidBonePhysics() {
    }

    public static AnimatedGeoModel lastModel(LivingEntity maid) {
        return LAST_MODELS.get(maid);
    }

    static PhysicsBoneSelectionPlan lastPlan(LivingEntity maid) {
        return LAST_PLANS.get(maid);
    }

    public static void apply(LivingEntity maid, AnimatedGeoModel model) {
        if (maid == null || model == null || !maid.isAlive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (maid.distanceToSqr(minecraft.gameRenderer.getMainCamera().getPosition())
                > MAX_RENDER_DISTANCE_SQR) {
            STATES.remove(maid);
            return;
        }

        LAST_MODELS.put(maid, model);
        String modelId = maid instanceof EntityMaid entityMaid
                ? entityMaid.getModelId()
                : "unknown:living_entity";
        PhysicsBoneSelectionPlan plan =
                PhysicsBonePlanCache.getOrCompute(modelId, model);
        LAST_PLANS.put(maid, plan);
        MaidState state = STATES.computeIfAbsent(maid, ignored -> new MaidState());
        state.useModel(model, plan);
        boolean paused = minecraft.isPaused();
        float dt = state.advanceClock(paused);

        // External force in the model's frame. A grounded entity's vertical
        // velocity flickers between gravity and the ground clamp every tick, so
        // drop that channel while grounded to avoid a jittery acceleration.
        Vec3 rawVelocity = maid.getDeltaMovement();
        Vec3 velocity = maid.onGround()
                ? new Vec3(rawVelocity.x, 0.0D, rawVelocity.z)
                : rawVelocity;
        MotionSignals motion = state.sampleMotion(maid, velocity, dt);

        Quaternionf root = new Quaternionf();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            solve(bone, null, root, motion, dt, paused, state, plan);
        }
        state.recordDiagnostics(maid, motion.acceleration().length());
    }

    private static void solve(
            AnimatedGeoBone bone,
            AnimatedGeoBone parentBone,
            Quaternionf parentRenderedOrientation,
            MotionSignals motion,
            float dt,
            boolean paused,
            MaidState state,
            PhysicsBoneSelectionPlan plan
    ) {
        float rx = bone.getRotationX();
        float ry = bone.getRotationY();
        float rz = bone.getRotationZ();
        float px = bone.getPositionX();
        float py = bone.getPositionY();
        float pz = bone.getPositionZ();
        // The frame contains ancestor physics but not this bone's own
        // deflection. This lets a child spring follow its moving parent without
        // applying the ancestor angle a second time.
        Quaternionf boneBaseOrientation =
                composeOrientation(parentRenderedOrientation, rx, ry, rz);

        BoneData data = state.dataFor(
                bone,
                parentBone,
                plan.decision(bone)
        );
        if (data != null) {
            // Rest direction of the bone's tail in model space, following the
            // animation pose. This is the target stiffness pulls back toward.
            Vector3f restDir = boneBaseOrientation.transform(
                    new Vector3f(data.boneAxis)
            ).normalize();
            if (!data.initialized) {
                data.currentDir.set(restDir);
                data.prevDir.set(restDir);
                data.initialized = true;
            }

            if (!paused && dt > EPSILON) {
                Vector3f next = new Vector3f(data.currentDir);
                PhysicsBoneSelectionPlan.SpringProfile profile = data.profile;
                float drag = Mth.clamp(
                        (float) DRAG * profile.dragScale(),
                        0.0F,
                        0.95F
                );
                float retention = dragRetention(drag, dt);
                float stepRatio = data.previousDt > EPSILON
                        ? Mth.clamp(dt / data.previousDt, 0.25F, 4.0F)
                        : 1.0F;
                // inertia: carry last frame's motion, bled off by drag.
                next.add(
                        (data.currentDir.x() - data.prevDir.x()) * retention * stepRatio,
                        (data.currentDir.y() - data.prevDir.y()) * retention * stepRatio,
                        (data.currentDir.z() - data.prevDir.z()) * retention * stepRatio
                );
                // stiffness: pull toward the animation rest direction.
                float stiffness = (float) (STIFFNESS * profile.stiffnessScale() * dt);
                next.add(restDir.x() * stiffness, restDir.y() * stiffness, restDir.z() * stiffness);
                // external gravity + movement, scaled by time.
                Vector3f externalForce = motion.force(profile);
                next.add(
                        externalForce.x() * dt,
                        externalForce.y() * dt,
                        externalForce.z() * dt
                );
                if (next.lengthSquared() > EPSILON) {
                    next.normalize();
                    data.prevDir.set(data.currentDir);
                    data.currentDir.set(next);
                }
                data.previousDt = dt;
            }

            applyDeflection(
                    bone,
                    boneBaseOrientation,
                    data,
                    rx,
                    ry,
                    rz,
                    px,
                    py,
                    pz,
                    state
            );
        }

        Quaternionf boneRenderedOrientation = composeOrientation(
                parentRenderedOrientation,
                bone.getRotationX(),
                bone.getRotationY(),
                bone.getRotationZ()
        );
        for (AnimatedGeoBone child : bone.children()) {
            solve(
                    child,
                    bone,
                    boneRenderedOrientation,
                    motion,
                    dt,
                    paused,
                    state,
                    plan
            );
        }
    }

    /**
     * Turns the simulated tail direction into a local rotation offset: the
     * rotation that carries the rest bone axis to the simulated direction, in
     * the bone's local frame, added to the animation Euler angles.
     */
    private static void applyDeflection(
            AnimatedGeoBone bone,
            Quaternionf boneAnimOrientation,
            BoneData data,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz,
            MaidState state
    ) {
        Vector3f localDir = boneAnimOrientation
                .transformInverse(new Vector3f(data.currentDir));
        if (localDir.lengthSquared() < EPSILON) {
            return;
        }
        localDir.normalize();
        Vector3f axis = data.boneAxis.cross(localDir, new Vector3f());
        float sin = axis.length();
        if (sin < EPSILON) {
            return;
        }
        axis.div(sin);
        float dot = Math.max(-1.0F, Math.min(1.0F, data.boneAxis.dot(localDir)));
        // Cap the angle so the geometry tip travels a bounded distance: a large
        // bone (long lever) is limited to a smaller angle than a short strand,
        // evening out the visible swing without any per-bone constant.
        float cap = Math.min(
                Math.min(MAX_ANGLE, data.safeAngle) * data.profile.angleScale(),
                MAX_TIP_DISPLACEMENT * data.profile.tipDisplacementScale()
                        / data.leverArm
        );
        float angle = Math.min((float) Math.acos(dot), cap);
        // Small/moderate angle: the rotation vector axis*angle equals the ZYX
        // Euler offsets to first order. Per-axis caps stand in for collision.
        float dRx = clampAbs(
                axis.x() * angle,
                MAX_DEFLECT_X * data.profile.angleScale()
        );
        float dRy = clampAbs(
                axis.y() * angle,
                MAX_DEFLECT_Y * data.profile.angleScale()
        );
        float dRz = clampAbs(
                axis.z() * angle,
                MAX_DEFLECT_Z * data.profile.angleScale()
        );
        bone.setRotationX(rx + dRx);
        bone.setRotationY(ry + dRy);
        bone.setRotationZ(rz + dRz);
        compensatePivot(bone, data, rx, ry, rz, px, py, pz);
        state.reportDeflection(angle);
    }

    private static void compensatePivot(
            AnimatedGeoBone bone,
            BoneData data,
            float rx,
            float ry,
            float rz,
            float px,
            float py,
            float pz
    ) {
        if (!data.kinematics.compensatesPivot()) {
            return;
        }
        Quaternionf animationRotation =
                new Quaternionf().rotateZYX(rz, ry, rx);
        Quaternionf physicalDelta = new Quaternionf().rotateZYX(
                bone.getRotationZ(),
                bone.getRotationY(),
                bone.getRotationX()
        ).mul(animationRotation.invert());
        Vector3f offset =
                data.kinematics.compensationOffset(physicalDelta);
        // RenderUtils negates only the animated X position when translating.
        bone.setPositionX(px - offset.x * 16.0F);
        bone.setPositionY(py + offset.y * 16.0F);
        bone.setPositionZ(pz + offset.z * 16.0F);
    }

    public static void forget(LivingEntity maid) {
        STATES.remove(maid);
        LAST_MODELS.remove(maid);
        LAST_PLANS.remove(maid);
    }

    public static void clear() {
        STATES.clear();
        LAST_MODELS.clear();
        LAST_PLANS.clear();
    }

    /**
     * The debug layer and dump consume the exact same immutable decision as the
     * solver, avoiding a second classifier that can disagree with live motion.
     */
    static boolean isDriven(
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan plan
    ) {
        return plan != null && plan.isDriven(bone);
    }

    static Vector3f worldAccelerationToModel(
            Vec3 acceleration,
            float bodyYawDegrees
    ) {
        // LivingEntityRenderer rotates model space by 180° - bodyYaw, so its
        // inverse maps a world vector by bodyYaw - 180°.
        float worldToModelYaw = (float) Math.toRadians(
                Mth.wrapDegrees(bodyYawDegrees - 180.0F)
        );
        return new Vector3f(
                (float) acceleration.x,
                (float) acceleration.y,
                (float) acceleration.z
        ).rotateY(worldToModelYaw);
    }

    static float dragRetention(float drag, float dt) {
        float clampedDrag = Mth.clamp(drag, 0.0F, 0.95F);
        if (dt <= 0.0F) {
            return 1.0F;
        }
        return (float) Math.pow(
                1.0F - clampedDrag,
                dt / REFERENCE_DELTA_SECONDS
        );
    }

    static Quaternionf composeOrientation(
            Quaternionf parent,
            float rotationX,
            float rotationY,
            float rotationZ
    ) {
        return new Quaternionf(parent).mul(
                new Quaternionf().rotateZYX(
                        rotationZ,
                        rotationY,
                        rotationX
                )
        );
    }

    private static float clampAbs(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private record MotionSignals(Vector3f acceleration, float yawRate) {
        private Vector3f force(PhysicsBoneSelectionPlan.SpringProfile profile) {
            float inertia = (float) INERTIA_GAIN * profile.inertiaScale();
            float turn = (float) TURN_GAIN * profile.turnScale();
            return new Vector3f(
                    acceleration.x() * -inertia + yawRate * turn,
                    acceleration.y() * -inertia
                            - (float) GRAVITY_POWER * profile.gravityScale(),
                    acceleration.z() * -inertia
            );
        }
    }

    private static final class MaidState {
        private final Map<AnimatedGeoBone, BoneData> bones = new IdentityHashMap<>();
        private final MotionSignalSampler motionSampler =
                new MotionSignalSampler();
        private AnimatedGeoModel model;
        private PhysicsBoneSelectionPlan plan;
        private long lastNanos;

        private int drivenBoneCount;
        private int diagnosticFrames;
        private int diagnosticWindows;
        private double peakDeflection;
        private double peakAcceleration;

        private void useModel(
                AnimatedGeoModel currentModel,
                PhysicsBoneSelectionPlan currentPlan
        ) {
            if (model == currentModel && plan == currentPlan) {
                return;
            }
            model = currentModel;
            plan = currentPlan;
            bones.clear();
            motionSampler.reset();
            lastNanos = 0L;
            drivenBoneCount = 0;
            diagnosticFrames = 0;
            diagnosticWindows = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
        }

        private MotionSignals sampleMotion(
                LivingEntity maid,
                Vec3 velocity,
                float dt
        ) {
            MotionSignalSampler.Sample filtered = motionSampler.update(
                    velocity,
                    maid.tickCount,
                    maid.yBodyRot,
                    maid.yBodyRotO,
                    (float) MAX_ACCEL,
                    dt
            );
            Vector3f worldAcceleration = filtered.worldAcceleration();
            return new MotionSignals(
                    worldAccelerationToModel(
                            new Vec3(
                                    worldAcceleration.x,
                                    worldAcceleration.y,
                                    worldAcceleration.z
                            ),
                            maid.yBodyRot
                    ),
                    filtered.yawRate()
            );
        }

        private float advanceClock(boolean paused) {
            long now = System.nanoTime();
            if (lastNanos == 0L || paused) {
                lastNanos = now;
                return 0.0F;
            }
            double seconds = (now - lastNanos) / 1.0E9D;
            lastNanos = now;
            if (seconds > MAX_CLOCK_GAP_SECONDS) {
                return 0.0F;
            }
            return (float) Mth.clamp(seconds, 0.0D, MAX_DELTA_SECONDS);
        }

        private BoneData dataFor(
                AnimatedGeoBone bone,
                AnimatedGeoBone parent,
                PhysicsBoneSelectionPlan.Decision decision
        ) {
            if (!decision.driven()) {
                return null;
            }
            BoneData cached = bones.get(bone);
            if (cached != null) {
                return cached;
            }
            BoneKinematics.Metrics kinematics = BoneKinematics.measure(
                    bone,
                    parent,
                    decision.type()
            );
            BoneData created = new BoneData(
                    kinematics,
                    decision.profile()
            );
            bones.put(bone, created);
            drivenBoneCount++;
            return created;
        }

        private void reportDeflection(double angle) {
            peakDeflection = Math.max(peakDeflection, angle);
        }

        private void recordDiagnostics(LivingEntity maid, double acceleration) {
            if (diagnosticWindows >= DIAGNOSTIC_WINDOWS) {
                return;
            }
            peakAcceleration = Math.max(peakAcceleration, acceleration);
            if (++diagnosticFrames < DIAGNOSTIC_FRAMES) {
                return;
            }
            diagnosticWindows++;
            LOGGER.debug(
                    "Maid bone physics [{}]: drivenBones={} peakDeflectionDeg={} peakAccel={}",
                    maid.getName().getString(),
                    drivenBoneCount,
                    String.format(java.util.Locale.ROOT, "%.2f", Math.toDegrees(peakDeflection)),
                    String.format(java.util.Locale.ROOT, "%.4f", peakAcceleration)
            );
            diagnosticFrames = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
        }
    }

    private static final class BoneData {
        private final BoneKinematics.Metrics kinematics;
        private final Vector3f boneAxis;
        private final float leverArm;
        private final float safeAngle;
        private final PhysicsBoneSelectionPlan.SpringProfile profile;
        private final Vector3f currentDir = new Vector3f();
        private final Vector3f prevDir = new Vector3f();
        private float previousDt;
        private boolean initialized;

        private BoneData(
                BoneKinematics.Metrics kinematics,
                PhysicsBoneSelectionPlan.SpringProfile profile
        ) {
            this.kinematics = kinematics;
            this.boneAxis = new Vector3f(kinematics.axis());
            this.leverArm = kinematics.leverArm();
            this.safeAngle = kinematics.safeAngle();
            this.profile = profile;
        }
    }
}
