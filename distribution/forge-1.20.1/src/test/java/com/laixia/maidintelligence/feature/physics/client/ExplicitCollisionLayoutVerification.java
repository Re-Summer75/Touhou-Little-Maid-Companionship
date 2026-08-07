package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.PreparedCollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.RuntimeCollisionFrames;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNear;

final class ExplicitCollisionLayoutVerification {
    private ExplicitCollisionLayoutVerification() {
    }

    static void run() {
        verifiesExplicitCapsuleAndReference();
        verifiesInvalidReferencesAreSkipped();
        verifiesPostorderedSiblingReferenceAtRuntime();
    }

    private static void verifiesExplicitCapsuleAndReference() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.explicit_collision",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0]},
                    {"name":"Hair","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-0.5,12,-0.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"ColliderAnchor","parent":"Body","pivot":[5,12,0],
                     "cubes":[{"origin":[4,8,-1],"size":[2,8,2],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsSolverLayout layout = build(
                model,
                """
                {"schema_version":3,"mode":"explicit","chains":[{
                  "id":"explicit_capsule","type":"HAIR",
                  "root":"Root/Body/Hair","constraints":{
                    "simulation_space":"MODEL",
                    "hit_radius_scale":2.0,
                    "collision":{"auto":false,"proxies":[{
                      "kind":"capsule","reference":"ColliderAnchor",
                      "start":[0,8,0],"end":[0,16,0],
                      "radius":2,"hit_radius":1
                    }]}
                  }}]}
                """
        );
        int hairIndex = indexOf(layout, "Hair");
        int referenceIndex = indexOf(layout, "ColliderAnchor");
        require(
                hairIndex >= 0
                        && referenceIndex >= 0
                        && referenceIndex < hairIndex
                        && layout.referencesPreordered(),
                "Explicit reference and its ancestors were not preordered"
        );
        CollisionProxy proxy = layout.node(hairIndex).constraint()
                .collisionProxies().proxy(0);
        require(
                layout.node(hairIndex).constraint()
                        .collisionProxies().proxyCount() == 1
                        && proxy.kind() == CollisionProxyKind.CAPSULE
                        && proxy.source() == CollisionProxySource.EXPLICIT,
                "auto:false did not retain only the explicit Capsule"
        );
        requireNear(
                proxy.hitRadius(),
                0.125F,
                1.0E-6F,
                "Explicit hit_radius was not converted and scaled"
        );
        Vector3f origin = proxy.copyReferenceOrigin(new Vector3f());
        Vector3f expectedOrigin = PhysicsBoneGeometry.analyze(model)
                .resolve("ColliderAnchor").get(0).bounds().center();
        requireNear(
                origin.x,
                expectedOrigin.x,
                1.0E-6F,
                "Explicit reference origin X did not use bounds center"
        );
        requireNear(
                origin.y,
                expectedOrigin.y,
                1.0E-6F,
                "Explicit reference origin Y did not use bounds center"
        );
        require(
                indexOf(layout, "Root") >= 0
                        && indexOf(layout, "Body") >= 0,
                "Explicit reference ancestors were pruned"
        );
    }

    private static void verifiesInvalidReferencesAreSkipped() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.invalid_reference",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0]},
                    {"name":"Hair","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[0,12,0],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"Anchor","parent":"Body","pivot":[2,8,0],
                     "cubes":[{"origin":[1,8,-1],"size":[2,2,2],"uv":[0,0]}]},
                    {"name":"anchor","parent":"Body","pivot":[-2,8,0],
                     "cubes":[{"origin":[-3,8,-1],"size":[2,2,2],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsSolverLayout layout = build(
                model,
                """
                {"schema_version":3,"mode":"explicit","chains":[{
                  "id":"invalid_references","type":"HAIR",
                  "root":"Root/Body/Hair","constraints":{
                    "simulation_space":"MODEL",
                    "collision":{"auto":false,"proxies":[
                      {"kind":"sphere","reference":"Missing",
                       "center":[0,0,0],"radius":2},
                      {"kind":"sphere","reference":"ANCHOR",
                       "center":[0,0,0],"radius":2}
                    ]}
                  }}]}
                """
        );
        require(
                node(layout, "Hair").constraint()
                        .collisionProxies().proxyCount() == 0,
                "Missing or ambiguous explicit reference was baked"
        );
        require(
                indexOf(layout, "Anchor") < 0
                        && indexOf(layout, "anchor") < 0,
                "Rejected explicit references leaked into active layout"
        );
    }

    private static void verifiesPostorderedSiblingReferenceAtRuntime() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.post_reference",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,2,-2],"size":[6,10,4],"uv":[0,0]}]},
                    {"name":"ZLeg","parent":"Body","pivot":[2,8,0],
                     "cubes":[{"origin":[1,2,-1],"size":[2,6,2],"uv":[0,0]}]},
                    {"name":"Soft","parent":"Body","pivot":[0,12,0],
                     "cubes":[{"origin":[-0.5,8,-0.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsSolverLayout layout = build(model, """
                {"schema_version":3,"mode":"explicit","chains":[
                  {"id":"soft","type":"RIBBON","root":"Root/Body/Soft",
                   "include_descendants":false,"constraints":{
                     "simulation_space":"MODEL",
                     "swing_limits":{"left_degrees":85,"right_degrees":85,
                       "outward_degrees":85,"inward_degrees":85},
                     "collision":{"auto":false,"proxies":[{
                       "kind":"sphere","reference":"ZLeg",
                       "center":[0,8,0],"radius":2,"hit_radius":0}]}}},
                  {"id":"leg","type":"RIBBON","root":"Root/Body/ZLeg",
                   "include_descendants":false,"constraints":{
                     "simulation_space":"MODEL",
                     "collision":{"auto":false,"proxies":[{
                       "kind":"sphere","reference":"Soft",
                       "center":[100,100,100],"radius":1,"hit_radius":0}]}}}
                ]}
                """);
        int softIndex = indexOf(layout, "Soft");
        int legIndex = indexOf(layout, "ZLeg");
        require(softIndex >= 0 && legIndex >= 0,
                "Postordered sibling fixture lost an active node");
        CollisionProxy proxy = layout.node(softIndex).constraint()
                .collisionProxies().proxy(0);
        require(
                legIndex > softIndex
                        && proxy.referenceNodeIndex() == legIndex
                        && !layout.referencesPreordered(),
                "Sibling collision reference was not postordered: soft="
                        + softIndex + ", leg=" + legIndex + ", ref="
                        + proxy.referenceNodeIndex() + ", preordered="
                        + layout.referencesPreordered()
        );

        resetPose(model);
        BoneModelSnapshot.Bone leg = find(model, "ZLeg");
        require(leg != null, "Postordered Leg reference is missing");
        leg.setPositionX(1.0F);
        leg.setPositionY(0.5F);
        leg.setRotationZ(0.2F);
        leg.setScaleX(1.8F);
        leg.setScaleY(0.6F);
        leg.setScaleZ(1.2F);
        RuntimeCollisionFrames frames = new RuntimeCollisionFrames(layout);
        frames.prepare();
        requireNear(
                frames.maxBasisScale(legIndex),
                1.8F,
                1.0E-5F,
                "Leg affine frame lost nonuniform scale"
        );
        SpringBoneSolver solver = new SpringBoneSolver(layout);
        solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        require(solver.lastCollisionProjectionCount() > 0,
                "Postordered Leg reference did not project");
        /*
         * The leg is scaled and swung across the strand in one step. A
         * projection may only move the segment so far per frame, so a
         * violation that large resolves over several frames instead of
         * teleporting; settle before measuring the final clearance.
         */
        for (int frame = 0; frame < 60; frame++) {
            solver.restoreAnimationPose();
            solver.solve(new Vector3f(), 0.0F, 1.0F / 60.0F, true);
        }

        PreparedCollisionProxy prepared = new PreparedCollisionProxy();
        proxy.copyStaticShape(
                frames.restOrientation(legIndex),
                prepared,
                new CollisionScratch()
        );
        Vector3f pivot = new Vector3f();
        Vector3f direction = new Vector3f();
        solver.copyRuntimePivot(softIndex, pivot);
        solver.copyCurrentDirection(
                layout.node(softIndex).drivenSlot(), direction
        );
        prepared.prepare(
                pivot,
                frames.affineDelta(legIndex),
                frames.normalTransform(legIndex),
                frames.maxBasisScale(legIndex),
                frames.maxBasisScale(softIndex)
        );
        float clearance = prepared.clearance(
                direction,
                new CollisionScratch()
        );
        require(clearance >= -2.0E-4F,
                "Postordered affine reference retained penetration: "
                        + clearance + ", pivot=" + pivot
                        + ", direction=" + direction);
    }

    private static void resetPose(BoneModelSnapshot model) {
        for (BoneModelSnapshot.Bone root : model.topLevelBones()) {
            resetPose(root);
        }
    }

    private static void resetPose(BoneModelSnapshot.Bone bone) {
        BoneModelSnapshot.RestPose initial = bone.getInitialSnapshot();
        bone.setRotationX(initial.rotationValueX);
        bone.setRotationY(initial.rotationValueY);
        bone.setRotationZ(initial.rotationValueZ);
        bone.setPositionX(initial.positionOffsetX);
        bone.setPositionY(initial.positionOffsetY);
        bone.setPositionZ(initial.positionOffsetZ);
        bone.setScaleX(initial.scaleValueX);
        bone.setScaleY(initial.scaleValueY);
        bone.setScaleZ(initial.scaleValueZ);
        for (BoneModelSnapshot.Bone child : bone.children()) resetPose(child);
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot model,
            String name
    ) {
        for (BoneModelSnapshot.Bone root : model.topLevelBones()) {
            BoneModelSnapshot.Bone found = find(root, name);
            if (found != null) return found;
        }
        return null;
    }

    private static BoneModelSnapshot.Bone find(
            BoneModelSnapshot.Bone bone,
            String name
    ) {
        if (name.equals(bone.getName())) return bone;
        for (BoneModelSnapshot.Bone child : bone.children()) {
            BoneModelSnapshot.Bone found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static PhysicsSolverLayout build(
            BoneModelSnapshot model,
            String metadataJson
    ) {
        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString(metadataJson).getAsJsonObject(),
                "explicit collision layout verification"
        );
        return PhysicsSolverLayout.build(
                model,
                PhysicsBoneDiscoverer.discover(
                        "verification:explicit_collision",
                        model,
                        metadata
                )
        );
    }

    private static PhysicsSolverLayout.Node node(
            PhysicsSolverLayout layout,
            String name
    ) {
        int index = indexOf(layout, name);
        require(index >= 0, "Missing active layout node " + name);
        return layout.node(index);
    }

    private static int indexOf(PhysicsSolverLayout layout, String name) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (name.equals(layout.node(index).bone().getName())) {
                return index;
            }
        }
        return -1;
    }
}
