package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.shading.api.FaceNormalTemplate;
import com.laixia.maidintelligence.feature.shading.domain.CubeFaceTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 使用缓存的逐面外法线发射 Gecko cube。绕序反向时同步反转顶点提交顺序，
 * 保证光影的 {@code gl_FrontFacing} 与法线外侧一致。
 */
@OnlyIn(Dist.CLIENT)
public final class OutsideNormalWriter {
    private static final Vector3f[] CORNERS = new Vector3f[8];
    private static final Vector3f DX = new Vector3f();
    private static final Vector3f DY = new Vector3f();
    private static final Vector3f DZ = new Vector3f();
    private static final Vector3f FACE_NORMAL = new Vector3f();

    static {
        for (int index = 0; index < CORNERS.length; index++) {
            CORNERS[index] = new Vector3f();
        }
    }

    private OutsideNormalWriter() {
    }

    /**
     * Sodium 仅在真正需要修正绕序时回退；普通 cube 保留批量快速路径。
     */
    public static boolean requiresCustomWriter(AnimatedGeoBone bone) {
        if (!ShadowPassDetector.isActive() && !isWorldView()) {
            return false;
        }
        return GeckoMeshNormalTemplates.getOrBuild(
                bone.geoBone().cubes()
        ).hasWindingCorrections();
    }

    public static void renderCubesOfBone(
            AnimatedGeoBone bone,
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        if (bone.isHidden() || bone.cubesAreHidden()) {
            return;
        }
        GeoMesh mesh = bone.geoBone().cubes();
        FaceNormalTemplate template =
                GeckoMeshNormalTemplates.getOrBuild(mesh);
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normalMatrix = last.normal();

        for (int cube = 0; cube < mesh.getCubeCount(); cube++) {
            prepareCorners(mesh, cube, pose);
            int faces = mesh.faces(cube);
            for (int face = 0; face < CubeFaceTopology.FACE_COUNT; face++) {
                if ((faces & (1 << face)) == 0) {
                    continue;
                }
                FACE_NORMAL.set(
                        template.normalX(cube, face),
                        template.normalY(cube, face),
                        template.normalZ(cube, face)
                );
                FACE_NORMAL.mul(normalMatrix);
                if (FACE_NORMAL.lengthSquared() > 1.0E-12F) {
                    FACE_NORMAL.normalize();
                }
                emitFace(
                        buffer,
                        mesh,
                        cube,
                        face,
                        template.shouldReverseWinding(cube, face),
                        packedLight,
                        packedOverlay,
                        red,
                        green,
                        blue,
                        alpha
                );
            }
        }
    }

    private static boolean isWorldView() {
        return RenderSystem.getModelViewMatrix().m32() == 0.0F;
    }

    private static void prepareCorners(
            GeoMesh mesh,
            int cube,
            Matrix4f pose
    ) {
        mesh.position(cube).mulPosition(pose, CORNERS[0]);
        mesh.dx(cube).mulDirection(pose, DX);
        mesh.dy(cube).mulDirection(pose, DY);
        mesh.dz(cube).mulDirection(pose, DZ);

        CORNERS[0].add(DX, CORNERS[1]);
        CORNERS[1].add(DY, CORNERS[2]);
        CORNERS[0].add(DY, CORNERS[3]);
        CORNERS[0].add(DZ, CORNERS[4]);
        CORNERS[1].add(DZ, CORNERS[5]);
        CORNERS[2].add(DZ, CORNERS[6]);
        CORNERS[3].add(DZ, CORNERS[7]);
    }

    private static void emitFace(
            VertexConsumer buffer,
            GeoMesh mesh,
            int cube,
            int face,
            boolean reverseWinding,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        float u0 = mesh.u0(cube, face);
        float v0 = mesh.v0(cube, face);
        float u1 = mesh.u1(cube, face);
        float v1 = mesh.v1(cube, face);
        if (reverseWinding) {
            emitVertex(
                    buffer, face, 0, u0, v1,
                    packedLight, packedOverlay, red, green, blue, alpha
            );
            emitVertex(
                    buffer, face, 3, u0, v0,
                    packedLight, packedOverlay, red, green, blue, alpha
            );
            emitVertex(
                    buffer, face, 2, u1, v0,
                    packedLight, packedOverlay, red, green, blue, alpha
            );
            emitVertex(
                    buffer, face, 1, u1, v1,
                    packedLight, packedOverlay, red, green, blue, alpha
            );
            return;
        }
        emitVertex(
                buffer, face, 0, u0, v1,
                packedLight, packedOverlay, red, green, blue, alpha
        );
        emitVertex(
                buffer, face, 1, u1, v1,
                packedLight, packedOverlay, red, green, blue, alpha
        );
        emitVertex(
                buffer, face, 2, u1, v0,
                packedLight, packedOverlay, red, green, blue, alpha
        );
        emitVertex(
                buffer, face, 3, u0, v0,
                packedLight, packedOverlay, red, green, blue, alpha
        );
    }

    private static void emitVertex(
            VertexConsumer buffer,
            int face,
            int vertex,
            float u,
            float v,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        Vector3f corner = CORNERS[CubeFaceTopology.corner(face, vertex)];
        buffer.vertex(
                corner.x, corner.y, corner.z,
                red, green, blue, alpha,
                u, v,
                packedOverlay, packedLight,
                FACE_NORMAL.x, FACE_NORMAL.y, FACE_NORMAL.z
        );
    }
}
