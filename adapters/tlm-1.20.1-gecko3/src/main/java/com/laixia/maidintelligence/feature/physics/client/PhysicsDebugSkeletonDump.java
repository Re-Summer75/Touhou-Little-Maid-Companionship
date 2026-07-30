package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
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
            dumpBone(player, bone, null, null, 0, counts, plan);
        }
        PhysicsCollisionDebugDump.Summary collisions =
                PhysicsCollisionDebugDump.dump(maid);

        LOGGER.info(
                "=== End dump: {} bones, {} driven, {} proxies, "
                        + "{} penetrating ===",
                counts[0],
                counts[1],
                collisions.proxyCount(),
                collisions.penetratingCount()
        );
        chat(player, Component.literal(String.format(
                Locale.ROOT,
                "共 %d 骨骼，%d 受物理驱动；碰撞代理 %d，穿透 %d。"
                        + "完整树见 run/logs/latest.log",
                counts[0],
                counts[1],
                collisions.proxyCount(),
                collisions.penetratingCount()
        )).withStyle(ChatFormatting.GRAY));
    }

    private static void dumpBone(
            Player player,
            AnimatedGeoBone bone,
            AnimatedGeoBone parent,
            AnimatedGeoBone nearestSolidAncestor,
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
        String kinematics = driven
                ? PhysicsKinematicsDebugText.describe(
                bone,
                parent,
                nearestSolidAncestor,
                decision,
                plan
        )
                : "";
        int cubes = bone.geoBone().cubes().getCubeCount();
        String detail = String.format(
                Locale.ROOT,
                "%s%s%s path=%s parent=%s pos=(%.2f,%.2f,%.2f)"
                        + " pivot=(%.2f,%.2f,%.2f)"
                        + " rot=(%.1f,%.1f,%.1f)deg scale=(%.2f,%.2f,%.2f)"
                        + " cubes=%d children=%d decision=%s/%s confidence=%.3f"
                        + " chain=%s%s reason=\"%s\"%s%s",
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
                PhysicsDecisionDebugText.describe(decision),
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

        AnimatedGeoBone nextSolidAncestor =
                bone.geoBone().cubes().getCubeCount() > 0
                        ? bone
                        : nearestSolidAncestor;
        for (AnimatedGeoBone child : bone.children()) {
            dumpBone(
                    player,
                    child,
                    bone,
                    nextSolidAncestor,
                    depth + 1,
                    counts,
                    plan
            );
        }
    }

    private static void chat(Player player, Component message) {
        player.sendSystemMessage(message);
    }
}
