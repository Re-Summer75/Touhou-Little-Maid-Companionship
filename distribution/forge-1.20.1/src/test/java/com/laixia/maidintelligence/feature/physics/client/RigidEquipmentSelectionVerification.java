package com.laixia.maidintelligence.feature.physics.client;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxy;
import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionProxySource;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

/**
 * Verifies that authored equipment subtrees do not become flexible chains.
 */
final class RigidEquipmentSelectionVerification {
    private RigidEquipmentSelectionVerification() {
    }

    static void run() throws Exception {
        verifiesBundledHeadwearStaysRigid();
        verifiesBundledMagicEquipmentStaysRigid();
        verifiesBundledWeaponsStayRigid();
        verifiesEquipmentIsNotAnAutomaticCollider();
        verifiesHandAttachmentBoundary();
        verifiesArticulatedHandDescendantsRemainDiscoverable();
        verifiesHairScopeAndMetadataOverride();
    }

    private static void verifiesBundledHeadwearStaysRigid()
            throws Exception {
        Fixture magical = discover("winefox_magical.json");
        requireRigidSubtree(magical, "witchcap");
        requireRigidSubtree(magical, "witchcap2");
        requireRigidSubtree(magical, "witchcap3");
        requireDriven(
                magical.plan(),
                magical.bone("Bangs"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Magical Winefox bangs were frozen with the hats"
        );
    }

    private static void verifiesBundledWeaponsStayRigid()
            throws Exception {
        requireRigidSubtree(discover("winefox_tactics.json"), "gun");
        requireRigidSubtree(
                discover("winefox_matured.json"),
                "swordroot"
        );
        requireRigidSubtree(
                discover("hailuo_new_year.json"),
                "swordroot"
        );
    }

    private static void verifiesBundledMagicEquipmentStaysRigid()
            throws Exception {
        Fixture magical = discover("winefox_magical.json");
        requireRigidSubtree(magical, "mofazhang");
        requireRigidSubtree(magical, "mofazhang2");
        requireRigidSubtree(magical, "mofazhang3");
        requireRigidSubtree(magical, "MfazhenL");
        requireRigidSubtree(magical, "MfazhenR");
        requireRigidSubtree(magical, "ysmGlowmofazhen19");
    }

    private static void verifiesEquipmentIsNotAnAutomaticCollider()
            throws Exception {
        Fixture tactics = discover("winefox_tactics.json");
        requireNoAutomaticReference(tactics, "gun");
        requireNoAutomaticReference(tactics, "RightHandLocator");
        requireNoAutomaticReference(tactics, "erji");
        requireNoAutomaticReference(tactics, "xiongmgua");
        requireNoAutomaticReference(tactics, "yaodai");
        requireNoAutomaticReference(tactics, "tuidai");

        Fixture magical = discover("winefox_magical.json");
        requireNoAutomaticReference(magical, "witchcap");
        requireNoAutomaticReference(magical, "ysmGlowmofazhen19");

        Fixture sta = discover("sta.json");
        requireNoAutomaticReference(sta, "74m");
        requireNoAutomaticReference(sta, "74m3");
    }

    private static void verifiesHandAttachmentBoundary() throws Exception {
        Fixture saint = discover("winefox_saint.json");
        requireRigidSubtree(saint, "MagicStick");
        requireRigidSubtree(saint, "ysmGlow_MagicBow");
        PhysicsBoneSelectionPlan.Decision decision =
                saint.plan().decision(saint.bone("MagicStick"));
        require(
                decision.reason().contains("attached to hand"),
                "Vocabulary-free hand equipment did not use the structural "
                        + "boundary: " + decision
        );
    }

    private static void verifiesArticulatedHandDescendantsRemainDiscoverable()
            throws Exception {
        requireAutomaticallyDriven(
                discover("winefox_wedding.json"),
                "Flower",
                "Wedding bouquet petals were frozen with the hand"
        );
    }

    private static void verifiesHairScopeAndMetadataOverride() {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.rigid_equipment",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],
                       "uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],
                       "uv":[0,0]}]},
                    {"name":"Hat","parent":"Head","pivot":[0,23,0],
                     "cubes":[{"origin":[-5,22,-5],"size":[10,1,10],
                       "uv":[0,0]}]},
                    {"name":"FixedPanel","parent":"Hat","pivot":[0,23,0],
                     "cubes":[{"origin":[-4,15,4],"size":[8,8,1],
                       "uv":[0,0]}]},
                    {"name":"HairAnchor","parent":"Hat","pivot":[0,22,-4]},
                    {"name":"Bangs","parent":"HairAnchor","pivot":[0,22,-4],
                     "cubes":[{"origin":[-2,16,-5],"size":[4,7,1],
                       "uv":[0,0]}]},
                    {"name":"swordroot","parent":"Body","pivot":[4,12,0]},
                    {"name":"piece","parent":"swordroot","pivot":[4,12,0],
                     "cubes":[{"origin":[3,0,-.5],"size":[2,12,1],
                       "uv":[0,0]}]}
                  ]}]}
                """);
        PhysicsBoneSelectionPlan automatic = PhysicsBoneDiscoverer.discover(
                "verification:rigid_equipment:auto",
                model,
                PhysicsMetadata.EMPTY
        );
        require(
                !automatic.isDriven(model.bones().get("FixedPanel")),
                "Anonymous fixed hat panel became physical: "
                        + automatic.decision(model.bones().get("FixedPanel"))
        );
        requireDriven(
                automatic,
                model.bones().get("Bangs"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Explicit hair scope under a hat was frozen"
        );
        require(
                !automatic.isDriven(model.bones().get("piece")),
                "Anonymous weapon descendant became physical: "
                        + automatic.decision(model.bones().get("piece"))
        );

        PhysicsMetadata metadata = PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {"schema_version":3,"mode":"auto","chains":[{
                          "id":"authored_hat_tassel","type":"RIBBON",
                          "root":"FixedPanel",
                          "constraints":{"collision":{"auto":false}}
                        }]}
                        """).getAsJsonObject(),
                "rigid equipment metadata override"
        );
        PhysicsBoneSelectionPlan authored = PhysicsBoneDiscoverer.discover(
                "verification:rigid_equipment:metadata",
                model,
                metadata
        );
        PhysicsBoneSelectionPlan.Decision decision =
                authored.decision(model.bones().get("FixedPanel"));
        require(
                decision.driven()
                        && decision.source()
                        == PhysicsBoneSelectionPlan.Source.METADATA
                        && decision.type()
                        == PhysicsBoneSelectionPlan.PartType.RIBBON,
                "Metadata did not override the automatic equipment boundary: "
                        + decision
        );
    }

    private static Fixture discover(String fileName) throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:rigid_equipment:" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        return new Fixture(fileName, model, plan);
    }

    private static void requireRigidSubtree(
            Fixture fixture,
            String rootName
    ) {
        BoneModelSnapshot.Bone root = fixture.bone(rootName);
        require(
                root != null,
                fixture.fileName() + " is missing equipment root " + rootName
        );
        requireRigidSubtree(fixture, root, rootName);
    }

    private static void requireRigidSubtree(
            Fixture fixture,
            BoneModelSnapshot.Bone bone,
            String rootName
    ) {
        require(
                !fixture.plan().isDriven(bone),
                fixture.fileName() + " equipment subtree " + rootName
                        + " physicalized " + bone.getName() + ": "
                        + fixture.plan().decision(bone)
        );
        for (BoneModelSnapshot.Bone child : bone.children()) {
            requireRigidSubtree(fixture, child, rootName);
        }
    }

    private static void requireAutomaticallyDriven(
            Fixture fixture,
            String boneName,
            String message
    ) {
        BoneModelSnapshot.Bone bone = fixture.bone(boneName);
        require(
                bone != null && fixture.plan().isDriven(bone),
                message + ": " + (bone == null
                        ? "missing"
                        : fixture.plan().decision(bone))
        );
    }

    private static void requireNoAutomaticReference(
            Fixture fixture,
            String rootName
    ) {
        BoneModelSnapshot.Bone root = fixture.bone(rootName);
        require(root != null, fixture.fileName() + " is missing " + rootName);
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(
                fixture.model(), fixture.plan()
        );
        for (int nodeIndex = 0;
             nodeIndex < layout.activeNodeCount();
             nodeIndex++) {
            PhysicsSolverLayout.Node node = layout.node(nodeIndex);
            if (!node.driven()) {
                continue;
            }
            for (int proxyIndex = 0;
                 proxyIndex < node.constraint().collisionProxies().proxyCount();
                 proxyIndex++) {
                CollisionProxy proxy = node.constraint()
                        .collisionProxies().proxy(proxyIndex);
                int reference = proxy.referenceNodeIndex();
                require(
                        proxy.source() != CollisionProxySource.AUTOMATIC
                                || reference < 0
                                || !isInSubtree(
                                layout.node(reference).bone(), root
                        ),
                        fixture.fileName() + " used " + rootName
                                + " as an automatic collision source for "
                                + node.bone().getName()
                );
            }
        }
    }

    private static boolean isInSubtree(
            BoneModelSnapshot.Bone bone,
            BoneModelSnapshot.Bone root
    ) {
        BoneModelSnapshot.Bone cursor = bone;
        while (cursor != null) {
            if (cursor == root) {
                return true;
            }
            cursor = cursor.parent();
        }
        return false;
    }

    private record Fixture(
            String fileName,
            BoneModelSnapshot model,
            PhysicsBoneSelectionPlan plan
    ) {
        BoneModelSnapshot.Bone bone(String name) {
            return model.bones().get(name);
        }
    }
}
