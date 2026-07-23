package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
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

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-bone spring-bone physics for the maid's free-swinging chains (tail, hair,
 * ears), following the VRM {@code VRMC_springBone} model. Each bone stores the
 * simulated direction of its tail and integrates it with Verlet:
 *
 * <ul>
 *     <li><b>inertia</b> — {@code (current - prev) * (1 - drag)} carries swing
 *     momentum;</li>
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
    // Blocks/tick of acceleration above which inertia saturates, so a teleport
    // or lag spike cannot fling the chain.
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
    private static final float MIN_LEVER_ARM = 1.0F;

    private static final double MAX_DELTA_SECONDS = 0.1D;
    private static final double MAX_RENDER_DISTANCE_SQR = 24.0D * 24.0D;
    private static final double EPSILON = 1.0E-5D;

    private static final int DIAGNOSTIC_FRAMES = 120;
    private static final int DIAGNOSTIC_WINDOWS = 20;

    private static final Map<LivingEntity, MaidState> STATES = new WeakHashMap<>();
    // Last model each maid rendered with, so the debug stick can dump its live
    // skeleton on demand from the client main thread.
    private static final Map<LivingEntity, AnimatedGeoModel> LAST_MODELS =
            new WeakHashMap<>();

    private MaidBonePhysics() {
    }

    public static AnimatedGeoModel lastModel(LivingEntity maid) {
        return LAST_MODELS.get(maid);
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
        MaidState state = STATES.computeIfAbsent(maid, ignored -> new MaidState());
        boolean paused = minecraft.isPaused();
        float dt = paused ? 0.0F : state.advanceClock();

        // External force in the model's frame. A grounded entity's vertical
        // velocity flickers between gravity and the ground clamp every tick, so
        // drop that channel while grounded to avoid a jittery acceleration.
        Vec3 rawVelocity = maid.getDeltaMovement();
        Vec3 velocity = maid.onGround()
                ? new Vec3(rawVelocity.x, 0.0D, rawVelocity.z)
                : rawVelocity;
        Vec3 accelWorld = clampLength(
                velocity.subtract(state.previousVelocity)
                        .scale(dt > EPSILON ? 1.0D / dt : 0.0D),
                MAX_ACCEL
        );
        float yaw = (float) Math.toRadians(Mth.wrapDegrees(maid.yBodyRot));
        Vector3f accelModel = new Vector3f(
                (float) accelWorld.x,
                (float) accelWorld.y,
                (float) accelWorld.z
        ).rotateY(yaw);
        double yawRate = Math.toRadians(Mth.wrapDegrees(maid.yBodyRot - maid.yBodyRotO));

        // gravity (small, downward) + linear inertia + turning pseudo-force.
        Vector3f externalForce = new Vector3f(
                accelModel.x() * (float) -INERTIA_GAIN + (float) (yawRate * TURN_GAIN),
                accelModel.y() * (float) -INERTIA_GAIN - (float) GRAVITY_POWER,
                accelModel.z() * (float) -INERTIA_GAIN
        );

        Quaternionf root = new Quaternionf();
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            solve(bone, root, externalForce, dt, paused, state);
        }
        state.previousVelocity = velocity;
        state.recordDiagnostics(maid, accelWorld.length());
    }

    private static void solve(
            AnimatedGeoBone bone,
            Quaternionf parentAnimOrientation,
            Vector3f externalForce,
            float dt,
            boolean paused,
            MaidState state
    ) {
        float rx = bone.getRotationX();
        float ry = bone.getRotationY();
        float rz = bone.getRotationZ();
        // Accumulate the ANIMATION-pose orientation only. Feeding the applied
        // deflection back into the frame the force is measured in creates a
        // chase-your-own-tail loop that jitters the chain.
        Quaternionf boneAnimOrientation = new Quaternionf(parentAnimOrientation)
                .mul(new Quaternionf().rotateZYX(rz, ry, rx));

        BoneData data = state.dataFor(bone);
        if (data != null) {
            // Rest direction of the bone's tail in model space, following the
            // animation pose. This is the target stiffness pulls back toward.
            Vector3f restDir = boneAnimOrientation.transform(
                    new Vector3f(data.boneAxis)
            ).normalize();
            if (!data.initialized) {
                data.currentDir.set(restDir);
                data.prevDir.set(restDir);
                data.initialized = true;
            }

            if (!paused && dt > EPSILON) {
                Vector3f next = new Vector3f(data.currentDir);
                // inertia: carry last frame's motion, bled off by drag.
                next.add(
                        (data.currentDir.x() - data.prevDir.x()) * (float) (1.0D - DRAG),
                        (data.currentDir.y() - data.prevDir.y()) * (float) (1.0D - DRAG),
                        (data.currentDir.z() - data.prevDir.z()) * (float) (1.0D - DRAG)
                );
                // stiffness: pull toward the animation rest direction.
                float stiffness = (float) (STIFFNESS * dt);
                next.add(restDir.x() * stiffness, restDir.y() * stiffness, restDir.z() * stiffness);
                // external gravity + movement, scaled by time.
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
            }

            applyDeflection(bone, boneAnimOrientation, data, rx, ry, rz, state);
        }

        for (AnimatedGeoBone child : bone.children()) {
            solve(child, boneAnimOrientation, externalForce, dt, paused, state);
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
        float cap = Math.min(MAX_ANGLE, MAX_TIP_DISPLACEMENT / data.leverArm);
        float angle = Math.min((float) Math.acos(dot), cap);
        // Small/moderate angle: the rotation vector axis*angle equals the ZYX
        // Euler offsets to first order. Per-axis caps stand in for collision.
        float dRx = clampAbs(axis.x() * angle, MAX_DEFLECT_X);
        float dRy = clampAbs(axis.y() * angle, MAX_DEFLECT_Y);
        float dRz = clampAbs(axis.z() * angle, MAX_DEFLECT_Z);
        bone.setRotationX(rx + dRx);
        bone.setRotationY(ry + dRy);
        bone.setRotationZ(rz + dRz);
        state.reportDeflection(angle);
    }

    public static void forget(LivingEntity maid) {
        STATES.remove(maid);
    }

    public static void clear() {
        STATES.clear();
        LAST_MODELS.clear();
    }

    /**
     * Whether this bone is one the layer deflects: a classified chain bone that
     * is not a branching container and carries geometry. Exposed for the
     * skeleton debug overlay.
     */
    static boolean isDrivenBone(AnimatedGeoBone bone) {
        return PhysicsBoneClassifier.classify(bone.getName()).isPhysical()
                && bone.children().size() < 2
                && bone.geoBone().cubes().getCubeCount() > 0;
    }

    private static float clampAbs(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static Vec3 clampLength(Vec3 vector, double maxLength) {
        double length = vector.length();
        if (length <= maxLength || length < EPSILON) {
            return vector;
        }
        return vector.scale(maxLength / length);
    }

    /**
     * Local-space unit direction from a bone's pivot to its geometry: the axis
     * the bone visibly extends along, used as the rest tail direction. Falls
     * back to local down for bones without cubes.
     */
    private static Vector3f boneAxisOf(AnimatedGeoBone bone) {
        GeoMesh mesh = bone.geoBone().cubes();
        if (mesh.getCubeCount() == 0) {
            return new Vector3f(0.0F, -1.0F, 0.0F);
        }
        Vector3f position = mesh.position(0);
        Vector3f dx = mesh.dx(0);
        Vector3f dy = mesh.dy(0);
        Vector3f dz = mesh.dz(0);
        Vector3f center = new Vector3f(
                position.x() + 0.5F * (dx.x() + dy.x() + dz.x()),
                position.y() + 0.5F * (dx.y() + dy.y() + dz.y()),
                position.z() + 0.5F * (dx.z() + dy.z() + dz.z())
        );
        if (center.length() < EPSILON) {
            return new Vector3f(0.0F, -1.0F, 0.0F);
        }
        return center.normalize();
    }

    /**
     * Longest distance from a bone's pivot to any corner of its geometry: the
     * lever arm used to normalise how far a deflection sweeps the geometry.
     */
    private static float leverArmOf(AnimatedGeoBone bone) {
        GeoMesh mesh = bone.geoBone().cubes();
        float maxLengthSquared = 0.0F;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f position = mesh.position(cube);
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            for (int corner = 0; corner < 8; corner++) {
                float x = position.x()
                        + ((corner & 1) != 0 ? dx.x() : 0.0F)
                        + ((corner & 2) != 0 ? dy.x() : 0.0F)
                        + ((corner & 4) != 0 ? dz.x() : 0.0F);
                float y = position.y()
                        + ((corner & 1) != 0 ? dx.y() : 0.0F)
                        + ((corner & 2) != 0 ? dy.y() : 0.0F)
                        + ((corner & 4) != 0 ? dz.y() : 0.0F);
                float z = position.z()
                        + ((corner & 1) != 0 ? dx.z() : 0.0F)
                        + ((corner & 2) != 0 ? dy.z() : 0.0F)
                        + ((corner & 4) != 0 ? dz.z() : 0.0F);
                maxLengthSquared = Math.max(maxLengthSquared, x * x + y * y + z * z);
            }
        }
        return Math.max((float) Math.sqrt(maxLengthSquared), MIN_LEVER_ARM);
    }

    private static final class MaidState {
        private final Map<String, BoneData> bones = new HashMap<>();
        private Vec3 previousVelocity = Vec3.ZERO;
        private long lastNanos;

        private int drivenBoneCount;
        private int diagnosticFrames;
        private int diagnosticWindows;
        private double peakDeflection;
        private double peakAcceleration;

        private float advanceClock() {
            long now = System.nanoTime();
            if (lastNanos == 0L) {
                lastNanos = now;
                return 0.0F;
            }
            double seconds = (now - lastNanos) / 1.0E9D;
            lastNanos = now;
            return (float) Mth.clamp(seconds, 0.0D, MAX_DELTA_SECONDS);
        }

        private BoneData dataFor(AnimatedGeoBone bone) {
            String key = bone.getName();
            if (bones.containsKey(key)) {
                return bones.get(key);
            }
            BoneData created = isDrivenBone(bone)
                    ? new BoneData(boneAxisOf(bone), leverArmOf(bone))
                    : null;
            if (created != null) {
                drivenBoneCount++;
            }
            bones.put(key, created);
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
        private final Vector3f boneAxis;
        private final float leverArm;
        private final Vector3f currentDir = new Vector3f();
        private final Vector3f prevDir = new Vector3f();
        private boolean initialized;

        private BoneData(Vector3f boneAxis, float leverArm) {
            this.boneAxis = boneAxis;
            this.leverArm = leverArm;
        }
    }
}
