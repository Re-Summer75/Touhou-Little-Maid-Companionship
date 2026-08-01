package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.physics.client.model.GeckoBoneModelPort;
import com.laixia.maidintelligence.feature.physics.client.pose.MaidPoseDriverPort;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.session.PhysicsSession;
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
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Per-entity owner for Gecko spring-bone state. Discovery and static
 * kinematics are prepared outside the frame loop; each rendered frame runs the
 * iterative solver over only driven bones and their ancestors.
 */
@OnlyIn(Dist.CLIENT)
public final class MaidBonePhysics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final float MAX_ACCEL = 0.5F;
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
    private static BooleanSupplier enabledSupplier = () -> true;

    private MaidBonePhysics() {
    }

    static void configure(BooleanSupplier source) {
        enabledSupplier = Objects.requireNonNull(source, "source");
    }

    static boolean enabled() {
        return enabledSupplier.getAsBoolean();
    }

    public static AnimatedGeoModel lastModel(LivingEntity maid) {
        return LAST_MODELS.get(maid);
    }

    static PhysicsBoneSelectionPlan lastPlan(LivingEntity maid) {
        return LAST_PLANS.get(maid);
    }

    static SpringBoneSolver lastSolver(Entity maid) {
        MaidState state = STATES.get(maid);
        return state == null || state.session == null
                ? null
                : state.session.solver();
    }

    public static void apply(
            LivingEntity maid,
            AnimatedGeoModel model,
            double animationTick
    ) {
        if (maid == null || model == null || !maid.isAlive()) {
            return;
        }
        if (!enabled()) {
            forget(maid);
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
        state.useModel(maid, modelId, model, plan);
        state.observePosition(maid);
        boolean paused = minecraft.isPaused();
        Vec3 velocity = maid.getDeltaMovement();
        float dt = state.session.advance(
                animationTick,
                velocity.x,
                velocity.y,
                velocity.z,
                maid.onGround(),
                maid.tickCount,
                maid.yBodyRot,
                maid.yBodyRotO,
                paused
        );
        state.session.copyModelAcceleration(state.modelAcceleration);
        state.session.copyModelPoseDrive(state.modelPoseDrive);
        state.recordDiagnostics(
                maid,
                state.modelAcceleration,
                state.modelPoseDrive,
                state.session.solver().lastPeakDeflection()
        );
        /*
         * Sampled here rather than on a tick, because jitter lives between ticks.
         * A client tick is three frames and the shortest oscillation the solver
         * can hold is two, so tick-rate sampling aliases the fastest shake away
         * entirely — it was reporting hair as the worst offender while the log
         * showed the actual offenders perfectly smooth.
         */
        PhysicsDisplacementLog.sample(maid, state.session.solver(), dt);
    }

    /**
     * Restores the animation-only local pose saved before the previous solve.
     */
    public static void restoreAnimationPose(
            LivingEntity maid,
            AnimatedGeoModel model
    ) {
        MaidState state = STATES.get(maid);
        if (state != null && state.model == model && state.session != null) {
            state.session.restoreAnimationPose();
        }
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
        private final Vector3f modelAcceleration = new Vector3f();
        private final Vector3f modelPoseDrive = new Vector3f();

        private AnimatedGeoModel model;
        private String modelId;
        private PhysicsBoneSelectionPlan plan;
        private GeckoBoneModelPort modelPort;
        private PhysicsSession session;
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean positionInitialized;

        private int diagnosticFrames;
        private int diagnosticWindows;
        private double peakDeflection;
        private double peakAcceleration;
        private double peakPoseDrive;

        private boolean matches(
                AnimatedGeoModel currentModel,
                String currentModelId
        ) {
            return model == currentModel
                    && java.util.Objects.equals(modelId, currentModelId);
        }

        private void useModel(
                LivingEntity maid,
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
            modelPort = GeckoBoneModelPort.of(currentModel);
            session = new PhysicsSession(
                    modelPort,
                    modelPort,
                    new MaidPoseDriverPort(maid),
                    currentPlan
            );
            modelAcceleration.zero();
            modelPoseDrive.zero();
            positionInitialized = false;
            diagnosticFrames = 0;
            diagnosticWindows = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
            peakPoseDrive = 0.0D;
        }

        private void observePosition(LivingEntity maid) {
            double x = maid.getX();
            double y = maid.getY();
            double z = maid.getZ();
            if (positionInitialized) {
                double dx = x - lastX;
                double dy = y - lastY;
                double dz = z - lastZ;
                if (dx * dx + dy * dy + dz * dz
                        > TELEPORT_DISTANCE_SQR) {
                    session.resetTransientState();
                }
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            positionInitialized = true;
        }

        private void recordDiagnostics(
                LivingEntity maid,
                Vector3f acceleration,
                Vector3f poseDrive,
                double deflection
        ) {
            if (!LOGGER.isDebugEnabled()
                    || diagnosticWindows >= DIAGNOSTIC_WINDOWS) {
                return;
            }
            peakAcceleration = Math.max(
                    peakAcceleration,
                    acceleration.length()
            );
            peakPoseDrive = Math.max(
                    peakPoseDrive,
                    poseDrive.length()
            );
            peakDeflection = Math.max(peakDeflection, deflection);
            if (++diagnosticFrames < DIAGNOSTIC_FRAMES) {
                return;
            }
            diagnosticWindows++;
            LOGGER.debug(
                    "Maid bone physics [{}]: activeNodes={}/{} drivenBones={} "
                            + "peakDeflectionDeg={} peakAccel={} "
                            + "peakPoseDrive={}",
                    maid.getName().getString(),
                    session.layout().activeNodeCount(),
                    session.layout().fullBoneCount(),
                    session.layout().drivenBoneCount(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.2f",
                            Math.toDegrees(peakDeflection)
                    ),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.4f",
                            peakAcceleration
                    ),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.4f",
                            peakPoseDrive
                    )
            );
            diagnosticFrames = 0;
            peakDeflection = 0.0D;
            peakAcceleration = 0.0D;
            peakPoseDrive = 0.0D;
        }
    }
}
