package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class BoneAttachmentFrameVerification {
    private BoneAttachmentFrameVerification() {
    }

    static void run() {
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{"identifier":"geometry.attachment_frame",
                      "texture_width":64,"texture_height":64},
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Head","parent":"Root","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"Upward","parent":"Head","pivot":[0,30,0],
                       "cubes":[{"origin":[-0.5,24,-0.5],"size":[1,6,1],"uv":[0,0]}]},
                      {"name":"CenteredUpward","parent":"Head","pivot":[0,27,0],
                       "cubes":[{"origin":[-0.5,24,-0.5],"size":[1,6,1],"uv":[0,0]}]},
                      {"name":"UpwardAnchor","parent":"Head","pivot":[0,30,0]},
                      {"name":"SupportedUpward","parent":"UpwardAnchor","pivot":[0,30,0],
                       "cubes":[{"origin":[-0.5,24,-0.5],"size":[1,6,1],"uv":[0,0]}]},
                      {"name":"Side","parent":"Head","pivot":[30,20,0],
                       "cubes":[{"origin":[4,19.5,-0.5],"size":[26,1,1],"uv":[0,0]}]},
                      {"name":"CorrectDown","parent":"Head","pivot":[0,24,0],
                       "cubes":[{"origin":[-0.5,12,-0.5],"size":[1,12,1],"uv":[0,0]}]},
                      {"name":"DetachedSide","parent":"Head","pivot":[0,0,0],
                       "cubes":[{"origin":[4,19.5,-0.5],"size":[8,1,1],"uv":[0,0]}]},
                      {"name":"RotatedDown","parent":"Head","pivot":[0,24,8],
                       "rotation":[-90,0,0],
                       "cubes":[{"origin":[-0.5,23.5,0],"size":[1,1,8],"uv":[0,0]}]},
                      {"name":"BangAnchor","parent":"Head","pivot":[0,24,0]},
                      {"name":"AnchoredBang","parent":"BangAnchor","pivot":[0,18,0],
                       "cubes":[{"origin":[-1,18,-1],"size":[2,6,2],"uv":[0,0]}]},
                      {"name":"AmbiguousAnchor","parent":"Head","pivot":[0,20,0]},
                      {"name":"AmbiguousRibbon","parent":"AmbiguousAnchor","pivot":[4,20,0],
                       "cubes":[{"origin":[-4,19.5,-0.5],"size":[8,1,1],"uv":[0,0]}]},
                      {"name":"WrongAnchor","parent":"Head","pivot":[0,50,0]},
                      {"name":"ThroughWrongAnchor","parent":"WrongAnchor",
                       "pivot":[0,30,0],
                       "cubes":[{"origin":[-0.5,24,-0.5],"size":[1,6,1],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
        AnimatedGeoBone head = model.bones().get("Head");
        requireAxis(model, head, "Upward", 1, 0.80F);
        requireAxis(model, head, "CenteredUpward", 1, 0.80F);
        requireAxis(
                model,
                model.bones().get("UpwardAnchor"),
                head,
                "SupportedUpward",
                1,
                0.80F
        );
        requireAxis(model, head, "Side", 0, -0.80F);
        requireAxis(model, head, "CorrectDown", 1, -0.80F);
        requireAxis(model, head, "DetachedSide", 0, -0.80F);
        requireRestDirection(model, head, "RotatedDown");
        requireAnchoredBang(model, head);
        requireAmbiguousSupport(model, head);
        requireAxis(
                model,
                model.bones().get("WrongAnchor"),
                head,
                "ThroughWrongAnchor",
                1,
                0.80F
        );
    }

    private static void requireAmbiguousSupport(
            AnimatedGeoModel model,
            AnimatedGeoBone head
    ) {
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                model.bones().get("AmbiguousRibbon"),
                model.bones().get("AmbiguousAnchor"),
                head,
                PhysicsBoneSelectionPlan.PartType.RIBBON
        );
        require(
                !metrics.compensatesPivot()
                        && metrics.supportConfidence() < 0.20F
                        && metrics.safeAngle() <= 0.18F,
                "Ambiguous support unexpectedly relocated the pivot: "
                        + metrics.effectivePivot()
                        + " contact=" + metrics.contactConfidence()
                        + " support=" + metrics.supportConfidence()
                        + " score=" + metrics.pivotScore()
        );
    }

    private static void requireAnchoredBang(
            AnimatedGeoModel model,
            AnimatedGeoBone head
    ) {
        AnimatedGeoBone bone = model.bones().get("AnchoredBang");
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                bone,
                model.bones().get("BangAnchor"),
                head,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        require(
                metrics.compensatesPivot()
                        && metrics.effectivePivot().y > 1.45F
                        && metrics.axis().y < -0.80F,
                "Valid empty bang anchor did not win over head center: "
                        + metrics.effectivePivot() + " / " + metrics.axis()
        );
    }

    private static void requireAxis(
            AnimatedGeoModel model,
            AnimatedGeoBone parent,
            String name,
            int component,
            float threshold
    ) {
        requireAxis(model, parent, parent, name, component, threshold);
    }

    private static void requireAxis(
            AnimatedGeoModel model,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
            String name,
            int component,
            float threshold
    ) {
        AnimatedGeoBone bone = model.bones().get(name);
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                bone,
                parent,
                nearestSolidAncestor,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        float actual = switch (component) {
            case 0 -> metrics.axis().x;
            case 1 -> metrics.axis().y;
            default -> metrics.axis().z;
        };
        require(
                threshold > 0.0F ? actual > threshold : actual < threshold,
                name + " attachment axis was reversed: " + metrics.axis()
        );
        if (!name.equals("CorrectDown")) {
            require(
                    metrics.compensatesPivot(),
                    name + " distal or detached pivot was not corrected"
            );
        }
    }

    private static void requireRestDirection(
            AnimatedGeoModel model,
            AnimatedGeoBone parent,
            String name
    ) {
        AnimatedGeoBone bone = model.bones().get(name);
        BoneKinematics.Metrics metrics = BoneKinematics.measure(
                bone,
                parent,
                parent,
                PhysicsBoneSelectionPlan.PartType.HAIR
        );
        var initial = bone.getInitialSnapshot();
        var restDirection = new org.joml.Quaternionf().rotateZYX(
                initial.rotationValueZ,
                initial.rotationValueY,
                initial.rotationValueX
        ).transform(metrics.axis());
        require(
                metrics.compensatesPivot() && restDirection.y < -0.80F,
                name + " rest rotation produced a reversed axis: "
                        + restDirection
        );
    }
}
