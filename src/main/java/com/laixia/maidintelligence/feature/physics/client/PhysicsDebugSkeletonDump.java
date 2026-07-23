package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * Dumps a maid's live Gecko skeleton when the debug stick right-clicks it. The
 * full indented tree (every bone's name, position, pivot, rotation, scale, cube
 * count, and parent) goes to the log; the chat gets the physics-driven bones
 * plus a summary, so the render loop's actual bone data can be inspected
 * without guessing from screenshots.
 */
@OnlyIn(Dist.CLIENT)
final class PhysicsDebugSkeletonDump {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PhysicsDebugSkeletonDump() {
    }

    static void dump(Player player, EntityMaid maid, AnimatedGeoModel model) {
        String title = maid.getName().getString();
        PhysicsBoneSelectionPlan plan = MaidBonePhysics.lastPlan(maid);
        if (plan == null) {
            plan = PhysicsBonePlanCache.getOrCompute(maid.getModelId(), model);
        }
        LOGGER.info(
                "=== Maid skeleton dump: {} (entity={}, model={}) ===",
                title,
                maid.getId(),
                plan.modelId()
        );
        chat(player, Component.literal(
                "=== 骨架: " + title + "  模型: " + plan.modelId() + " ==="
        ).withStyle(ChatFormatting.AQUA));

        int[] counts = new int[2];
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            dumpBone(player, bone, null, 0, counts, plan);
        }

        LOGGER.info("=== End dump: {} bones, {} driven ===", counts[0], counts[1]);
        chat(player, Component.literal(String.format(
                Locale.ROOT,
                "共 %d 骨骼，%d 受物理驱动。完整树见 run/logs/latest.log",
                counts[0],
                counts[1]
        )).withStyle(ChatFormatting.GRAY));
    }

    private static void dumpBone(
            Player player,
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            int depth,
            int[] counts,
            PhysicsBoneSelectionPlan plan
    ) {
        counts[0]++;
        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        boolean driven = MaidBonePhysics.isDriven(bone, plan);
        if (driven) {
            counts[1]++;
        }
        String parentName = parent == null ? "root" : parent.getName();
        String kinematics = "";
        if (driven) {
            BoneKinematics.Metrics metrics = plan.kinematics(bone);
            if (metrics == null) {
                metrics = BoneKinematics.measure(
                        bone,
                        parent,
                        decision.type()
                );
            }
            kinematics = String.format(
                    Locale.ROOT,
                    " effectivePivot=(%.2f,%.2f,%.2f)"
                            + " physicsAxis=(%.3f,%.3f,%.3f)"
                            + " pivotCorrected=%s supportConfidence=%.3f"
                            + " safeAngle=%.1fdeg",
                    metrics.effectivePivot().x * 16.0F,
                    metrics.effectivePivot().y * 16.0F,
                    metrics.effectivePivot().z * 16.0F,
                    metrics.axis().x,
                    metrics.axis().y,
                    metrics.axis().z,
                    metrics.compensatesPivot(),
                    metrics.supportConfidence(),
                    Math.toDegrees(metrics.safeAngle()
                            * decision.profile().angleScale())
            );
        }
        int cubes = bone.geoBone().cubes().getCubeCount();
        String detail = String.format(
                Locale.ROOT,
                "%s%s%s path=%s parent=%s pos=(%.2f,%.2f,%.2f)"
                        + " pivot=(%.2f,%.2f,%.2f)"
                        + " rot=(%.1f,%.1f,%.1f)deg scale=(%.2f,%.2f,%.2f)"
                        + " cubes=%d children=%d decision=%s/%s confidence=%.3f"
                        + " chain=%s reason=\"%s\"%s%s",
                "  ".repeat(depth),
                driven ? "[P] " : "",
                bone.getName(),
                plan.path(bone),
                parentName,
                bone.getPositionX(),
                bone.getPositionY(),
                bone.getPositionZ(),
                bone.getPivotX(),
                bone.getPivotY(),
                bone.getPivotZ(),
                Math.toDegrees(bone.getRotationX()),
                Math.toDegrees(bone.getRotationY()),
                Math.toDegrees(bone.getRotationZ()),
                bone.getScaleX(),
                bone.getScaleY(),
                bone.getScaleZ(),
                cubes,
                bone.children().size(),
                decision.source().label(),
                decision.type(),
                decision.confidence(),
                decision.chainId(),
                decision.reason(),
                kinematics,
                driven ? " <DRIVEN>" : ""
        );
        LOGGER.info(detail);

        if (driven) {
            chat(player, Component.literal(String.format(
                    Locale.ROOT,
                    "[P:%s/%s] %s parent=%s conf=%.2f pivot=(%.2f,%.2f,%.2f)"
                            + " rot=(%.0f,%.0f,%.0f) cubes=%d",
                    decision.source().label(),
                    decision.type(),
                    bone.getName(),
                    parentName,
                    decision.confidence(),
                    bone.getPivotX(),
                    bone.getPivotY(),
                    bone.getPivotZ(),
                    Math.toDegrees(bone.getRotationX()),
                    Math.toDegrees(bone.getRotationY()),
                    Math.toDegrees(bone.getRotationZ()),
                    cubes
            )).withStyle(ChatFormatting.YELLOW));
        }

        for (AnimatedGeoBone child : bone.children()) {
            dumpBone(player, child, bone, depth + 1, counts, plan);
        }
    }

    private static void chat(Player player, Component message) {
        player.sendSystemMessage(message);
    }
}
