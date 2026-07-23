package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.ChainType;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.Classification;
import com.laixia.maidintelligence.feature.physics.client.solver.AdaptiveMotionFilter;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionNoiseGate;
import com.laixia.maidintelligence.feature.physics.client.solver.MotionSignalSampler;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline checks for semantic hints, metadata precedence, and real Gecko
 * rest-pose discovery. The spring integration itself remains an in-game check.
 */
public final class BonePhysicsVerification {
    private BonePhysicsVerification() {
    }

    public static void main(String[] args) throws Exception {
        verifiesTailChainClassification();
        verifiesPivotAndRigidBonesAreSkipped();
        verifiesHairAndEarClassification();
        verifiesClassifierNormalisesNames();
        verifiesNumberedSegmentsAndPinyin();
        verifiesExtendedSoftPartHints();
        verifiesMotionCoordinateFrame();
        verifiesTimeCorrectedDrag();
        verifiesAdaptiveMotionDenoising();
        verifiesMetadataParsing();
        verifiesEmptyAnchorFallback();
        verifiesAnonymousExtendedGeometryDiscovery();
        verifiesWinefoxGeometryDiscovery();
        System.out.println("Bone physics verification passed.");
    }

    private static void verifiesTailChainClassification() {
        requireChain("Tail", ChainType.TAIL, 0);
        requireChain("Tail2", ChainType.TAIL, 1);
        requireChain("Tail4", ChainType.TAIL, 3);
        requireChain("Tail7", ChainType.TAIL, 6);
    }

    private static void verifiesPivotAndRigidBonesAreSkipped() {
        requireNone("MTail");
        requireNone("MHead");
        requireNone("MRightSideHair");
        requireNone("Head");
        requireNone("UpBody");
        requireNone("UpperBody");
    }

    private static void verifiesHairAndEarClassification() {
        require(
                PhysicsBoneClassifier.classify("RightSideHair").type() == ChainType.HAIR,
                "Side hair was not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Bangs").type() == ChainType.HAIR,
                "Bangs were not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Left_ear").type() == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
        require(
                PhysicsBoneClassifier.classify("Right_ear").type() == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
    }

    private static void verifiesClassifierNormalisesNames() {
        // camelCase, separators, and casing must all fold to the same chain.
        require(
                PhysicsBoneClassifier.classify("sideHair").type() == ChainType.HAIR,
                "camelCase hair token was not recognised"
        );
        require(
                PhysicsBoneClassifier.classify("ponytail").type() == ChainType.HAIR,
                "Ponytail was not recognised as hair"
        );
        require(
                PhysicsBoneClassifier.classify("Left_Braid").type() == ChainType.HAIR,
                "Braid was not recognised as hair"
        );
        requireChain("Tail10", ChainType.TAIL, 9);
        require(
                PhysicsBoneClassifier.classify("tail").type() == ChainType.TAIL,
                "Lowercase tail was not recognised"
        );
        // A word that merely contains a chain token as a substring must not match.
        require(
                PhysicsBoneClassifier.classify("detail").type() == ChainType.NONE,
                "Substring 'tail' inside another word was misclassified"
        );
        require(
                PhysicsBoneClassifier.classify("Chair").type() == ChainType.NONE,
                "Substring 'hair' inside another word was misclassified"
        );
    }

    private static void verifiesNumberedSegmentsAndPinyin() {
        // Numbered chain segments must resolve no matter which word holds the
        // number — the earlier whole-word match dropped every segment past the
        // first, so a hair or tail chain only got its root driven.
        requireChain("LongHair2", ChainType.HAIR, 1);
        requireChain("LongRightHair2", ChainType.HAIR, 1);
        requireChain("RightSideHair2", ChainType.HAIR, 1);
        requireChain("Ear2", ChainType.EAR, 1);
        requireChain("Body_Tail3", ChainType.TAIL, 2);
        require(
                PhysicsBoneClassifier.classify("Tail999999999999999999999").type()
                        == ChainType.TAIL,
                "Oversized numeric suffix crashed or lost its semantic hint"
        );
        // Chinese pinyin names used by many model packs.
        require(
                PhysicsBoneClassifier.classify("shuangmawei").type() == ChainType.HAIR,
                "Pinyin twin-tail (shuangmawei) was not recognised as hair"
        );
        require(
                PhysicsBoneClassifier.classify("weiqu").type() == ChainType.TAIL,
                "Pinyin tail-skirt (weiqu) was not recognised as tail"
        );
        require(
                PhysicsBoneClassifier.classify("toufa").type() == ChainType.HAIR,
                "Pinyin hair (toufa) was not recognised"
        );
        // Expression bones that merely contain a pinyin substring must be safe:
        // weixiao (smile) must never be mistaken for wei (tail).
        require(
                PhysicsBoneClassifier.classify("weixiao").type() == ChainType.NONE,
                "Smile bone (weixiao) was misclassified as a tail"
        );
    }

