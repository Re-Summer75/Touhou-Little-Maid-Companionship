package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers cloth reacting to a collider that sweeps through it, which is what
 * legs do to a skirt on every stride.
 *
 * <p>Three separate mechanisms each used to swallow this motion whole, and each
 * looked locally reasonable. Only the segment's tip was ever tested for
 * contact, so a leg entering the middle of a panel met nothing. The allowance
 * that lets authored poses overlap was seeded once from the rest pose and kept
 * forever, and a leg rest-poses inside the very skirt it will later kick, so
 * that depth was permanently excused. The damper that settles a segment caught
 * between two colliders triggered on a single reversal, and a limb swinging
 * back and forth reverses once per stride.
 *
 * <p>What they share is that the skirt kept its authored pose while a leg
 * passed through it, and the measure here is deliberately the same: how far the
 * cloth is driven off the pose it settled into. Penetration depth cannot carry
 * this test on its own, because every one of those mechanisms drives it towards
 * zero — by excusing the overlap rather than resolving it.
 *
 * <p>The same measure now also covers the gaps a segment caches to skip
 * collision geometry it has room to spare against. Those gaps are only sound
 * while everything that can close them is charged against them, and a leg
 * closing on a skirt is exactly that; under-charging it drops the response
 * here to near zero.
 */
final class SweptContactVerification {
    private static final float DT = 1.0F / 60.0F;
    /** Long enough for the rest allowance to seed and the pose to settle. */
    private static final int SETTLE = 180;
    /**
     * Stride periods to try, spanning a walk and a run.
     *
     * <p>More than one is needed because a single rate can land on a gait the
     * skirt happens to travel with: at 0.5s this model's panels and legs move
     * nearly together and never overlap at all, so nothing is asked of the
     * collision and a zero response there means only that the leg missed. The
     * claim being tested is that some ordinary gait drives the cloth, not that
     * every one does.
     */
    private static final float[] STRIDES = {0.8F, 0.4F};
    private static final float STRIDE_RADIANS = 0.9F;
    /**
     * Deflection a swept leg has to produce, in radians.
     *
     * <p>The panels reach 0.35 here and 0.094 with body sampling removed, so
     * this sits between the two with room on both sides. Both ends move with
     * gravity: pulling world-down lets the panels sag into the swing of a leg,
     * so their tips meet one and the length of the segment is no longer the
     * only thing in contact — resolving gravity against the authored pose
     * instead held them clear and left 0.17 against 0.010. Being far below the
     * swing ceiling either way, the claim stays "the cloth was driven, not
     * merely nudged" rather than pinning an exact response.
     */
    private static final float RESPONSE = 0.20F;
    /** Cloth may not be thrown further than a limb could plausibly push it. */
    private static final float RUNAWAY = 0.95F;
    private static final String MODEL = "winefox.json";

    private SweptContactVerification() {
    }

    static void run() throws Exception {
        verifiesSweptLegDrivesTheSkirt();
    }

    private static void verifiesSweptLegDrivesTheSkirt() throws Exception {
        BoneModelSnapshot model = BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(MODEL)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:swept", model, PhysicsMetadata.EMPTY
                )
        );
        List<BoneModelSnapshot.Bone> legs = legs(model);
        require(!legs.isEmpty(), MODEL + " has no leg bones to swing");
        int[] skirt = skirtSegments(layout);
        require(skirt.length > 0, MODEL + " has no driven skirt segments");

        float driven = 0.0F;
        for (float stride : STRIDES) {
            driven = Math.max(
                    driven,
                    deflection(layout, model, legs, skirt, stride)
            );
        }
        require(
                driven >= RESPONSE,
                "A leg swept through the skirt of " + MODEL + " and moved it"
                        + " by only " + driven + " rad; cloth is ignoring"
                        + " limbs that pass through it"
        );
        require(
                driven <= RUNAWAY,
                "A swept leg threw the skirt of " + MODEL + " " + driven
                        + " rad off its settled pose"
        );
    }

    /** Furthest the skirt is driven off its settled pose at one stride rate. */
    private static float deflection(
            PhysicsSolverLayout layout,
            BoneModelSnapshot model,
            List<BoneModelSnapshot.Bone> legs,
            int[] skirt,
            float stride
    ) {
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }

        Vector3f[] settled = new Vector3f[skirt.length];
        for (int index = 0; index < skirt.length; index++) {
            settled[index] = new Vector3f();
            solver.copyCurrentDirection(
                    layout.node(skirt[index]).drivenSlot(),
                    settled[index]
            );
        }

        Vector3f live = new Vector3f();
        float driven = 0.0F;
        int frames = Math.round(6.0F * stride / DT);
        for (int frame = 0; frame < frames; frame++) {
            float angle = STRIDE_RADIANS * (float) Math.sin(
                    2.0 * Math.PI * frame * DT / stride
            );
            solver.restoreAnimationPose();
            swing(legs, angle);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            if (frame < frames / 4) {
                // The first stride starts from a standstill and lands harder
                // than any that follows; measure the gait, not the lurch.
                continue;
            }
            for (int index = 0; index < skirt.length; index++) {
                solver.copyCurrentDirection(
                        layout.node(skirt[index]).drivenSlot(),
                        live
                );
                driven = Math.max(driven, settled[index].angle(live));
            }
        }
        return driven;
    }

    private static void swing(
            List<BoneModelSnapshot.Bone> legs,
            float angle
    ) {
        for (BoneModelSnapshot.Bone leg : legs) {
            boolean lower = leg.getName().contains("Lower");
            boolean right = leg.getName().startsWith("Right");
            leg.setRotationX((right ? -angle : angle) * (lower ? -0.6F : 1.0F));
        }
    }

    private static List<BoneModelSnapshot.Bone> legs(
            BoneModelSnapshot model
    ) {
        List<BoneModelSnapshot.Bone> found = new ArrayList<>();
        for (BoneModelSnapshot.Bone bone : model.boneList()) {
            String name = bone.getName();
            if (name.equals("LeftLeg") || name.equals("RightLeg")
                    || name.equals("LeftLowerLeg")
                    || name.equals("RightLowerLeg")) {
                found.add(bone);
            }
        }
        return found;
    }

    private static int[] skirtSegments(PhysicsSolverLayout layout) {
        List<Integer> found = new ArrayList<>();
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            PhysicsSolverLayout.Node node = layout.node(index);
            if (node.driven()
                    && node.decision().type()
                    == PhysicsBoneSelectionPlan.PartType.SKIRT
                    && node.constraint().collisionProxies().proxyCount() > 0) {
                found.add(index);
            }
        }
        int[] result = new int[found.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = found.get(index);
        }
        return result;
    }
}
