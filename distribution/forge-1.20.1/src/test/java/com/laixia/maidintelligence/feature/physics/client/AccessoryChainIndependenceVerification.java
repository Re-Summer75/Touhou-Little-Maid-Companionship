package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class AccessoryChainIndependenceVerification {
    private AccessoryChainIndependenceVerification() {
    }

    static void run() throws Exception {
        verifiesZhibanSegmentGradient();
        verifiesWinefoxSegmentGradient();
        verifiesSegmentsRespondIndependently();
        verifiesVisibleRigidGapBreaksChain();
    }

    private static void verifiesZhibanSegmentGradient() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("zhiban_hanfu.json")
        ));
        PhysicsBoneSelectionPlan plan = discover(model, "zhiban_hanfu");
        verifyGradient(
                plan,
                model.bones().get("LeftMWX"),
                model.bones().get("LeftMWX2"),
                model.bones().get("LeftMWX3"),
                "Zhiban Hanfu left ponytail"
        );
        verifyGradient(
                plan,
                model.bones().get("RightMWX"),
                model.bones().get("RightMWX2"),
                model.bones().get("RightMWX3"),
                "Zhiban Hanfu right ponytail"
        );
        PhysicsBoneSelectionPlan.Decision left =
                plan.decision(model.bones().get("LeftMWX"));
        PhysicsBoneSelectionPlan.Decision right =
                plan.decision(model.bones().get("RightMWX"));
        require(
                right.driven() && !left.chainId().equals(right.chainId()),
                "Zhiban Hanfu ponytail sides shared one chain id"
        );
    }

    private static void verifiesWinefoxSegmentGradient() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("winefox.json")
        ));
        PhysicsBoneSelectionPlan plan = discover(model, "winefox");
        verifyGradient(
                plan,
                model.bones().get("LongLeftHair"),
                null,
                model.bones().get("LongLeftHair2"),
                "Winefox left long hair"
        );
    }

    private static void verifiesSegmentsRespondIndependently() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.segment_response",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,4,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairRoot","parent":"Head","pivot":[3,8,0],
                     "cubes":[{"origin":[2,4,-.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"HairMid","parent":"HairRoot","pivot":[3,4,0],
                     "cubes":[{"origin":[2,0,-.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"HairTip","parent":"HairMid","pivot":[3,0,0],
                     "cubes":[{"origin":[2,-4,-.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsBoneSelectionPlan plan = discover(model, "segment_response");
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        SpringBoneSolver solver = new SpringBoneSolver(layout, false);
        Vector3f released = new Vector3f();
        for (int frame = 0; frame < 12; frame++) {
            resetPose(model);
            solver.solve(released, 0.0F, 1.0F / 60.0F, false);
        }
        AnimatedGeoBone root = model.bones().get("HairRoot");
        AnimatedGeoBone tip = model.bones().get("HairTip");
        Vector3f controlRoot = rotation(root, new Vector3f());
        Vector3f controlTip = rotation(tip, new Vector3f());

        solver.reset();
        Vector3f impulse = new Vector3f(8.0F, 0.0F, 0.0F);
        for (int frame = 0; frame < 12; frame++) {
            resetPose(model);
            solver.solve(
                    frame == 0 ? impulse : released,
                    0.0F,
                    1.0F / 60.0F,
                    false
            );
        }
        Vector3f rootResponse =
                rotation(root, new Vector3f()).sub(controlRoot);
        Vector3f tipResponse =
                rotation(tip, new Vector3f()).sub(controlTip);
        require(
                rootResponse.lengthSquared() > 1.0E-8F
                        && tipResponse.lengthSquared() > 1.0E-8F
                        && rootResponse.distance(tipResponse) > 1.0E-4F,
                "A one-frame impulse did not produce independent local "
                        + "segment responses above the no-input control"
        );
    }

    private static void verifiesVisibleRigidGapBreaksChain() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.visible_chain_gap",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,8,0],
                     "cubes":[{"origin":[-4,4,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairRoot","parent":"Head","pivot":[3,8,0],
                     "cubes":[{"origin":[2.5,4,-.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"FacePlate","parent":"HairRoot","pivot":[3,4,0],
                     "cubes":[{"origin":[2.5,3,-.5],"size":[1,1,1],"uv":[0,0]}]},
                    {"name":"HairTip","parent":"FacePlate","pivot":[3,3,0],
                     "cubes":[{"origin":[2.5,-1,-.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsBoneSelectionPlan plan = discover(model, "visible_chain_gap");
        PhysicsBoneSelectionPlan.Decision root =
                plan.decision(model.bones().get("HairRoot"));
        PhysicsBoneSelectionPlan.Decision tip =
                plan.decision(model.bones().get("HairTip"));
        require(
                root.driven() && tip.driven()
                        && !plan.isDriven(model.bones().get("FacePlate"))
                        && root.chainSegment().count() == 1
                        && tip.chainSegment().count() == 1
                        && tip.chainSegment().index() == 0,
                "A rejected visible bone was crossed as a flexible segment"
        );
    }

    private static void verifyGradient(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone root,
            AnimatedGeoBone middle,
            AnimatedGeoBone tip,
            String label
    ) {
        require(root != null && tip != null, label + " fixture is incomplete");
        PhysicsBoneSelectionPlan.Decision first = plan.decision(root);
        PhysicsBoneSelectionPlan.Decision last = plan.decision(tip);
        require(first.driven() && last.driven(), label + " was not driven");
        require(
                first.chainId().equals(last.chainId()),
                label + " was split into unrelated chains"
        );
        require(
                first.chainSegment().index() == 0
                        && last.chainSegment().index()
                        == last.chainSegment().count() - 1
                        && last.chainSegment().count() >= 2,
                label + " has invalid segment indices"
        );
        require(
                first.profile().stiffnessScale()
                        > last.profile().stiffnessScale()
                        && first.profile().dragScale()
                        > last.profile().dragScale()
                        && first.profile().massScale()
                        > last.profile().massScale()
                        && first.profile().inertiaScale()
                        < last.profile().inertiaScale()
                        && first.profile().angleScale()
                        < last.profile().angleScale()
                        && first.constraints().rotationInertiaScale()
                        < last.constraints().rotationInertiaScale(),
                label + " did not receive a root-to-tip dynamics gradient"
        );
        if (middle != null) {
            PhysicsBoneSelectionPlan.Decision center = plan.decision(middle);
            require(
                    center.driven()
                            && center.chainSegment().index() > 0
                            && center.chainSegment().index()
                            < last.chainSegment().index(),
                    label + " middle segment is not independently indexed"
            );
        }
    }

    private static PhysicsBoneSelectionPlan discover(
            AnimatedGeoModel model,
            String id
    ) {
        return PhysicsBoneDiscoverer.discover(
                "verification:" + id,
                model,
                PhysicsMetadata.EMPTY
        );
    }

    private static void resetPose(AnimatedGeoModel model) {
        for (AnimatedGeoBone bone : model.bones().values()) {
            bone.setRotationX(0.0F);
            bone.setRotationY(0.0F);
            bone.setRotationZ(0.0F);
            bone.setPositionX(0.0F);
            bone.setPositionY(0.0F);
            bone.setPositionZ(0.0F);
            bone.setScaleX(1.0F);
            bone.setScaleY(1.0F);
            bone.setScaleZ(1.0F);
        }
    }

    private static Vector3f rotation(
            AnimatedGeoBone bone,
            Vector3f output
    ) {
        return output.set(
                bone.getRotationX(),
                bone.getRotationY(),
                bone.getRotationZ()
        );
    }
}
