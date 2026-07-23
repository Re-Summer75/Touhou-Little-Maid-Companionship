package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;

import java.nio.file.Path;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireDriven;

final class RigidAttachmentVerification {
    private RigidAttachmentVerification() {
    }

    static void run() throws Exception {
        verifiesWinefoxFlowerMount();
        verifiesRiceCakeFoxAttachments();
    }

    private static void verifiesWinefoxFlowerMount() throws Exception {
        Verification fixture = discover("winefox_elf.json");
        requireNotDriven(
                fixture,
                "flower",
                "Winefox elf flower mount was physicalized"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("bone5"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Winefox elf flexible hair was lost with the flower mount"
        );
    }

    private static void verifiesRiceCakeFoxAttachments() throws Exception {
        Verification fixture = discover("rice_cake_fox.json");
        requireNotDriven(
                fixture,
                "Balls",
                "Rice cake fox rigid hair balls were physicalized"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("LeftPony"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Rice cake fox left ponytail was lost"
        );
        requireDriven(
                fixture.plan(),
                fixture.model().bones().get("RightPony"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Rice cake fox right ponytail was lost"
        );
    }

    private static Verification discover(String fileName) throws Exception {
        Path path = MODEL_DIRECTORY.resolve(fileName);
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(path));
        String modelName = fileName.substring(0, fileName.length() - 5);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "geckolib:" + modelName,
                model,
                PhysicsMetadata.EMPTY
        );
        return new Verification(model, plan);
    }

    private static void requireNotDriven(
            Verification fixture,
            String boneName,
            String message
    ) {
        AnimatedGeoBone bone = fixture.model().bones().get(boneName);
        require(bone != null, message + " (bone missing)");
        require(!fixture.plan().isDriven(bone), message);
    }

    private record Verification(
            AnimatedGeoModel model,
            PhysicsBoneSelectionPlan plan
    ) {
    }
}
