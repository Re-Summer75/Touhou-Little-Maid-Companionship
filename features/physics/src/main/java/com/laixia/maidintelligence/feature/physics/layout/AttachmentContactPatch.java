package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

/**
 * Robust child/support contact evidence baked during model discovery.
 */
record AttachmentContactPatch(
        Vector3f center,
        Vector3f dominantCenter,
        Vector3f normal,
        Vector3f nearestMidpoint,
        Sample[] samples,
        MeshSurfaceSamples childSurface,
        float confidence,
        float minimumDistance,
        float bandwidth
) {
    AttachmentContactPatch {
        center = new Vector3f(center);
        dominantCenter = new Vector3f(dominantCenter);
        normal = new Vector3f(normal);
        nearestMidpoint = new Vector3f(nearestMidpoint);
        samples = samples.clone();
    }

    static AttachmentContactPatch none(MeshSurfaceSamples surface) {
        return new AttachmentContactPatch(
                new Vector3f(),
                new Vector3f(),
                new Vector3f(),
                new Vector3f(),
                new Sample[0],
                surface,
                0.0F,
                Float.POSITIVE_INFINITY,
                0.0F
        );
    }

    boolean present() {
        return samples.length > 0;
    }

    record Sample(
            Vector3f childPoint,
            Vector3f supportPoint,
            Vector3f midpoint,
            float initialSignedDistance,
            float weight,
            int childBoxIndex
    ) {
        Sample {
            childPoint = new Vector3f(childPoint);
            supportPoint = new Vector3f(supportPoint);
            midpoint = new Vector3f(midpoint);
        }
    }
}
