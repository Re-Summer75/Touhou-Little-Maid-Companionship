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
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import org.joml.Vector3f;

import java.util.Arrays;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Covers which colliders survive culling, and that space-splitting the cull
 * buckets does not change the answer.
 *
 * <p>Colliders are bucketed per reference bone and then split until the buckets
 * are small, because one bone's worth of cubes can span a whole body and its
 * bounding sphere then rejects nothing. Culling has to stay conservative
 * through that: a bucket sphere that under-covers its contents silently drops
 * colliders the endpoint is touching, and cloth passes through a body with
 * nothing in the layout to show why. So the contract is both halves at once —
 * every collider the endpoint overlaps is selected, every collider far out of
 * reach is not, and a bucket large enough to split behaves like one that is
 * not.
 */
final class CullBucketSubdivisionVerification {
    private static final float DT = 1.0F / 60.0F;
    private static final int SETTLE = 120;
    /**
     * Colliders wrapped around the endpoint, matching the per-segment active
     * limit so all of them have to be selected and none can be crowded out.
     */
    private static final int TOUCHING = 6;
    /** Enough to push the bucket past the split threshold. */
    private static final int SPLIT = 14;
    /**
     * Clearance no collider the endpoint rests against can exceed. The far
     * colliders sit tens of pixels away, so this separates the two sets by a
     * wide margin rather than a tuned one.
     */
    private static final float NEAR = 1.0F;

    private CullBucketSubdivisionVerification() {
    }

    static void run() {
        verifiesEveryTouchedColliderSurvivesCulling();
        verifiesSplittingSelectsTheSameColliders();
    }

    private static void verifiesEveryTouchedColliderSurvivesCulling() {
        float[] selected = selected(SPLIT);
        require(
                selected.length == TOUCHING,
                "Culling kept " + selected.length + " of the " + TOUCHING
                        + " colliders wrapped around the endpoint"
        );
        require(
                selected[selected.length - 1] <= NEAR,
                "Culling kept a collider that is nowhere near the endpoint: "
                        + selected[selected.length - 1]
        );
    }

    private static void verifiesSplittingSelectsTheSameColliders() {
        float[] unsplit = selected(TOUCHING);
        float[] split = selected(SPLIT);
        require(
                unsplit.length == split.length,
                "Splitting changed how many colliders were selected: "
                        + unsplit.length + " unsplit against " + split.length
        );
        for (int index = 0; index < unsplit.length; index++) {
            require(
                    Math.abs(unsplit[index] - split[index]) <= 1.0E-6F,
                    "Splitting changed the selected colliders at " + index
                            + ": " + unsplit[index] + " against "
                            + split[index]
            );
        }
    }

    /** Clearances of every collider the endpoint was held by, ascending. */
    private static float[] selected(int colliders) {
        BoneModelSnapshot model = model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:buckets",
                        model,
                        PhysicsMetadataJsonParser.parse(
                                JsonParser.parseString(metadata(colliders))
                                        .getAsJsonObject(),
                                "bucket verification"
                        )
                )
        );
        int node = indexOf(layout, "Strand");
        require(node >= 0, "Bucket fixture lost its driven strand");
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 0.0F, false);
        for (int frame = 0; frame < SETTLE; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, DT, false);
        }
        int count = solver.preparedProxyCount(node);
        float[] clearances = new float[count];
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        int written = 0;
        for (int proxy = 0; proxy < count; proxy++) {
            if (solver.copyPreparedCollisionProxy(node, proxy, data)) {
                clearances[written++] = data.clearance;
            }
        }
        float[] trimmed = Arrays.copyOf(clearances, written);
        Arrays.sort(trimmed);
        return trimmed;
    }

    /**
     * Colliders overlapping the endpoint from every side, plus optional far
     * ones that no swing can reach. The far ones only exist to push the bucket
     * past the split threshold, so they must not change the outcome.
     */
    private static String metadata(int colliders) {
        StringBuilder proxies = new StringBuilder();
        appendSphere(proxies, -1.6F, 20.0F, 0.0F, 2.0F);
        appendSphere(proxies, 1.6F, 20.0F, 0.0F, 2.0F);
        appendSphere(proxies, 0.0F, 20.0F, -1.6F, 2.0F);
        appendSphere(proxies, 0.0F, 20.0F, 1.6F, 2.0F);
        appendSphere(proxies, -1.1F, 21.0F, -1.1F, 2.0F);
        appendSphere(proxies, 1.1F, 21.0F, 1.1F, 2.0F);
        for (int extra = TOUCHING; extra < colliders; extra++) {
            appendSphere(proxies, 18.0F + extra, -40.0F, 18.0F, 1.0F);
        }
        return """
                {"schema_version":3,"mode":"explicit","chains":[{
                  "id":"buckets","type":"RIBBON","root":"Root/Body/Strand",
                  "include_descendants":false,"constraints":{
                    "simulation_space":"MODEL",
                    "collision":{"auto":false,"proxies":[%s]}}}]}
                """.formatted(proxies);
    }

    private static void appendSphere(
            StringBuilder output,
            float x,
            float y,
            float z,
            float radius
    ) {
        if (!output.isEmpty()) {
            output.append(',');
        }
        output.append("{\"kind\":\"sphere\",\"reference\":\"Root\",\"center\":[")
                .append(x).append(',').append(y).append(',').append(z)
                .append("],\"radius\":").append(radius)
                .append(",\"hit_radius\":0}");
    }

    private static BoneModelSnapshot model() {
        return coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.buckets",
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
}
