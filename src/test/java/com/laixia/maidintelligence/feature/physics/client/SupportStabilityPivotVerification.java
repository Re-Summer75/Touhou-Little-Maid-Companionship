package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Verifies scale-independent support-stability pivot selection.
 */
final class SupportStabilityPivotVerification {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;

    private SupportStabilityPivotVerification() {
    }

    static void run() throws Exception {
        verifiesAnonymousCompoundMount();
        verifiesAmbiguousFlexibleMountIsNotForcedRigid();
        verifiesBundledHanfuBow();
    }

    private static void verifiesBundledHanfuBow() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadGeoModel(
                MODEL_DIRECTORY.resolve("zhiban_hanfu.json")
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:support_stability/zhiban_hanfu",
                model,
                PhysicsMetadata.EMPTY
        );
        AnimatedGeoBone bow = model.bones().get("HUDIEJIE");
        BoneKinematics.Metrics metrics = plan.kinematics(bow);
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bow);
        float massCenterY = massCenter(bow.geoBone().cubes()).y;
        require(
                decision.driven()
                        && metrics != null
                        && metrics.supportStabilityPivotCorrected()
                        && metrics.compensatesPivot()
                        && metrics.contactConfidence() >= 0.15F
                        && metrics.authoredPivot().y * PIXELS_PER_BLOCK
                        <= massCenterY * PIXELS_PER_BLOCK - 0.5F
                        && metrics.effectivePivot().y * PIXELS_PER_BLOCK
                        >= massCenterY * PIXELS_PER_BLOCK + 0.5F
                        && metrics.safeAngle() <= 0.1201F
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .COMPOUND_SINGLE_BONE
                        && decision.profile().gravityScale() == 0.0F,
                "HUDIEJIE did not receive a stable support pivot: "
                        + "decision=" + decision
                        + ", authored=" + metrics.authoredPivot()
                        + ", effective=" + metrics.effectivePivot()
                        + ", massCenterY=" + massCenterY
                        + ", contact=" + metrics.contactConfidence()
        );
    }

    private static void verifiesAnonymousCompoundMount() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.support_stability",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"AnonymousMount","parent":"Head","pivot":[0,17,5],
                     "cubes":[
                       {"origin":[-1.5,21,4],"size":[3,3,2],"uv":[0,0]},
                       {"origin":[-7,18,5],"size":[7,4,2],"uv":[0,0]},
                       {"origin":[0,18,5],"size":[7,4,2],"uv":[0,0]},
                       {"origin":[-4,13,5],"size":[3,5,2],"uv":[0,0]},
                       {"origin":[1,13,5],"size":[3,5,2],"uv":[0,0]}
                     ]}
                  ]}]}
                """);
        AnimatedGeoBone head = model.bones().get("Head");
        AnimatedGeoBone attachment = model.bones().get("AnonymousMount");
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                attachment,
                head,
                head,
                PhysicsBoneSelectionPlan.PartType.RIBBON,
                PhysicsBoneSelectionPlan.StructureRole
                        .FLEXIBLE_CHAIN_SEGMENT,
                new PhysicsBoneSelectionPlan.ChainSegment(
                        "anonymous_mount",
                        0,
                        1
                ),
                null
        );
        float massCenterY = massCenter(attachment.geoBone().cubes()).y;
        require(
                metrics.supportStabilityPivotCorrected()
                        && metrics.effectivePivot().y * PIXELS_PER_BLOCK
                        >= massCenterY * PIXELS_PER_BLOCK + 0.5F,
                "Geometry-only mount did not select upper support: "
                        + metrics.effectivePivot()
                        + " / centerY=" + massCenterY
                        + " / contact=" + metrics.contactConfidence()
        );
    }

    private static void verifiesAmbiguousFlexibleMountIsNotForcedRigid() {
        AnimatedGeoModel model = modelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.unsupported_mount",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Head","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"RibbonMount","parent":"Head","pivot":[0,17,10],
                     "cubes":[
                       {"origin":[-1.5,21,10],"size":[3,3,2],"uv":[0,0]},
                       {"origin":[-7,18,11],"size":[7,4,2],"uv":[0,0]},
                       {"origin":[0,18,11],"size":[7,4,2],"uv":[0,0]},
                       {"origin":[-4,13,11],"size":[3,5,2],"uv":[0,0]},
                       {"origin":[1,13,11],"size":[3,5,2],"uv":[0,0]}
                     ]}
                  ]}]}
                """);
        AnimatedGeoBone attachment = model.bones().get("RibbonMount");
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:unsupported_support_mount",
                model,
                PhysicsMetadata.EMPTY
        );
        PhysicsBoneSelectionPlan.Decision decision =
                plan.decision(attachment);
        BoneKinematics.Metrics metrics = plan.kinematics(attachment);
        require(
                decision.driven()
                        && metrics != null
                        && !metrics.supportStabilityUnsupported()
                        && decision.structureRole()
                        == PhysicsBoneSelectionPlan.StructureRole
                        .FLEXIBLE_CHAIN_SEGMENT,
                "Ambiguous cantilever was forced rigid: " + decision
        );
    }

    private static Vector3f massCenter(GeoMesh mesh) {
        Vector3f center = new Vector3f();
        float totalWeight = 0.0F;
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            float x = dx.length();
            float y = dy.length();
            float z = dz.length();
            float weight = Math.max(
                    EPSILON,
                    Math.max(
                            x * y * z,
                            Math.max(x * y, Math.max(x * z, y * z))
                                    / PIXELS_PER_BLOCK
                    )
            );
            center.add(
                    new Vector3f(mesh.position(cube))
                            .fma(0.5F, dx)
                            .fma(0.5F, dy)
                            .fma(0.5F, dz)
                            .mul(weight)
            );
            totalWeight += weight;
        }
        return center.div(Math.max(EPSILON, totalWeight));
    }
}
