package com.laixia.maidintelligence.feature.interaction.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ViewSpaceWorldPosition {
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
        return new Matrix4f()
                .rotate(Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F))
                .invert();
    }
}
