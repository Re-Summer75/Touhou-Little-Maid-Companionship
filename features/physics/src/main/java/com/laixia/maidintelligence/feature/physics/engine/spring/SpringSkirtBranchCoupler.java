package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.layout.SkirtBranchConstraintLayout;
import org.joml.Vector3f;

/**
 * Applies a compliant maximum-distance tether between adjacent skirt roots.
 */
final class SpringSkirtBranchCoupler {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float MINIMUM_SLACK = 0.5F / PIXELS_PER_BLOCK;
    private static final float MAXIMUM_SLACK = 1.5F / PIXELS_PER_BLOCK;
    private static final float SLACK_FRACTION = 0.15F;
    private static final float COMPLIANCE = 2.0E-5F;
    private static final int ITERATIONS = 3;

    private SpringSkirtBranchCoupler() {
    }

    static boolean apply(
            SpringBoneContext context,
            int followerNodeIndex,
            float dt
    ) {
        SkirtBranchConstraintLayout layout =
                context.layout.skirtBranchConstraints();
        int pairIndex = layout.pairForFollowerNode(followerNodeIndex);
        SpringBoneScratch scratch = context.scratch;
        scratch.couplingCorrected = false;
        if (pairIndex < 0
                || !Float.isFinite(dt)
                || dt <= SpringBoneMath.EPSILON
                || scratch.runtimeSegmentLength <= SpringBoneMath.EPSILON) {
            return false;
        }

        SkirtBranchConstraintLayout.Pair pair = layout.pair(pairIndex);
        if (!context.endpoints.copyPivot(
                pair.leaderNodeIndex(),
                scratch.couplingLeaderPivot
        ) || !context.endpoints.copyTip(
                pair.leaderNodeIndex(),
                scratch.couplingLeaderTip
        )) {
            return false;
        }
        float leaderLength = scratch.couplingLeaderPivot.distance(
                scratch.couplingLeaderTip
        );
        if (leaderLength <= SpringBoneMath.EPSILON
                || scratch.nextDirection.lengthSquared()
                <= SpringBoneMath.EPSILON) {
            return false;
        }

        SpringBoneState state = context.state;
        Vector3f leaderRest =
                state.integration.restDirections[pair.leaderDrivenSlot()];
        Vector3f followerRest =
                state.integration.restDirections[pair.followerDrivenSlot()];
        if (leaderRest.lengthSquared() <= SpringBoneMath.EPSILON
                || followerRest.lengthSquared() <= SpringBoneMath.EPSILON) {
            return false;
        }
        scratch.couplingLeaderRestTip
                .set(scratch.couplingLeaderPivot)
                .fma(leaderLength, leaderRest);
        scratch.couplingFollowerRestTip
                .set(scratch.pivotScratch)
                .fma(scratch.runtimeSegmentLength, followerRest);
        float authoredDistance = scratch.couplingLeaderRestTip.distance(
                scratch.couplingFollowerRestTip
        );
        float slack = Math.max(
                MINIMUM_SLACK,
                Math.min(MAXIMUM_SLACK, authoredDistance * SLACK_FRACTION)
        );
        float maximumDistance = authoredDistance + slack;
        float step = Math.min(dt, 0.1F);
        float alphaTilde = COMPLIANCE / (step * step);
        float inverseMass = 1.0F / Math.max(
                1.0F,
                context.layout.node(followerNodeIndex)
                        .decision().profile().massScale()
        );
        float lambda = state.skirtCoupling.lambdas[pairIndex];
        scratch.couplingStart.set(scratch.nextDirection);

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            scratch.endpointScratch.set(scratch.pivotScratch)
                    .fma(
                            scratch.runtimeSegmentLength,
                            scratch.nextDirection
                    );
            scratch.couplingSeparation.set(scratch.endpointScratch)
                    .sub(scratch.couplingLeaderTip);
            float distance = scratch.couplingSeparation.length();
            float violation = distance - maximumDistance;
            if (violation <= SpringBoneMath.EPSILON
                    || distance <= SpringBoneMath.EPSILON) {
                break;
            }
            scratch.couplingSeparation.div(distance);
            float deltaLambda = (
                    -violation - alphaTilde * lambda
            ) / (inverseMass + alphaTilde);
            float nextLambda = Math.min(0.0F, lambda + deltaLambda);
            float appliedLambda = nextLambda - lambda;
            if (Math.abs(appliedLambda) <= SpringBoneMath.EPSILON) {
                break;
            }
            scratch.endpointScratch.fma(
                    inverseMass * appliedLambda,
                    scratch.couplingSeparation
            );
            scratch.nextDirection.set(scratch.endpointScratch)
                    .sub(scratch.pivotScratch);
            if (scratch.nextDirection.lengthSquared()
                    <= SpringBoneMath.EPSILON) {
                scratch.nextDirection.set(followerRest);
            } else {
                scratch.nextDirection.normalize();
            }
            lambda = nextLambda;
            scratch.couplingCorrected = true;
        }
        state.skirtCoupling.lambdas[pairIndex] = lambda;
        if (scratch.couplingCorrected) {
            // A pose cut closes a seam over several frames instead of snapping.
            SpringConstraintProjector.limitCorrection(
                    scratch.couplingStart,
                    scratch.nextDirection,
                    SpringConstraintProjector.maximumCorrection(
                            dt, scratch.runtimeSegmentLength
                    )
            );
        }
        return scratch.couplingCorrected;
    }
}
