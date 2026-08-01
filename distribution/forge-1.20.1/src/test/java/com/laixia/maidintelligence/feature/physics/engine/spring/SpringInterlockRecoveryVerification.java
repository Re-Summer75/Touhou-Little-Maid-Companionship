package com.laixia.maidintelligence.feature.physics.engine.spring;

import com.laixia.maidintelligence.feature.physics.engine.collision.model.CollisionScratch;
import com.laixia.maidintelligence.feature.physics.engine.collision.runtime.CollisionProjection;
import org.joml.Vector3f;

/**
 * Focused recovery checks for a legal target separated by a collision barrier.
 */
final class SpringInterlockRecoveryVerification {
    private static final float DT = 1.0F / 60.0F;

    private SpringInterlockRecoveryVerification() {
    }

    static void run() {
        verifiesBlockedPathReturnsToLegalRest();
        verifiesLongVisibleOffsetReturnsToRest();
        verifiesOrdinaryContactIsNotReset();
        verifiesOccupiedRestIsNotCrossed();
    }

    private static void verifiesBlockedPathReturnsToLegalRest() {
        verifyRecovers(100.0F, 1.0F);
    }

    private static void verifiesLongVisibleOffsetReturnsToRest() {
        verifyRecovers(40.0F, 1.0F);
    }

    private static void verifyRecovers(float degrees, float leverArm) {
        SpringRecoveryState state = new SpringRecoveryState(1);
        RestProbe collisions = new RestProbe(true);
        CollisionScratch scratch = new CollisionScratch();
        Vector3f rest = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f current = directionFromRest(degrees);
        Vector3f integrated = new Vector3f();
        Vector3f projected = new Vector3f();
        boolean recovered = false;
        for (int frame = 0; frame < 120; frame++) {
            integrated.set(current).lerp(rest, 0.08F).normalize();
            projected.set(current);
            boolean moved = SpringInterlockRecovery.apply(
                    0,
                    current,
                    integrated,
                    projected,
                    rest,
                    leverArm,
                    true,
                    DT,
                    collisions,
                    scratch,
                    state
            );
            if (frame < 10 && moved) {
                throw new AssertionError(
                        "Interlock recovery fired before contact persisted"
                );
            }
            recovered |= moved;
            current.set(projected);
            if (current.angle(rest) <= 1.0E-3F) {
                break;
            }
        }
        if (!recovered || current.angle(rest) > 1.0E-3F) {
            throw new AssertionError(
                    "Collision-cancelled spring never returned to legal rest: "
                            + current.angle(rest)
            );
        }
    }

    private static void verifiesOrdinaryContactIsNotReset() {
        SpringRecoveryState state = new SpringRecoveryState(1);
        Vector3f rest = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f current = directionFromRest(40.0F);
        verifyNeverRecovers(
                state, new RestProbe(true), current, rest, 0.1F
        );
    }

    private static void verifiesOccupiedRestIsNotCrossed() {
        SpringRecoveryState state = new SpringRecoveryState(1);
        Vector3f rest = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f current = directionFromRest(100.0F);
        verifyNeverRecovers(
                state, new RestProbe(false), current, rest, 1.0F
        );
    }

    private static void verifyNeverRecovers(
            SpringRecoveryState state,
            CollisionProjection collisions,
            Vector3f current,
            Vector3f rest,
            float leverArm
    ) {
        CollisionScratch scratch = new CollisionScratch();
        Vector3f integrated = new Vector3f();
        Vector3f projected = new Vector3f();
        for (int frame = 0; frame < 120; frame++) {
            integrated.set(current).lerp(rest, 0.08F).normalize();
            projected.set(current);
            if (SpringInterlockRecovery.apply(
                    0,
                    current,
                    integrated,
                    projected,
                    rest,
                    leverArm,
                    true,
                    DT,
                    collisions,
                    scratch,
                    state
            )) {
                throw new AssertionError(
                        "Ordinary or target-occupied contact triggered recovery"
                );
            }
        }
    }

    private static Vector3f directionFromRest(float degrees) {
        float radians = (float) Math.toRadians(degrees);
        return new Vector3f(
                (float) Math.sin(radians),
                -(float) Math.cos(radians),
                0.0F
        );
    }

    private static final class RestProbe implements CollisionProjection {
        private final boolean clear;

        private RestProbe(boolean clear) {
            this.clear = clear;
        }

        @Override
        public void beginProjectionSeries() {
        }

        @Override
        public boolean project(
                Vector3f direction,
                CollisionScratch scratch,
                int maximumPasses
        ) {
            return false;
        }

        @Override
        public boolean isClear(
                Vector3f direction,
                CollisionScratch scratch
        ) {
            return clear;
        }

        @Override
        public boolean resolveRecurringContact(
                boolean recurringContact,
                Vector3f projectedDirection,
                CollisionScratch scratch
        ) {
            return false;
        }
    }
}
