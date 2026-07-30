package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProjector;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers a segment that no pose can satisfy. A seated maid closes gaps below
 * the thickness of what hangs in them, so two colliders end up demanding
 * opposite things; the solver has to settle somewhere and stay there rather
 * than hop between the two surfaces at frame rate.
 */
final class SqueezedContactVerification {
    private static final float DT = 1.0F / 60.0F;
    private static final int SETTLE = 240;
    private static final int SAMPLE = 120;
    /** Escape faces, encoded as {@code axis * 2} plus one when negative. */
    private static final int POSITIVE_X = 0;
    private static final int POSITIVE_Z = 4;

    private SqueezedContactVerification() {
    }

    static void run() {
        verifiesBuriedEndpointKeepsItsExitFace();
        verifiesClearlyShallowerFaceStillWins();
        verifiesLeavingTheBoxForgetsTheFace();
        verifiesSqueezedSegmentSettlesInsteadOfBuzzing();
        verifiesOneSidedContactKeepsFullResponse();
    }

    /**
     * Two faces of a cube are equally deep along its diagonal, and an idle
     * pose trembles across that tie. Re-deciding every frame turns the escape
     * ninety degrees between frames, so the face the endpoint entered through
     * has to survive a near-tie — and survive the endpoint drifting past the
     * centre, which would otherwise push it out the opposite side.
     */
    private static void verifiesBuriedEndpointKeepsItsExitFace() {
        CollisionScratch scratch = new CollisionScratch();
        int tied = bury(scratch, 0.20F, 0.20F, CollisionProjector.NO_FACE);
        require(
                tied == POSITIVE_X,
                "A tie should fall to the first axis, not " + tied
        );
        int held = bury(scratch, 0.20F, 0.2004F, POSITIVE_X);
        require(
                held == POSITIVE_X,
                "A hairline-shallower face stole the escape: " + held
        );
        int crossed = bury(scratch, -0.20F, 0.0F, POSITIVE_X);
        require(
                crossed == POSITIVE_X,
                "Drifting past the centre flipped the escape: " + crossed
        );
    }

    /** Hysteresis is a tie-breaker, not a lock. */
    private static void verifiesClearlyShallowerFaceStillWins() {
        CollisionScratch scratch = new CollisionScratch();
        int face = bury(scratch, 0.02F, 0.46F, POSITIVE_X);
        require(
                face == POSITIVE_Z,
                "A clearly shallower face was ignored: " + face
        );
    }

    private static void verifiesLeavingTheBoxForgetsTheFace() {
        CollisionScratch scratch = new CollisionScratch();
        Vector3f direction = new Vector3f(0.0F, 1.0F, 0.0F);
        scratch.setExitFace(POSITIVE_X);
        require(
                !project(direction, scratch),
                "An endpoint above the box should not project"
        );
        require(
                scratch.exitFace() == CollisionProjector.NO_FACE,
                "The escape face outlived the contact: " + scratch.exitFace()
        );
    }

    /**
     * Drops the endpoint into the box leaning by {@code x} and {@code z},
     * and reports which face the projection chose.
     */
    private static int bury(
            CollisionScratch scratch,
            float x,
            float z,
            int previous
    ) {
        Vector3f direction = new Vector3f(x, -1.0F, z).normalize();
        scratch.setExitFace(previous);
        require(
                project(direction, scratch),
                "The endpoint failed to reach the box at " + x + "," + z
        );
        return scratch.exitFace();
    }

