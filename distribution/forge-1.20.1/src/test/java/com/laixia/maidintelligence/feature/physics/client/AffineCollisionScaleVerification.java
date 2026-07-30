package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.laixia.maidintelligence.feature.physics.client.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProxyDebugData;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.RuntimeCollisionFrames;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;

final class AffineCollisionScaleVerification {
    private AffineCollisionScaleVerification() {
    }

    static void run() {
        verifiesShearUsesConservativeScaleBound();
        verifiesColliderAndEndpointScaleAreIndependent();
        verifiesRuntimeSafetyLeverTracksScale();
    }

    private static void verifiesShearUsesConservativeScaleBound() {
        BoneModelSnapshot model = RuntimeEndpointHierarchyFixture.model();
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:affine_scale",
                        model,
                        RuntimeEndpointHierarchyFixture.collisionMetadata()
                )
        );
        BoneModelSnapshot.Bone body = find(model, "Body");
        BoneModelSnapshot.Bone child = find(model, "ChildHair");
        int childIndex = indexOf(layout, "ChildHair");
        require(body != null && child != null && childIndex >= 0,
                "Affine scale fixture is incomplete");
        body.setScaleX(2.0F);
        body.setScaleY(0.5F);
        child.setRotationZ((float) Math.toRadians(45.0D));
        RuntimeCollisionFrames frames = new RuntimeCollisionFrames(layout);
        frames.prepare();
        require(
                frames.maxBasisScale(childIndex) >= 1.99F,
                "Sheared affine frame underestimated its spectral scale"
        );
        body.setScaleX(0.003F);
        body.setScaleY(0.001F);
        body.setScaleZ(0.001F);
        frames.prepare();
        require(
                frames.maxBasisScale(childIndex) >= 0.00299F,
                "Tiny sheared frame was mistaken for orthogonal scaling"
        );
    }

    private static void verifiesColliderAndEndpointScaleAreIndependent() {
        PreparedCollisionProxy proxy = new PreparedCollisionProxy();
        proxy.setSphere(
                0,
                new Vector3f(),
                new Vector3f(),
                1.0F,
                0.25F,
                1.0F
        );
        proxy.prepare(
                new Vector3f(),
                new Matrix4f(),
                new Matrix3f(),
                2.0F,
                0.5F
        );
        CollisionProxyDebugData data = new CollisionProxyDebugData();
        proxy.copyDebugData(
                new Vector3f(1.0F, 0.0F, 0.0F),
                data,
                new CollisionScratch()
        );
        requireNear(data.sphereRadius, 2.0F, 1.0E-6F,
                "Reference scale did not resize the collider");
        requireNear(data.scaledHitRadius, 0.125F, 1.0E-6F,
                "Driven scale did not resize the endpoint radius");
        requireNear(data.leverArm, 0.5F, 1.0E-6F,
                "Driven scale did not resize the fallback collision lever");
        proxy.prepare(
                new Vector3f(),
                new Matrix4f(),
                new Matrix3f(),
                2.0F,
                0.5F,
                1.75F
        );
        proxy.copyDebugData(
                new Vector3f(1.0F, 0.0F, 0.0F),
                data,
                new CollisionScratch()
        );
        requireNear(data.leverArm, 1.75F, 1.0E-6F,
                "Exact runtime segment length was not used by collision");
    }

    private static void verifiesRuntimeSafetyLeverTracksScale() {
        float normal = solveScaledHair(1.0F);
        float enlarged = solveScaledHair(2.0F);
        require(
                normal > enlarged + 0.04F,
                "Runtime scale did not tighten the full-mesh displacement cap"
        );
    }

    private static float solveScaledHair(float scale) {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.scaled_safety",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,4,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"Hair","parent":"Head","pivot":[5,8,0],
                     "cubes":[{"origin":[4.5,0,-.5],"size":[1,8,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"explicit","chains":[{
                          "id":"scaled","type":"HAIR","root":"Head/Hair",
                          "constraints":{"backstop":false,
                            "head_collision":false,
                            "collision":{"auto":false},
                            "swing_limits":{"left_degrees":80,
                              "right_degrees":80,"outward_degrees":80,
                              "inward_degrees":80}}
                        }]}
                        """).getAsJsonObject(),
                "runtime safety scale verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:scaled_safety",
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        BoneModelSnapshot.Bone hair = model.bones().get("Hair");
        hair.setScaleX(scale);
        hair.setScaleY(scale);
        hair.setScaleZ(scale);
        for (int frame = 0; frame < 90; frame++) {
            hair.setRotationX(0.0F);
            hair.setRotationY(0.0F);
            hair.setRotationZ(0.0F);
            solver.solve(
                    new Vector3f(40.0F, 0.0F, 0.0F),
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        int index = indexOf(layout, "Hair");
        Vector3f direction = new Vector3f();
        require(
                solver.copyCurrentDirection(
                        layout.node(index).drivenSlot(),
                        direction
                ),
                "Scaled safety direction was unavailable"
        );
        Vector3f rest = layout.node(index).axisInto(new Vector3f());
        return (float) Math.acos(Math.max(
                -1.0F,
                Math.min(1.0F, direction.dot(rest))
        ));
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot model,
            String name
    ) {
        for (BoneModelSnapshot.Bone root : model.topLevelBones()) {
            BoneModelSnapshot.Bone found = find(root, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot.Bone bone,
            String name
    ) {
        if (name.equals(bone.getName())) {
            return bone;
        }
        for (BoneModelSnapshot.Bone child : bone.children()) {
            BoneModelSnapshot.Bone found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int indexOf(
            PhysicsSolverLayout layout,
            String name
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) {
                return index;
            }
        }
        return -1;
    }
}
