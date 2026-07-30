package com.laixia.maidintelligence.mixin;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * TLM 1.5.3 的 Sodium 写入器把 DOWN 法线打包为
 * {@code (-ny.z, -ny.y, -ny.z)}，X 分量因此在旋转 cube 上错误。
 *
 * <p>仅替换第五次 packUnsafe 调用（normalNY）的第一个参数，其余批量写入和
 * 剔除逻辑完全保留。
 */
@Mixin(
        targets = "com.github.tartaricacid.touhoulittlemaid.compat.sodium.SodiumGeoRenderer",
        remap = false
)
public abstract class SodiumDownNormalPackingMixin {
    @Shadow
    private static Vector3f ny;

    @ModifyArg(
            method = "renderCubesOfBone",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/compat/sodium/SodiumGeoRenderer;packUnsafe(FFF)I",
                    ordinal = 4
            ),
            index = 0,
            remap = false,
            require = 1
    )
    private static float maidIntelligence$fixDownNormalX(float ignoredX) {
        return -ny.x;
    }
}
