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
        LOGGER.info("=== Maid skeleton dump: {} (id={}) ===", title, maid.getId());
        chat(player, Component.literal("=== 骨架: " + title + " ===")
                .withStyle(ChatFormatting.AQUA));

        int[] counts = new int[2];
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            dumpBone(player, bone, "root", 0, counts);
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
            String parentName,
            int depth,
            int[] counts
    ) {
        counts[0]++;
        boolean driven = MaidBonePhysics.isDrivenBone(bone);
        if (driven) {
            counts[1]++;
        }
        int cubes = bone.geoBone().cubes().getCubeCount();
        String detail = String.format(
                Locale.ROOT,
                "%s%s%s  parent=%s pos=(%.2f,%.2f,%.2f) pivot=(%.2f,%.2f,%.2f)"
                        + " rot=(%.1f,%.1f,%.1f)deg scale=(%.2f,%.2f,%.2f) cubes=%d children=%d%s",
                "  ".repeat(depth),
                driven ? "[P] " : "",
                bone.getName(),
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
                driven ? " <DRIVEN>" : ""
        );
        LOGGER.info(detail);

        if (driven) {
            chat(player, Component.literal(String.format(
                    Locale.ROOT,
                    "[P] %s  parent=%s pivot=(%.2f,%.2f,%.2f) rot=(%.0f,%.0f,%.0f) cubes=%d",
                    bone.getName(),
                    parentName,
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
            dumpBone(player, child, bone.getName(), depth + 1, counts);
        }
    }

    private static void chat(Player player, Component message) {
        player.sendSystemMessage(message);
    }
}
