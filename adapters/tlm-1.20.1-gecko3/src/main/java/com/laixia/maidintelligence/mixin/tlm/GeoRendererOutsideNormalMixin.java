package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.laixia.maidintelligence.feature.shading.client.OutsideNormalWriter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 为 Gecko 模型接入逐面外法线写入器。
 *
 * <p>{@code renderCubesOfBone} 只存在于 {@code IGeoRenderer} 的接口默认实现，而
 * Mixin 不支持向接口注入。向 {@code GeoReplacedEntityRenderer} 合并同签名方法后，
 * 所有继承它的 Gecko 渲染器都会按正常虚方法分派进入这里。
 */
@Mixin(value = GeoReplacedEntityRenderer.class, remap = false)
public abstract class GeoRendererOutsideNormalMixin {
    public void renderCubesOfBone(
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
        OutsideNormalWriter.renderCubesOfBone(
                bone,
                poseStack,
                buffer,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha
        );
    }
}
