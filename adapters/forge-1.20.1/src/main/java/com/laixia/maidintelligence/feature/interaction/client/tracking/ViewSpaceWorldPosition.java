package com.laixia.maidintelligence.feature.interaction.client.tracking;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ViewSpaceWorldPosition {
    // Render-thread-only cache: every tracked maid in a frame shares the same
    // camera rotation, so the inverse view matrix is built once per rotation
    // change instead of twice per maid.
    private static final Matrix4f CACHED_INVERSE_VIEW = new Matrix4f();
    private static float cachedXRot = Float.NaN;
    private static float cachedYRot = Float.NaN;

    private ViewSpaceWorldPosition() {
    }

    static Vec3 fromRenderPosition(Vec3 renderPosition) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f inverseView = inverseViewMatrix();
        Vector3f worldOffset = new Vector3f(
                (float) renderPosition.x,
                (float) renderPosition.y,
                (float) renderPosition.z
        ).mulPosition(inverseView);
        return camera.getPosition().add(
                worldOffset.x(),
                worldOffset.y(),
                worldOffset.z()
        );
    }

    static Vec3 fromRenderDirection(Vec3 renderDirection) {
        Vector3f worldDirection = new Vector3f(
                (float) renderDirection.x,
                (float) renderDirection.y,
                (float) renderDirection.z
        ).mulDirection(inverseViewMatrix()).normalize();
        return new Vec3(
                worldDirection.x(),
                worldDirection.y(),
                worldDirection.z()
        );
    }

    private static Matrix4f inverseViewMatrix() {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float xRot = camera.getXRot();
        float yRot = camera.getYRot();
        if (xRot != cachedXRot || yRot != cachedYRot) {
            CACHED_INVERSE_VIEW.identity()
                    .rotate(Axis.XP.rotationDegrees(xRot))
                    .rotate(Axis.YP.rotationDegrees(yRot + 180.0F))
                    .invert();
            cachedXRot = xRot;
            cachedYRot = yRot;
        }
        return CACHED_INVERSE_VIEW;
    }
}
