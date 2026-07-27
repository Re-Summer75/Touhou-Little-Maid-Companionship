package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxyKind;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.CollisionProxySet;

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
        verifiesFrontSkirtChainsRunToTheHem();
        verifiesVolumetricWaistAndRigidClothMount();
    }

    /**
     * Every spatial scorer rewards geometry behind the body, so a front skirt
     * panel stops looking soft on its own a segment or two down. Continuation
     * must not depend on that, or the front of a skirt freezes while its
     * mirror image at the back keeps swinging.
     */
    private static void verifiesFrontSkirtChainsRunToTheHem()
            throws Exception {
        Fixture fixture = discover("winefox.json");
        requireChain(fixture, "FR", "FR1", "FR2");
        requireChain(fixture, "FL", "FL1", "FL2");
        requireChain(fixture, "FM", "FM1", "FM2");
        requireChain(fixture, "RF", "RF2", "RF3");
        requireChain(discover("winefox_momo.json"), "BL", "BL2", "BL3");
    }

    private static void requireChain(Fixture fixture, String... names) {
        String chainId = null;
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            requireSkirt(fixture, name);
            PhysicsBoneSelectionPlan.Decision decision =
                    fixture.plan().decision(fixture.bone(name));
            if (chainId == null) {
                chainId = decision.chainId();
            }
            require(
                    chainId.equals(decision.chainId())
                            && decision.chainSegment().index() == index
                            && decision.chainSegment().count()
                            >= names.length,
                    name + " left the chain rooted at " + names[0] + ": "
                            + decision
            );
        }
    }

    /**
     * Salesperson Winefox builds the lower jacket from thick body cubes under
     * an anonymous {@code jk2} bone. Its compact {@code cloth} waist ring is a
     * shared mount for four long panels; rotating that ring would lean every
     * panel around the torso centre and force the opposite side through it.
     */
    private static void verifiesVolumetricWaistAndRigidClothMount()
            throws Exception {
        Fixture fixture = discover("winefox_salesperson.json");
        requireRigid(fixture, "jk");
        requireRigid(fixture, "jk2");
        requireRigidClothMount(fixture);
        Fixture schoolUniform = discover("winefox_jk.json");
        requireRigid(schoolUniform, "jk2");
        requireRigidClothMount(schoolUniform);
        Fixture survivor = discover("winefox_survivor.json");
        requireRigid(survivor, "jk2");
        requireRigidClothMount(survivor);

        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                fixture.model(),
                fixture.plan()
        );
        requireMeshReference(layout, fixture, "FrontClothe", "jk2");
    }

    private static void requireRigidClothMount(Fixture fixture) {
        requireRigidMount(fixture, "cloth");
        requireSkirt(fixture, "RightClothe");
        requireSkirt(fixture, "LeftClothe");
        requireSkirt(fixture, "FrontClothe");
        requireSkirt(fixture, "BackClothe");
    }

    private static void requireMeshReference(
            PhysicsSolverLayout layout,
            Fixture fixture,
            String driven,
            String reference
    ) {
        PhysicsSolverLayout.Node node = layoutNode(
                layout,
                fixture.bone(driven)
        );
        require(node != null, driven + " left the solver layout");
        CollisionProxySet proxies = node.constraint().collisionProxies();
        boolean found = false;
        for (int index = 0; index < proxies.proxyCount(); index++) {
            int referenceIndex = proxies.proxy(index).referenceNodeIndex();
            if (proxies.proxy(index).kind() == CollisionProxyKind.BOX
                    && referenceIndex >= 0
                    && layout.node(referenceIndex).bone()
                    == fixture.bone(reference)) {
                found = true;
                break;
            }
        }
        require(
                found,
                driven + " did not receive rigid mesh " + reference
        );
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
        CollisionProxySet proxies = node == null
                ? null
                : node.constraint().collisionProxies();
        require(
                node != null
                        && node.constraint().simulationSpace()
                        == PhysicsBoneSelectionPlan.SimulationSpace.BODY_LOCAL
                        && proxies.proxyCount() > 0
                        && !proxies.hasKind(CollisionProxyKind.CAPSULE)
                        && proxies.hasKind(CollisionProxyKind.BOX),
                "SKIRT did not receive mesh collision"
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

    private static void requireRigidMount(Fixture fixture, String name) {
        PhysicsBoneSelectionPlan.Decision decision =
                fixture.plan().decision(fixture.bone(name));
        require(
                !decision.driven()
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .RIGID_ATTACHMENT_BASE,
                name + " was not preserved as a rigid cloth mount: "
                        + decision
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