    private static void verifiesExtendedSoftPartHints() {
        requireChain("Skirt2", ChainType.SKIRT, 1);
        requireChain("bow", ChainType.RIBBON, 0);
        requireChain("BackCape3", ChainType.CAPE, 2);
        requireChain("LeftWing4", ChainType.WING, 3);
        requireChain("weiqun", ChainType.SKIRT, 0);
        requireChain("hudiejie", ChainType.RIBBON, 0);
        requireChain("chibang2", ChainType.WING, 1);
        requireNone("MRibbon");
    }

    private static void verifiesMotionCoordinateFrame() {
        Vector3f facingSouth = MaidBonePhysics.worldAccelerationToModel(
                new Vec3(0.0D, 0.0D, 1.0D),
                0.0F
        );
        requireNear(facingSouth.x, 0.0F, "South-facing acceleration gained X");
        requireNear(facingSouth.z, -1.0F, "South-facing forward axis was reversed");
        require(
                new Vector3f(facingSouth).negate().z > 0.99F,
                "Inertial force did not lag behind forward acceleration"
        );

        Vector3f facingWest = MaidBonePhysics.worldAccelerationToModel(
                new Vec3(-1.0D, 0.0D, 0.0D),
                90.0F
        );
        requireNear(facingWest.z, -1.0F, "West-facing forward axis was reversed");

        Quaternionf parentDeflection = new Quaternionf().rotateZ(
                (float) Math.toRadians(10.0D)
        );
        Quaternionf childBase = MaidBonePhysics.composeOrientation(
                parentDeflection,
                0.0F,
                0.0F,
                0.0F
        );
        Vector3f childRest = childBase.transform(new Vector3f(0.0F, -1.0F, 0.0F));
        require(
                childRest.x > 0.17F,
                "Child rest frame did not inherit the current parent deflection"
        );
    }

    private static void verifiesTimeCorrectedDrag() {
        float atSixtyFps = MaidBonePhysics.dragRetention(0.35F, 1.0F / 60.0F);
        float atThirtyFps = MaidBonePhysics.dragRetention(0.35F, 1.0F / 30.0F);
        float atOneTwentyFps = MaidBonePhysics.dragRetention(0.35F, 1.0F / 120.0F);
        requireNear(
                atThirtyFps,
                atSixtyFps * atSixtyFps,
                "30 FPS drag does not match two 60 FPS steps"
        );
        requireNear(
                atOneTwentyFps * atOneTwentyFps,
                atSixtyFps,
                "120 FPS drag does not match one 60 FPS step"
        );
    }

