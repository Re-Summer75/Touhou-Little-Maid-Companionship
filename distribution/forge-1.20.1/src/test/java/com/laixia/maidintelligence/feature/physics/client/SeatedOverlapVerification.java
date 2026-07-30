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
 * Covers a part that a steady force holds against a collider, which is the
 * ordinary state of cloth: a skirt hangs onto the body it is cut for and stays
 * there for as long as the maid does nothing in particular.
 *
 * <p>Position projection cannot settle that on its own, and the failure is not
 * subtle. Gravity adds a frame's travel into the collider every frame and the
 * projection takes exactly that back out, so the part trembles by that amount
 * indefinitely — on this model it covered 1.5 radians in three seconds of
 * standing still, reversing every other frame, which is plainly visible. What
 * makes it self-sustaining is that nothing about the situation changes between
 * frames, so the constraint keeps answering where the part is while the force
 * keeps pushing it back.
 *
 * <p>The existing squeeze coverage cannot see this. It measures a segment caught
 * between two colliders that reverses frame on frame, and one held against a
 * single collider never reverses at all: every push points the same way, with
 * uncorrected frames in between. It also uses explicit proxies, which skip the
 * rest allowance entirely, so nothing there exercises the path a real model
 * takes.
 */
final class SeatedOverlapVerification {
    private static final float DT = 1.0F / 60.0F;
    /** Long enough for the pose to settle and any standoff to establish. */
    private static final int SETTLE = 600;
    private static final int SAMPLE = 180;
    /** Thigh lift of a seated pose, which folds the hem onto the leg. */
    private static final float THIGH_RADIANS = -1.45F;
    /**
     * Total distance every driven segment may cover over the sample, summed.
     *
     * <p>A held pose asks the solver for one answer, so the honest budget is
     * zero and this is only the tolerance around it. Contact support brings the
     * model to 0.07, against 1.63 without it; the gap either way is wide enough
     * that this reads as "the pose came to rest" rather than as a tuned figure.
     */
    private static final float RESTING_PATH = 0.30F;
    /** No single segment may account for the whole budget on its own. */
    private static final float RESTING_SEGMENT = 0.15F;
    private static final String MODEL = "winefox.json";

    private SeatedOverlapVerification() {
    }

    static void run() throws Exception {
        verifiesHeldOverlapComesToRest();
    }

    private static void verifiesHeldOverlapComesToRest() throws Exception {
        BoneModelSnapshot model = BonePhysicsVerificationSupport.coreModel(
                BonePhysicsVerificationSupport.loadGeoModel(
                        BonePhysicsVerificationSupport.MODEL_DIRECTORY
                                .resolve(MODEL)
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:seated", model, PhysicsMetadata.EMPTY
                )
        );
        List<BoneModelSnapshot.Bone> legs = legs(model);
        require(!legs.isEmpty(), MODEL + " has no leg bones to seat");
        int[] skirt = skirtSegments(layout);
        require(skirt.length > 0, MODEL + " has no driven skirt segments");

        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            seat(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }

        Vector3f[] previous = new Vector3f[skirt.length];
        float[] path = new float[skirt.length];
        for (int index = 0; index < skirt.length; index++) {
            previous[index] = new Vector3f();
            solver.copyCurrentDirection(
                    layout.node(skirt[index]).drivenSlot(),
                    previous[index]
            );
        }

        Vector3f current = new Vector3f();
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            seat(legs);
            solver.solve(new Vector3f(), 0.0F, DT, false);
            for (int index = 0; index < skirt.length; index++) {
                solver.copyCurrentDirection(
                        layout.node(skirt[index]).drivenSlot(),
                        current
                );
                path[index] += current.distance(previous[index]);
                previous[index].set(current);
            }
        }

        float total = 0.0F;
        float worst = 0.0F;
        String worstBone = "";
        for (int index = 0; index < skirt.length; index++) {
            total += path[index];
            if (path[index] > worst) {
                worst = path[index];
                worstBone = layout.node(skirt[index]).bone().getName();
            }
        }
        require(
                total <= RESTING_PATH,
                "A held pose never came to rest on " + MODEL + ": its "
                        + skirt.length + " driven segments travelled " + total
                        + " over " + SAMPLE + " frames of standing still"
        );
        require(
                worst <= RESTING_SEGMENT,
                "Segment " + worstBone + " of " + MODEL + " trembled against"
                        + " its collider, travelling " + worst + " over "
                        + SAMPLE + " frames of a held pose"
        );
    }

    /** Holds a seated pose: thighs forward, shins hanging down. */
    private static void seat(List<BoneModelSnapshot.Bone> legs) {
        for (BoneModelSnapshot.Bone leg : legs) {
            boolean lower = leg.getName().contains("Lower");
            leg.setRotationX(lower ? -THIGH_RADIANS : THIGH_RADIANS);
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
