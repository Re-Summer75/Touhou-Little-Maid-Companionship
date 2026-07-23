package com.laixia.maidintelligence.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.laixia.maidintelligence.feature.physics.client.ClientPhysicsSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * TLM can load downloaded custom packs without a Minecraft resource reload.
 * Refresh the matching physics sidecars after either direct pack load path.
 */
@Mixin(value = CustomPackLoader.class, remap = false)
public abstract class CustomPackPhysicsMetadataMixin {
    @Inject(
            method = {"readModelFromZipFile", "readModelFromFolder"},
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void maidIntelligence$refreshPhysicsMetadata(
            File file,
            CallbackInfo callback
    ) {
        ClientPhysicsSetup.refreshAfterCustomPackLoad();
    }
}