    private static void verifiesAdaptiveMotionDenoising() {
        Vector3f tinyNoise = MotionNoiseGate.vector(
                new Vector3f(0.005F, 0.0F, 0.0F),
                0.5F,
                0.01F,
                0.04F
        );
        require(
                tinyNoise.lengthSquared() < 1.0E-8F,
                "Nonlinear dead zone did not remove tiny movement noise"
        );
        Vector3f saturatedSpike = MotionNoiseGate.vector(
                new Vector3f(5.0F, 0.0F, 0.0F),
                0.5F,
                0.01F,
                0.04F
        );
        require(
                saturatedSpike.x > 0.35F && saturatedSpike.x <= 0.5F,
                "Soft saturation did not bound a movement spike"
        );

        float dt = 1.0F / 60.0F;
        AdaptiveMotionFilter slowFilter = new AdaptiveMotionFilter();
        AdaptiveMotionFilter fastFilter = new AdaptiveMotionFilter();
        slowFilter.update(new Vector3f(), 0.0F, 0.5F, dt);
        fastFilter.update(new Vector3f(), 0.0F, 0.5F, dt);
        float slowResponse = slowFilter.update(
                new Vector3f(0.06F, 0.0F, 0.0F),
                0.0F,
                0.5F,
                dt
        ).acceleration().x / 0.06F;
        float fastResponse = fastFilter.update(
                new Vector3f(0.30F, 0.0F, 0.0F),
                0.0F,
                0.5F,
                dt
        ).acceleration().x / 0.30F;
        require(
                fastResponse > slowResponse,
                "Adaptive cutoff did not respond faster to intentional motion"
        );
        require(
                fastResponse < 1.0F,
                "Adaptive filter passed a full one-frame movement step"
        );

        MotionSignalSampler sampler = new MotionSignalSampler();
        sampler.update(Vec3.ZERO, 0, 0.0F, 0.0F, 0.5F, dt);
        float firstTickSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                1,
                0.0F,
                0.0F,
                0.5F,
                dt
        ).worldAcceleration().x;
        float heldRenderSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                1,
                0.0F,
                0.0F,
                0.5F,
                dt
        ).worldAcceleration().x;
        require(
                heldRenderSample > firstTickSample,
                "Tick acceleration was replaced by zero between render frames"
        );
        float releaseSample = sampler.update(
                new Vec3(0.02D, 0.0D, 0.0D),
                2,
                0.0F,
                0.0F,
                0.5F,
                dt
        ).worldAcceleration().x;
        require(
                releaseSample > 0.0F && releaseSample < heldRenderSample,
                "Tick acceleration release was not smoothed"
        );
    }

    private static void verifiesMetadataParsing() {
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version": 1,
                          "mode": "explicit",
                          "exclude": ["Head/Mask"],
                          "chains": [{
                            "id": "anonymous_hair",
                            "type": "HAIR",
                            "root": "Head/bone17",
                            "include_descendants": true,
                            "profile": {
                              "stiffness_scale": 0.75,
                              "angle_scale": 0.5
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "verification"
        );
        require(metadata.mode() == PhysicsMetadata.Mode.EXPLICIT, "Explicit mode was lost");
        require(metadata.excludes().contains("Head/Mask"), "Metadata exclude was lost");
        require(metadata.chains().size() == 1, "Metadata chain was not parsed");
        PhysicsMetadata.Chain chain = metadata.chains().get(0);
        require(
                chain.type() == PhysicsBoneSelectionPlan.PartType.HAIR,
                "Metadata part type was not parsed"
        );
        require(
                Math.abs(chain.profile().stiffnessScale() - 0.75F) < 1.0E-5F,
                "Metadata stiffness scale was not parsed"
        );
        require(
                Math.abs(chain.profile().angleScale() - 0.5F) < 1.0E-5F,
                "Metadata angle scale was not parsed"
        );
    }

    private static void verifiesEmptyAnchorFallback() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.empty_anchor_verification",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0]},
                      {"name":"TorsoCore","parent":"Body","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0]},
                      {"name":"HeadCore","parent":"Head","pivot":[0,20,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"HairContainer","parent":"Head","pivot":[0,20,0]},
                      {"name":"HairShell","parent":"HairContainer","pivot":[0,20,0],
                       "cubes":[{"origin":[-10,14,-10],"size":[20,12,20],"uv":[0,0]}]},
                      {"name":"TailContainer","parent":"Body","pivot":[0,6,2]},
                      {"name":"TailMesh","parent":"TailContainer","pivot":[0,6,2],
                       "cubes":[{"origin":[-1,0,2],"size":[2,6,16],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsBoneGeometry.Analysis geometry = PhysicsBoneGeometry.analyze(model);
        require(
                geometry.headBounds().size().x < 0.75F,
                "Empty Head anchor absorbed the hair subtree"
        );
        require(
                geometry.bodyBounds().size().z < 0.50F,
                "Empty Body anchor absorbed the tail subtree"
        );
    }

    private static void verifiesAnonymousExtendedGeometryDiscovery() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.physics_verification",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"bone90","parent":"Head","pivot":[-4,20,0],
                       "cubes":[{"origin":[-6,19,-1],"size":[2,3,1],"uv":[0,0]}]},
                      {"name":"bone91","parent":"Head","pivot":[4,20,0],
                       "cubes":[{"origin":[4,19,-1],"size":[2,3,1],"uv":[0,0]}]},

                      {"name":"bone1","parent":"Head","pivot":[0,20,0],
                       "cubes":[{"origin":[-4.5,15.5,-4.5],"size":[9,9,9],
                                 "inflate":-0.35,"uv":[0,0]}]},
                      {"name":"bone2","parent":"bone1","pivot":[-2,18,3],
                       "cubes":[{"origin":[-3,8,3],"size":[2,10,1],"uv":[0,0]}]},
                      {"name":"bone3","parent":"bone1","pivot":[2,18,3],
                       "cubes":[{"origin":[1,8,3],"size":[2,10,1],"uv":[0,0]}]},

                      {"name":"bone30","parent":"Body","pivot":[0,8,2],
                       "cubes":[{"origin":[-1,5,2],"size":[2,4,4],"uv":[0,0]}]},
                      {"name":"bone31","parent":"bone30","pivot":[0,6,6],
                       "cubes":[{"origin":[-0.75,4,6],"size":[1.5,2,4],"uv":[0,0]}]},
                      {"name":"bone32","parent":"bone31","pivot":[0,5,10],
                       "cubes":[{"origin":[-0.5,4,10],"size":[1,1,4],"uv":[0,0]}]},

                      {"name":"bone40","parent":"Body","pivot":[0,10,2],
                       "cubes":[{"origin":[-4,1,2],"size":[8,9,1],"uv":[0,0]}]},
                      {"name":"bone41","parent":"bone40","pivot":[0,3,3],
                       "cubes":[{"origin":[-3,0,3],"size":[6,3,1],"uv":[0,0]}]},

                      {"name":"bone50","parent":"Body","pivot":[3,13,0],
                       "cubes":[{"origin":[3,11,-1],"size":[8,1,3],"uv":[0,0]}]},
                      {"name":"bone51","parent":"bone50","pivot":[11,12,0],
                       "cubes":[{"origin":[11,11.5,-0.5],"size":[4,1,1],"uv":[0,0]}]},

                      {"name":"bone60","parent":"Body","pivot":[-3,14,0],
                       "cubes":[{"origin":[-4,5,-1],"size":[2,9,2],"uv":[0,0]}]},
                      {"name":"bone61","parent":"bone60","pivot":[-3,5,0],
                       "cubes":[{"origin":[-4,0,-1],"size":[2,5,2],"uv":[0,0]}]},

                      {"name":"RightHandLocator","parent":"Body","pivot":[-3,12,0]},
                      {"name":"bone100","parent":"RightHandLocator","pivot":[-3,12,0],
                       "cubes":[{"origin":[-12,11,-1],"size":[9,1,3],"uv":[0,0]}]},
                      {"name":"bone101","parent":"bone100","pivot":[-12,12,0],
                       "cubes":[{"origin":[-16,11.5,-0.5],"size":[4,1,1],"uv":[0,0]}]},

                      {"name":"bone70","parent":"Body","pivot":[0,14,2],
                       "cubes":[{"origin":[-3,8,2],"size":[6,6,1],"uv":[0,0]}]},
                      {"name":"bone71","parent":"bone70","pivot":[0,9,3],
                       "cubes":[{"origin":[-2.5,4,3],"size":[5,5,1],"uv":[0,0]}]},
                      {"name":"bone72","parent":"bone71","pivot":[0,5,4],
                       "cubes":[{"origin":[-2,1,4],"size":[4,4,1],"uv":[0,0]}]},

                      {"name":"bone80","parent":"Body","pivot":[1,10,2],
                       "cubes":[{"origin":[0.5,2,2],"size":[1,8,0.25],"uv":[0,0]}]},
                      {"name":"bone81","parent":"bone80","pivot":[1,3,2.25],
                       "cubes":[{"origin":[0.6,-3,2.25],"size":[0.8,6,0.2],"uv":[0,0]}]},
                      {"name":"bone82","parent":"bone81","pivot":[1,-2,2.45],
                       "cubes":[{"origin":[0.7,-6,2.45],"size":[0.6,4,0.15],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:anonymous",
                model,
                PhysicsMetadata.EMPTY
        );
        requireDriven(
                plan,
                model.bones().get("bone1"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Anonymous head shell was not discovered"
        );
        requireDriven(
                plan,
                model.bones().get("bone2"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Anonymous hair strand was not discovered"
        );
        requireDriven(
                plan,
                model.bones().get("bone90"),
                PhysicsBoneSelectionPlan.PartType.EAR,
                "Anonymous mirrored ear was not discovered"
        );
        requireDriven(
                plan,
                model.bones().get("bone30"),
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Anonymous rear chain was not discovered as a tail"
        );
        requireDriven(
                plan,
                model.bones().get("bone31"),
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Anonymous tail continuation lost its parent type"
        );
        require(
                plan.decision(model.bones().get("bone30")).chainId().equals(
                        plan.decision(model.bones().get("bone31")).chainId()
                ),
                "Serial tail segments did not share a chain id"
        );
        requireDriven(
                plan,
                model.bones().get("bone40"),
                PhysicsBoneSelectionPlan.PartType.SKIRT,
                "Anonymous lower cloth was not discovered as a skirt"
        );
        requireDriven(
                plan,
                model.bones().get("bone50"),
                PhysicsBoneSelectionPlan.PartType.WING,
                "Anonymous lateral chain was not discovered as a wing"
        );
        requireDriven(
                plan,
                model.bones().get("bone70"),
                PhysicsBoneSelectionPlan.PartType.CAPE,
                "Anonymous rear cloth was not discovered as a cape"
        );
        requireDriven(
                plan,
                model.bones().get("bone80"),
                PhysicsBoneSelectionPlan.PartType.RIBBON,
                "Anonymous narrow cloth was not discovered as a ribbon"
        );
        require(
                !plan.isDriven(model.bones().get("bone60")),
                "Anonymous rigid arm-like chain was selected"
        );
        require(
                !plan.isDriven(model.bones().get("bone100")),
                "Visible child below a hand locator was selected"
        );
    }

    private static void verifiesWinefoxGeometryDiscovery() throws Exception {
        Path modelPath = Path.of("winefox_blockbench", "winefox.json");
        require(Files.isRegularFile(modelPath), "Tracked winefox fixture is missing");
        RawGeoModel raw;
        try (InputStream input = Files.newInputStream(modelPath)) {
            raw = Converter.fromInputStream(input);
        }
        GeoModel geoModel = GeoBuilder.getGeoBuilder().constructGeoModel(
                RawGeometryTree.parseHierarchy(raw)
        );
        AnimatedGeoModel model = new AnimatedGeoModel(geoModel);
        PhysicsBoneSelectionPlan automatic = PhysicsBoneDiscoverer.discover(
                "geckolib:winefox",
                model,
                PhysicsMetadata.EMPTY
        );

        requireDriven(
                automatic,
                model.bones().get("BaseHair"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL,
                "Visible branching head shell was not discovered"
        );
        requireDriven(
                automatic,
                model.bones().get("bone5"),
                PhysicsBoneSelectionPlan.PartType.HAIR,
                "Anonymous hair geometry below the shell was not discovered"
        );
        requireDriven(
                automatic,
                model.bones().get("Tail7"),
                PhysicsBoneSelectionPlan.PartType.TAIL,
                "Tail tip was not selected"
        );
        require(
                !automatic.isDriven(model.bones().get("Head")),
                "Rigid head bone was selected"
        );
        require(
                !automatic.isDriven(model.bones().get("flower")),
                "Fixed flower attachment was selected"
        );
        verifiesWinefoxPivotCorrection(model);

        AnimatedGeoModel switchedModel = new AnimatedGeoModel(geoModel);
        PhysicsBonePlanCache.clear();
        PhysicsBoneSelectionPlan firstCached = PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                model
        );
        PhysicsBoneSelectionPlan switchedCached = PhysicsBonePlanCache.getOrCompute(
                "geckolib:winefox",
                switchedModel
        );
        require(firstCached != switchedCached, "Model switch reused a stale selection plan");
        require(
                !firstCached.isDriven(switchedModel.bones().get("BaseHair")),
                "A plan accepted a same-name bone from another model instance"
        );
        require(
                switchedCached.isDriven(switchedModel.bones().get("BaseHair")),
                "Switched model did not receive a fresh head-shell decision"
        );
        PhysicsBonePlanCache.clear();

        PhysicsMetadata explicit = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "mode": "explicit",
                          "exclude": ["flower"],
                          "chains": [{
                            "id": "head_shell_only",
                            "type": "HEAD_SHELL",
                            "root": "Head/Hair/BaseHair",
                            "include_descendants": true,
                            "profile": {"stiffness_scale": 0.5}
                          }]
                        }
                        """).getAsJsonObject(),
                "verification"
        );
        PhysicsBoneSelectionPlan explicitPlan = PhysicsBoneDiscoverer.discover(
                "geckolib:winefox",
                model,
                explicit
        );
        require(
                explicitPlan.isDriven(model.bones().get("BaseHair")),
                "Explicit metadata did not select its root"
        );
        require(
                Math.abs(explicitPlan.decision(model.bones().get("BaseHair"))
                        .profile().stiffnessScale()
                        - PhysicsBoneSelectionPlan.SpringProfile.defaults(
                        PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ).stiffnessScale() * 0.5F) < 1.0E-5F,
                "Metadata profile was not multiplied over the HEAD_SHELL preset"
        );
        require(
                explicitPlan.isDriven(model.bones().get("bone5")),
                "Explicit descendant binding did not include anonymous child"
        );
        require(
                !explicitPlan.isDriven(model.bones().get("flower")),
                "Explicit exclude did not override an included subtree"
        );
        require(
                !explicitPlan.isDriven(model.bones().get("Tail7")),
                "Explicit mode unexpectedly ran automatic discovery"
        );
    }

    private static void verifiesWinefoxPivotCorrection(AnimatedGeoModel model) {
        AnimatedGeoBone baseHair = model.bones().get("BaseHair");
        BoneKinematics.Metrics shell = BoneKinematics.measure(
                baseHair,
                model.bones().get("Hair"),
                PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
        );
        require(
                shell.compensatesPivot(),
                "Edge-pivoted winefox head shell did not receive a virtual pivot"
        );
        require(
                PhysicsBoneSelectionPlan.SpringProfile.defaults(
                        PhysicsBoneSelectionPlan.PartType.HEAD_SHELL
                ).gravityScale() <= 0.03F,
                "Head shell still receives enough gravity to fold on head pitch"
        );

        BoneKinematics.Metrics detached = BoneKinematics.measure(
                model.bones().get("bone5"),
                baseHair,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        require(
                detached.compensatesPivot(),
                "Remote winefox bone5 pivot was not corrected"
        );
        require(
                detached.effectivePivot().y
                        > detached.authoredPivot().y + 0.5F,
                "Remote winefox pivot was not moved toward its visible mesh"
        );
        require(
                detached.axis().y < -0.5F,
                "Remote hair pivot did not produce a downward hanging axis"
        );

        Quaternionf delta = new Quaternionf().rotateZ(0.2F);
        Vector3f offset = detached.compensationOffset(delta);
        Vector3f point = new Vector3f(detached.effectivePivot())
                .add(0.1F, -0.2F, 0.05F);
        Vector3f actual = delta.transform(
                new Vector3f(point).sub(detached.authoredPivot())
        ).add(detached.authoredPivot()).add(offset);
        Vector3f expected = delta.transform(
                new Vector3f(point).sub(detached.effectivePivot())
        ).add(detached.effectivePivot());
        require(
                actual.distance(expected) < 1.0E-5F,
                "Virtual-pivot translation did not preserve the intended rotation"
        );
    }

    private static void requireDriven(
            PhysicsBoneSelectionPlan plan,
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan.PartType type,
            String message
    ) {
        require(bone != null, message + " (bone missing)");
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        require(decision.driven() && decision.type() == type, message
                + ": " + decision.type() + " / " + decision.reason()
                + " / " + decision.confidence());
    }

    private static AnimatedGeoModel modelFromJson(String json) {
        RawGeoModel raw = Converter.fromJsonString(json);
        return new AnimatedGeoModel(
                GeoBuilder.getGeoBuilder().constructGeoModel(
                        RawGeometryTree.parseHierarchy(raw)
                )
        );
    }

    private static void requireChain(String name, ChainType type, int depth) {
        Classification classification = PhysicsBoneClassifier.classify(name);
        require(
                classification.type() == type && classification.depth() == depth,
                "Bone " + name + " classified as " + classification.type()
                        + " depth " + classification.depth()
        );
    }

    private static void requireNone(String name) {
        require(
                PhysicsBoneClassifier.classify(name).type() == ChainType.NONE,
                "Bone " + name + " should not receive physics"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireNear(float actual, float expected, String message) {
        require(
                Math.abs(actual - expected) < 1.0E-4F,
                message + ": expected " + expected + ", got " + actual
        );
    }
}
