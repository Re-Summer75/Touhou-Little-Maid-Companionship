package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionSignalSampler;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-entity owner for Gecko spring-bone state. Discovery and static
 * kinematics are prepared outside the frame loop; each rendered frame runs the
 * iterative solver over only driven bones and their ancestors.
 */
@OnlyIn(Dist.CLIENT)
public final class MaidBonePhysics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final float MAX_ACCEL = 0.5F;
    private static final double MAX_DELTA_SECONDS = 0.1D;
    private static final double MAX_CLOCK_GAP_SECONDS = 0.25D;
    private static final double MAX_RENDER_DISTANCE_SQR = 24.0D * 24.0D;
    private static final double TELEPORT_DISTANCE_SQR = 4.0D * 4.0D;

    private static final int DIAGNOSTIC_FRAMES = 120;
    private static final int DIAGNOSTIC_WINDOWS = 20;

    private static final Map<LivingEntity, MaidState> STATES =
            new WeakHashMap<>();
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

    static SpringBoneSolver lastSolver(Entity maid) {
        MaidState state = STATES.get(maid);
        return state == null ? null : state.solver;
    }

    public static void apply(LivingEntity maid, AnimatedGeoModel model) {
        if (maid == null || model == null || !maid.isAlive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (maid.distanceToSqr(
                minecraft.gameRenderer.getMainCamera().getPosition()
        ) > MAX_RENDER_DISTANCE_SQR) {
            STATES.remove(maid);
            return;
        }

        LAST_MODELS.put(maid, model);
        String modelId = maid instanceof EntityMaid entityMaid
                ? entityMaid.getModelId()
                : "unknown:living_entity";
        MaidState state = STATES.get(maid);
        if (state == null) {
            state = new MaidState();
            STATES.put(maid, state);
        }
        PhysicsBoneSelectionPlan plan = state.matches(model, modelId)
                ? state.plan
                : PhysicsBonePlanCache.getOrCompute(modelId, model);
        LAST_PLANS.put(maid, plan);
        state.useModel(modelId, model, plan);
        boolean paused = minecraft.isPaused();
        float dt = state.advanceClock(paused);
        state.sampleMotion(maid, maid.getDeltaMovement(), dt);
        state.solver.solve(
                state.modelAcceleration,
                state.yawRate,
                dt,
                paused
        );
        state.recordDiagnostics(
                maid,
                state.modelAcceleration.length(),
                state.solver.lastPeakDeflection()
        );
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
        return worldAccelerationToModelInto(
                new Vector3f(
                        (float) acceleration.x,
                        (float) acceleration.y,
                        (float) acceleration.z
                ),
                bodyYawDegrees,
                new Vector3f()
        );
    }

    static Vector3f worldAccelerationToModelInto(
            Vector3f acceleration,
            float bodyYawDegrees,
            Vector3f output
    ) {
        float worldToModelYaw = (float) Math.toRadians(
                Mth.wrapDegrees(bodyYawDegrees - 180.0F)
        );
        return output.set(acceleration).rotateY(worldToModelYaw);
    }

    static float dragRetention(float drag, float dt) {
        return SpringBoneSolver.dragRetention(drag, dt);
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

    private static final class MaidState {
        private final MotionSignalSampler motionSampler =
                new MotionSignalSampler();
        private final Vector3f worldAcceleration = new Vector3f();
        private final Vector3f modelAcceleration = new Vector3f();

        private AnimatedGeoModel model;
        private String modelId;
        private PhysicsBoneSelectionPlan plan;
        private PhysicsSolverLayout layout;
        private SpringBoneSolver solver;
        private float yawRate;
        private long lastNanos;
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean positionInitialized;

        private int diagnosticFrames;
        private int diagnosticWindows;
        private double peakDeflection;
        private double peakAcceleration;

        private boolean matches(
                AnimatedGeoModel currentModel,
                String currentModelId
        ) {
            return model == currentModel
                    && java.util.Objects.equals(modelId, currentModelId);
        }

        private void useModel(
                String currentModelId,
                AnimatedGeoModel currentModel,
                PhysicsBoneSelectionPlan currentPlan
        ) {
            if (matches(currentModel, currentModelId)
                    && plan == currentPlan) {
                return;
            }
            modelId = currentModelId;
            model = currentModel;
            plan = currentPlan;
            layout = PhysicsSolverLayout.build(currentModel, currentPlan);
            solver = new SpringBoneSolver(layout);
            motionSampler.reset();
            worldAcceleration.zero();
            modelAcceleration.zero();
            yawRate = 0.0F;
            lastNanos = 0L;
            positionInitialized = false;
            diagnosticFrames = 0;
            diagnosticWindows = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
        }

        private void sampleMotion(
                LivingEntity maid,
                Vec3 velocity,
                float dt
        ) {
            double x = maid.getX();
            double y = maid.getY();
            double z = maid.getZ();
            if (positionInitialized) {
                double dx = x - lastX;
                double dy = y - lastY;
                double dz = z - lastZ;
                if (dx * dx + dy * dy + dz * dz
                        > TELEPORT_DISTANCE_SQR) {
                    resetTransientMotion();
                }
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            positionInitialized = true;
            yawRate = motionSampler.updateInto(
                    velocity.x,
                    maid.onGround() ? 0.0D : velocity.y,
                    velocity.z,
                    maid.tickCount,
                    maid.yBodyRot,
                    maid.yBodyRotO,
                    MAX_ACCEL,
                    dt,
                    worldAcceleration
            );
            worldAccelerationToModelInto(
                    worldAcceleration,
                    maid.yBodyRot,
                    modelAcceleration
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
                resetTransientMotion();
                return 0.0F;
            }
            return (float) Mth.clamp(
                    seconds,
                    0.0D,
                    MAX_DELTA_SECONDS
            );
        }

        private void resetTransientMotion() {
            solver.reset();
            motionSampler.reset();
            worldAcceleration.zero();
            modelAcceleration.zero();
            yawRate = 0.0F;
        }

        private void recordDiagnostics(
                LivingEntity maid,
                double acceleration,
                double deflection
        ) {
            if (!LOGGER.isDebugEnabled()
                    || diagnosticWindows >= DIAGNOSTIC_WINDOWS) {
                return;
            }
            peakAcceleration = Math.max(peakAcceleration, acceleration);
            peakDeflection = Math.max(peakDeflection, deflection);
            if (++diagnosticFrames < DIAGNOSTIC_FRAMES) {
                return;
            }
            diagnosticWindows++;
            LOGGER.debug(
                    "Maid bone physics [{}]: activeNodes={}/{} drivenBones={} "
                            + "peakDeflectionDeg={} peakAccel={}",
                    maid.getName().getString(),
                    layout.activeNodeCount(),
                    layout.fullBoneCount(),
                    layout.drivenBoneCount(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            Math.toDegrees(peakDeflection)
                    ),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.4f",
                            peakAcceleration
                    )
            );
            diagnosticFrames = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
        }
    }
}
