package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class ClothAccessoryDiscoveryVerification {
    private ClothAccessoryDiscoveryVerification() {
    }

    static void run() throws Exception {
        verifiesZhibanRigidFlexibleSplit();
        verifiesWinefoxMasks();
        verifiesSkirtFixtures();
        verifiesWinefoxSkirtChain();
    }

    private static void verifiesZhibanRigidFlexibleSplit() throws Exception {
        Fixture fixture = discover("zhiban.json");
        requireRigid(fixture, "bone32");
        requireRigid(fixture, "bone11");
        requireRigid(fixture, "bone38");
        requireRigid(fixture, "bone37");
        requireDriven(
                fixture.plan(),
                fixture.bone("qunzi"),
                PhysicsBoneSelectionPlan.PartType.SKIRT,
                "Zhiban qunzi was not discovered as skirt"
        );
        require(
                fixture.plan().decision(fixture.bone("qunzi"))
                        .structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE,
                "Zhiban qunzi was not given the single-bone skirt profile: "
                        + fixture.plan().decision(fixture.bone("qunzi"))
        );
        requireRigid(fixture, "guashi");
        require(
                !fixture.plan().decision(fixture.bone("qunzi")).chainId()
                        .equals(fixture.plan().decision(
                                fixture.bone("guashi")
                        ).chainId()),
                "Zhiban skirt and pendant were merged into one chain"
        );
    }

    private static void verifiesWinefoxMasks() throws Exception {
        requireDangling(discover("winefox.json"), "Mask");
        requireDangling(discover("winefox_wedding.json"), "Mask");
        requireRigid(discover("winefox_little.json"), "Mask");
        Fixture astronaut = discover("winefox_astronaut.json");
        requireRigid(astronaut, "SHmask");
        requireRigid(astronaut, "SpaceHelmet");
    }

    private static void verifiesSkirtFixtures() throws Exception {
        Fixture saint = discover("winefox_saint.json");
        requireSkirt(saint, "SkirtBone");
        requireSkirt(saint, "SkirtBone2");
        require(
                !saint.plan().decision(saint.bone("SkirtBone")).chainId()
                        .equals(saint.plan().decision(
                                saint.bone("SkirtBone2")
                        ).chainId()),
                "Independent saint skirt panels shared a chain id"
        );
        Fixture riceCake = discover("rice_cake_fox.json");
        requireSkirt(riceCake, "Dress");
        require(
                riceCake.plan().decision(riceCake.bone("Dress"))
                        .structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE,
                "Rice Cake Fox Dress did not receive single-bone dynamics"
        );
        requireSkirt(discover("kluonoa.json"), "SkirtUp");
        Fixture wedding = discover("winefox_wedding.json");
        require(
                !wedding.plan().isDriven(wedding.bone("LongerSkirt"))
                        && !wedding.plan().isDriven(
                        wedding.bone("InnerSkirt2")
                )
                        && !wedding.plan().isDriven(
                        wedding.bone("OutterSkirt2")
                ),
                "Wedding skirt semantic anchors were driven without geometry"
        );
        requireSkirt(wedding, "bone22");
        requireSkirt(wedding, "bone32");
    }

    private static void verifiesWinefoxSkirtChain() throws Exception {
        Fixture fixture = discover("winefox.json");
        requireSkirt(fixture, "LF");
        requireSkirt(fixture, "LF2");
        PhysicsBoneSelectionPlan.Decision root =
                fixture.plan().decision(fixture.bone("LF"));
        PhysicsBoneSelectionPlan.Decision tip =
                fixture.plan().decision(fixture.bone("LF2"));
        require(
                root.chainId().equals(tip.chainId())
                        && root.chainSegment().index() == 0
                        && tip.chainSegment().index() == 1
                        && tip.chainSegment().count() >= 2
                        && root.profile().stiffnessScale()
                        > tip.profile().stiffnessScale()
                        && root.profile().gravityScale() > 0.90F
                        && tip.profile().gravityScale()
                        > root.profile().gravityScale()
                        && root.profile().massScale()
                        > tip.profile().massScale()
                        && root.profile().angleScale()
                        < tip.profile().angleScale()
                        && root.constraints().swingLimits().inward()
                        <= Math.toRadians(8.1D),
                "Winefox skirt chain did not receive segmented dynamics"
        );
        PhysicsSolverLayout.Node node = layoutNode(
                PhysicsSolverLayout.build(fixture.model(), fixture.plan()),
                fixture.bone("LF")
        );
        require(
                node != null
                        && node.constraint().simulationSpace()
                        == PhysicsBoneSelectionPlan.SimulationSpace.BODY_LOCAL
                        && node.constraint().collisionProxies().proxyCount() == 1
                        && node.constraint().collisionProxies()
                        .hasKind(CollisionProxyKind.CAPSULE),
                "SKIRT did not retain only its Body collision proxy"
        );
    }

    private static void requireDangling(Fixture fixture, String name) {
        AnimatedGeoBone bone = fixture.bone(name);
        requireDriven(
                fixture.plan(),
                bone,
                PhysicsBoneSelectionPlan.PartType.RIBBON,
                name + " was not discovered as a dangling accessory"
        );
        PhysicsBoneSelectionPlan.Decision decision =
                fixture.plan().decision(bone);
        require(
                decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .DANGLING_ACCESSORY
                        && decision.profile().gravityScale() == 0.0F
                        && decision.profile().angleScale() <= 0.30F
                        && decision.constraints().rotationInertiaScale()
                        <= 0.10F,
                name + " did not receive rest-preserving bounded dynamics: "
                        + decision
        );
    }

    private static void requireSkirt(Fixture fixture, String name) {
        requireDriven(
                fixture.plan(),
                fixture.bone(name),
                PhysicsBoneSelectionPlan.PartType.SKIRT,
                name + " was not discovered as skirt"
        );
    }

    private static void requireRigid(Fixture fixture, String name) {
        AnimatedGeoBone bone = fixture.bone(name);
        require(
                bone != null && !fixture.plan().isDriven(bone),
                name + " should remain rigid: " + fixture.plan().decision(bone)
        );
    }

    private static PhysicsSolverLayout.Node layoutNode(
            PhysicsSolverLayout layout,
            AnimatedGeoBone bone
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == bone) {
                return layout.node(index);
            }
        }
        return null;
    }

    private static Fixture discover(String fileName) throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        return new Fixture(model, plan);
    }

    private record Fixture(AnimatedGeoModel model,
                           PhysicsBoneSelectionPlan plan) {
        AnimatedGeoBone bone(String name) { return model.bones().get(name); }
    }
}