    /** A unit cube of half extent {@code 0.25} centred under the pivot. */
    private static boolean project(
            Vector3f direction,
            CollisionScratch scratch
    ) {
        return CollisionProjector.projectBox(
                direction,
                new Vector3f(0.0F, 0.5F, 0.0F),
                new Vector3f(),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.25F, 0.25F, 0.25F),
                0.0F,
                CollisionProjector.CLOSED_BOX,
                0.5F,
                scratch
        );
    }

    private static void verifiesSqueezedSegmentSettlesInsteadOfBuzzing() {
        Trace trace = drive(squeezed());
        require(
                trace.reversals <= SAMPLE / 10,
                "A squeezed segment kept reversing every frame: "
                        + trace.reversals + " reversals in " + SAMPLE
                        + " frames"
        );
        require(
                trace.path <= 0.02F,
                "A squeezed segment never came to rest: travelled "
                        + trace.path + " rad while settled"
        );
    }

    /**
     * Damping keys on the reversal, not on contact, so a segment resting
     * against one collider must still be answered at full strength.
     */
    private static void verifiesOneSidedContactKeepsFullResponse() {
        Trace trace = drive(oneSided());
        require(
                trace.clearance >= -1.0E-3F,
                "One-sided contact sank into the collider: " + trace.clearance
        );
        require(
                trace.path <= 0.02F,
                "One-sided contact never came to rest: " + trace.path
        );
    }

    private static Trace drive(String metadata) {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:squeeze",
                        model,
                        PhysicsMetadataJsonParser.parse(
                                JsonParser.parseString(metadata)
                                        .getAsJsonObject(),
                                "squeeze verification"
                        )
                )
        );
        int node = indexOf(layout, "Strand");
        require(node >= 0, "Squeeze fixture lost its driven strand");
        int slot = layout.node(node).drivenSlot();
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }

        Trace trace = new Trace();
        Vector3f previous = new Vector3f();
        Vector3f current = new Vector3f();
        Vector3f step = new Vector3f();
        Vector3f lastStep = new Vector3f();
        solver.copyCurrentDirection(slot, previous);
        for (int frame = 0; frame < SAMPLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, DT, false);
            solver.copyCurrentDirection(slot, current);
            step.set(current).sub(previous);
            float travelled = step.length();
            trace.path += travelled;
            if (travelled > 1.0E-4F
                    && lastStep.lengthSquared() > 1.0E-8F
                    && step.dot(lastStep) < 0.0F) {
                trace.reversals++;
            }
            lastStep.set(step);
            previous.set(current);
        }
        trace.clearance = nearestClearance(solver, node);
        return trace;
    }

    private static float nearestClearance(SpringBoneSolver solver, int node) {
        int count = solver.preparedProxyCount(node);
        float nearest = Float.MAX_VALUE;
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        for (int proxy = 0; proxy < count; proxy++) {
            if (solver.copyPreparedCollisionProxy(node, proxy, data)) {
                nearest = Math.min(nearest, data.clearance);
            }
        }
        return nearest == Float.MAX_VALUE ? 0.0F : nearest;
    }

    /**
     * Two spheres overlapping the strand's endpoint from opposite sides. Each
     * one alone is satisfiable; together they leave nowhere legal to be.
     */
    private static String squeezed() {
        return """
                {"schema_version":3,"mode":"explicit","chains":[{
                  "id":"squeeze","type":"RIBBON","root":"Root/Body/Strand",
                  "include_descendants":false,"constraints":{
                    "simulation_space":"MODEL",
                    "collision":{"auto":false,"proxies":[
                      {"kind":"sphere","reference":"Root",
                       "center":[-1.8,20,0],"radius":2,"hit_radius":0},
                      {"kind":"sphere","reference":"Root",
                       "center":[1.8,20,0],"radius":2,"hit_radius":0}]}}}]}
                """;
    }

    /** One sphere the endpoint rests against, with room on the other side. */
    private static String oneSided() {
        return """
                {"schema_version":3,"mode":"explicit","chains":[{
                  "id":"squeeze","type":"RIBBON","root":"Root/Body/Strand",
                  "include_descendants":false,"constraints":{
                    "simulation_space":"MODEL",
                    "collision":{"auto":false,"proxies":[
                      {"kind":"sphere","reference":"Root",
                       "center":[-1.8,20,0],"radius":2,"hit_radius":0}]}}}]}
                """;
    }

    private static BoneModelSnapshot model() {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.squeeze",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,8,-2],"size":[6,16,4],
                       "uv":[0,0]}]},
                    {"name":"Strand","parent":"Body","pivot":[0,24,0],
                     "cubes":[{"origin":[-0.5,20,-0.5],"size":[1,4,1],
                       "uv":[0,0]}]}
                  ]}]}
                """);
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone().getName().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static final class Trace {
        private float path;
        private int reversals;
        private float clearance;
    }
}
