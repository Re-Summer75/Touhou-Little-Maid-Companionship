package com.laixia.maidintelligence.feature.physics.geometry;

import java.util.Objects;

/**
 * Mutable per-frame pose and visibility kept apart from immutable bone data.
 */
abstract class BoneRuntimePose {
    private final BoneModelSnapshot.RestPose restPose;
    private final boolean geometryRenderable;

    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float positionX;
    private float positionY;
    private float positionZ;
    private float scaleX;
    private float scaleY;
    private float scaleZ;
    private boolean hierarchyVisible = true;
    private boolean geometryVisible;

    BoneRuntimePose(
            BoneModelSnapshot.RestPose restPose,
            boolean geometryRenderable
    ) {
        this.restPose = Objects.requireNonNull(restPose, "restPose");
        this.geometryRenderable = geometryRenderable;
        geometryVisible = geometryRenderable;
        resetToRestPose();
    }

    public final float getRotationX() {
        return rotationX;
    }

    public final void setRotationX(float value) {
        rotationX = value;
    }

    public final float getRotationY() {
        return rotationY;
    }

    public final void setRotationY(float value) {
        rotationY = value;
    }

    public final float getRotationZ() {
        return rotationZ;
    }

    public final void setRotationZ(float value) {
        rotationZ = value;
    }

    public final float getPositionX() {
        return positionX;
    }

    public final void setPositionX(float value) {
        positionX = value;
    }

    public final float getPositionY() {
        return positionY;
    }

    public final void setPositionY(float value) {
        positionY = value;
    }

    public final float getPositionZ() {
        return positionZ;
    }

    public final void setPositionZ(float value) {
        positionZ = value;
    }

    public final float getScaleX() {
        return scaleX;
    }

    public final void setScaleX(float value) {
        scaleX = value;
    }

    public final float getScaleY() {
        return scaleY;
    }

    public final void setScaleY(float value) {
        scaleY = value;
    }

    public final float getScaleZ() {
        return scaleZ;
    }

    public final void setScaleZ(float value) {
        scaleZ = value;
    }

    public final boolean isHierarchyVisible() {
        return hierarchyVisible;
    }

    public final boolean isGeometryVisible() {
        return geometryVisible;
    }

    /**
     * Receives effective parent visibility and this bone's cube visibility.
     */
    public final void setRenderVisibility(
            boolean effectiveHierarchyVisible,
            boolean cubesVisible
    ) {
        hierarchyVisible = effectiveHierarchyVisible;
        geometryVisible = effectiveHierarchyVisible
                && cubesVisible
                && geometryRenderable;
    }

    public final void resetToRestPose() {
        rotationX = restPose.rotationValueX;
        rotationY = restPose.rotationValueY;
        rotationZ = restPose.rotationValueZ;
        positionX = restPose.positionOffsetX;
        positionY = restPose.positionOffsetY;
        positionZ = restPose.positionOffsetZ;
        scaleX = restPose.scaleValueX;
        scaleY = restPose.scaleValueY;
        scaleZ = restPose.scaleValueZ;
    }
}
