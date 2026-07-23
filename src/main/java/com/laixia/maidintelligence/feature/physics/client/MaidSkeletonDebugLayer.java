package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the live Gecko skeleton on every maid while the player holds the
 * {@link PhysicsDebugStick}: a small cross at each bone's origin (its pivot),
 * and for the bones the physics layer actually drives, the bone's local XYZ
 * axes plus a cyan wireframe of its cubes. Seeing the real pivots and driven
 * set turns "the hair swings wrong" into something measurable.
 */
@OnlyIn(Dist.CLIENT)
public final class MaidSkeletonDebugLayer<T extends Mob, R extends IGeoEntityRenderer<T>>
        extends GeoLayerRenderer<T, R> {
    private static final float ORIGIN_SIZE = 0.03F;
    private static final float DRIVEN_ORIGIN_SIZE = 0.06F;
    private static final float AXIS_LENGTH = 0.12F;

    private static final int[] IDLE_ORIGIN = {110, 110, 110};
    private static final int[] AUTO_ORIGIN = {255, 235, 60};
    private static final int[] METADATA_ORIGIN = {220, 90, 255};
    private static final int[] UNCERTAIN_ORIGIN = {255, 80, 80};
    private static final int[] AXIS_X = {255, 60, 60};
    private static final int[] AXIS_Y = {60, 255, 60};
    private static final int[] AXIS_Z = {70, 130, 255};
    private static final int[] PHYSICS_AXIS = {255, 145, 30};
    private static final int[] MESH = {40, 230, 230};

    public MaidSkeletonDebugLayer(R entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R entityRenderer) {
        return new MaidSkeletonDebugLayer<>(entityRenderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (!PhysicsDebugStick.isHeldBy(player)) {
            return;
        }
        ILocationModel modelObject = getGeoEntity(entity).getGeoModel();
        if (!(modelObject instanceof AnimatedGeoModel model)) {
            return;
        }
        PhysicsBoneSelectionPlan plan = MaidBonePhysics.lastPlan(maid);
        if (plan == null) {
            plan = PhysicsBonePlanCache.getOrCompute(maid.getModelId(), model);
        }
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (AnimatedGeoBone bone : model.topLevelBones()) {
            renderBone(poseStack, lines, bone, plan);
        }
    }

    private void renderBone(
            PoseStack poseStack,
            VertexConsumer lines,
            AnimatedGeoBone bone,
            PhysicsBoneSelectionPlan plan
    ) {
        poseStack.pushPose();
        RenderUtils.prepMatrixForBone(poseStack, bone);
        Matrix4f pose = poseStack.last().pose();

        PhysicsBoneSelectionPlan.Decision decision = plan.decision(bone);
        boolean driven = MaidBonePhysics.isDriven(bone, plan);
        var metrics = driven ? plan.kinematics(bone) : null;
        float size = driven ? DRIVEN_ORIGIN_SIZE : ORIGIN_SIZE;
        int[] originColor = !driven
                ? IDLE_ORIGIN
                : metrics != null && metrics.supportConfidence() < 0.20F
                ? UNCERTAIN_ORIGIN
                : decision.source() == PhysicsBoneSelectionPlan.Source.METADATA
                ? METADATA_ORIGIN
                : AUTO_ORIGIN;
        float authoredX = bone.getPivotX() / 16.0F;
        float authoredY = bone.getPivotY() / 16.0F;
        float authoredZ = bone.getPivotZ() / 16.0F;
        if (!driven) {
            cross(
                    lines,
                    pose,
                    authoredX,
                    authoredY,
                    authoredZ,
                    size,
                    originColor
            );
        }

        if (driven) {
            Vector3f pivot = metrics == null
                    ? new Vector3f(authoredX, authoredY, authoredZ)
                    : metrics.effectivePivot();
            if (metrics != null && metrics.compensatesPivot()) {
                cross(
                        lines,
                        pose,
                        authoredX,
                        authoredY,
                        authoredZ,
                        ORIGIN_SIZE,
                        IDLE_ORIGIN
                );
            }
            cross(
                    lines,
                    pose,
                    pivot.x,
                    pivot.y,
                    pivot.z,
                    size,
                    originColor
            );
            line(lines, pose, pivot.x, pivot.y, pivot.z,
                    pivot.x + AXIS_LENGTH, pivot.y, pivot.z, AXIS_X);
            line(lines, pose, pivot.x, pivot.y, pivot.z,
                    pivot.x, pivot.y + AXIS_LENGTH, pivot.z, AXIS_Y);
            line(lines, pose, pivot.x, pivot.y, pivot.z,
                    pivot.x, pivot.y, pivot.z + AXIS_LENGTH, AXIS_Z);
            if (metrics != null) {
                Vector3f axis = metrics.axis();
                line(lines, pose, pivot.x, pivot.y, pivot.z,
                        pivot.x + axis.x * AXIS_LENGTH,
                        pivot.y + axis.y * AXIS_LENGTH,
                        pivot.z + axis.z * AXIS_LENGTH,
                        PHYSICS_AXIS);
            }
            renderMesh(lines, pose, bone);
        }

        for (AnimatedGeoBone child : bone.children()) {
            renderBone(poseStack, lines, child, plan);
        }
        poseStack.popPose();
    }

    private void cross(
            VertexConsumer lines,
            Matrix4f pose,
            float x,
            float y,
            float z,
            float size,
            int[] color
    ) {
        line(lines, pose, x - size, y, z, x + size, y, z, color);
        line(lines, pose, x, y - size, z, x, y + size, z, color);
        line(lines, pose, x, y, z - size, x, y, z + size, color);
    }

    private void renderMesh(VertexConsumer lines, Matrix4f pose, AnimatedGeoBone bone) {
        GeoMesh mesh = bone.geoBone().cubes();
        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            Vector3f origin = mesh.position(cube);
            Vector3f dx = mesh.dx(cube);
            Vector3f dy = mesh.dy(cube);
            Vector3f dz = mesh.dz(cube);
            Vector3f[] corners = new Vector3f[8];
            for (int mask = 0; mask < 8; mask++) {
                Vector3f corner = new Vector3f(origin);
                if ((mask & 1) != 0) {
                    corner.add(dx);
                }
                if ((mask & 2) != 0) {
                    corner.add(dy);
                }
                if ((mask & 4) != 0) {
                    corner.add(dz);
                }
                corners[mask] = corner;
            }
            // 12 cube edges, grouped by the axis they run along.
            edge(lines, pose, corners, 0, 1);
            edge(lines, pose, corners, 2, 3);
            edge(lines, pose, corners, 4, 5);
            edge(lines, pose, corners, 6, 7);
            edge(lines, pose, corners, 0, 2);
            edge(lines, pose, corners, 1, 3);
            edge(lines, pose, corners, 4, 6);
            edge(lines, pose, corners, 5, 7);
            edge(lines, pose, corners, 0, 4);
            edge(lines, pose, corners, 1, 5);
            edge(lines, pose, corners, 2, 6);
            edge(lines, pose, corners, 3, 7);
        }
    }

    private void edge(
            VertexConsumer lines,
            Matrix4f pose,
            Vector3f[] corners,
            int first,
            int second
    ) {
        Vector3f a = corners[first];
        Vector3f b = corners[second];
        line(lines, pose, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), MESH);
    }

    private void line(
            VertexConsumer lines,
            Matrix4f pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            int[] color
    ) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-5F) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
        }
        lines.vertex(pose, x1, y1, z1)
                .color(color[0], color[1], color[2], 255)
                .normal(nx, ny, nz)
                .endVertex();
        lines.vertex(pose, x2, y2, z2)
                .color(color[0], color[1], color[2], 255)
                .normal(nx, ny, nz)
                .endVertex();
    }
}
