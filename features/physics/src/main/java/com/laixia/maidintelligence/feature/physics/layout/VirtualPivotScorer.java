package com.laixia.maidintelligence.feature.physics.layout;


import org.joml.Vector3f;

/**
 * Scores pivot candidates by contact retention and added penetration.
 */
final class VirtualPivotScorer {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float EPSILON = 1.0E-6F;
    private static final float[] ANGLES = {
            (float) Math.toRadians(-10.0D),
            (float) Math.toRadians(-5.0D),
            (float) Math.toRadians(5.0D),
            (float) Math.toRadians(10.0D)
    };

    private VirtualPivotScorer() {
    }

    static Score score(
            Vector3f pivot,
            Vector3f authoredPivot,
            BoneMeshMetrics child,
            BoneMeshMetrics support,
            AttachmentContactPatch patch
    ) {
        Vector3f direction = new Vector3f(child.centroid()).sub(pivot);
        if (direction.lengthSquared() < EPSILON) {
            direction.set(patch.normal());
        }
        direction.normalize();
        Vector3f firstAxis = perpendicular(direction);
        Vector3f secondAxis =
                new Vector3f(direction).cross(firstAxis).normalize();
        float scale = Math.max(
                1.0F / PIXELS_PER_BLOCK,
                child.diagonal()
        );
        float allowance = Math.max(
                0.25F / PIXELS_PER_BLOCK,
                patch.minimumDistance() * 0.10F
        );
        float contactLoss = 0.0F;
        float penetrationLoss = 0.0F;
        int states = 0;
        for (Vector3f axis : new Vector3f[]{firstAxis, secondAxis}) {
            for (float angle : ANGLES) {
                contactLoss += contactLoss(
                        pivot,
                        axis,
                        angle,
                        support,
                        patch,
                        allowance
                );
                penetrationLoss += penetrationLoss(
                        pivot,
                        axis,
                        angle,
                        support,
                        patch.childSurface(),
                        allowance
                );
                states++;
            }
        }
        float normalizer = Math.max(EPSILON, scale * scale * states);
        contactLoss /= normalizer;
        penetrationLoss /= normalizer;
        float anchorLoss = pivot.distanceSquared(patch.center())
                / (scale * scale);
        float authoredDistance = authoredPivot.distance(patch.center());
        float priorWeight = authoredDistance <= scale * 0.35F
                ? 0.12F
                : 0.0F;
        float priorLoss = pivot.distanceSquared(authoredPivot)
                / (scale * scale);
        float total = contactLoss
                + penetrationLoss * 2.5F
                + anchorLoss * 0.30F
                + priorLoss * priorWeight;
        return new Score(total, contactLoss, penetrationLoss);
    }

    private static float contactLoss(
            Vector3f pivot,
            Vector3f axis,
            float angle,
            BoneMeshMetrics support,
            AttachmentContactPatch patch,
            float allowance
    ) {
        float loss = 0.0F;
        float weight = 0.0F;
        for (AttachmentContactPatch.Sample sample : patch.samples()) {
            Vector3f moved = rotate(
                    sample.childPoint(),
                    pivot,
                    axis,
                    angle
            );
            float signedDistance =
                    MeshDistanceField.signedDistance(support, moved);
            float addedSeparation = Math.max(
                    0.0F,
                    Math.abs(signedDistance)
                            - Math.abs(sample.initialSignedDistance())
                            - allowance
            );
            loss += sample.weight()
                    * addedSeparation * addedSeparation;
            weight += sample.weight();
        }
        return loss / Math.max(EPSILON, weight);
    }

    private static float penetrationLoss(
            Vector3f pivot,
            Vector3f axis,
            float angle,
            BoneMeshMetrics support,
            MeshSurfaceSamples surface,
            float allowance
    ) {
        float loss = 0.0F;
        float weight = 0.0F;
        for (int index = 0; index < surface.points().length; index++) {
            Vector3f point = surface.points()[index];
            float initial = MeshDistanceField.signedDistance(support, point);
            float moved = MeshDistanceField.signedDistance(
                    support,
                    rotate(point, pivot, axis, angle)
            );
            float restPenetration = Math.max(0.0F, -initial);
            float addedPenetration = Math.max(
                    0.0F,
                    -moved - restPenetration - allowance
            );
            loss += surface.weights()[index]
                    * addedPenetration * addedPenetration;
            weight += surface.weights()[index];
        }
        return loss / Math.max(EPSILON, weight);
    }

    private static Vector3f rotate(
            Vector3f point,
            Vector3f pivot,
            Vector3f axis,
            float angle
    ) {
        Vector3f offset = new Vector3f(point).sub(pivot);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float projection = axis.dot(offset);
        return new Vector3f(offset).mul(cosine)
                .fma(sine, new Vector3f(axis).cross(offset))
                .fma(projection * (1.0F - cosine), axis)
                .add(pivot);
    }

    private static Vector3f perpendicular(Vector3f direction) {
        Vector3f basis = Math.abs(direction.y) < 0.90F
                ? new Vector3f(0.0F, 1.0F, 0.0F)
                : new Vector3f(1.0F, 0.0F, 0.0F);
        return basis.cross(direction).normalize();
    }

    record Score(float total, float contact, float penetration) {
    }
}
