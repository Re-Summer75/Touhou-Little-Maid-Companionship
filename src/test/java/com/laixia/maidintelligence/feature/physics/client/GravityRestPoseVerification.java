package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.client.solver.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.client.solver.SpringBoneSolver;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Gravity must not move the pose the artist drew.
 *
 * <p>A constant force on a spring has one inevitable consequence: the spring
 * stops where the force and the restoring pull cancel, not where it was drawn.
 * Pulling world-down on a part the artist already drew hanging therefore
 * applies gravity twice and settles it lower, and a part drawn sideways sags
 * out of line entirely. No strength value avoids this — it is what a constant
 * force does — so gravity is instead resolved against the authored direction,
 * and only the component that cannot move the pose acts unconditionally.
 *
 * <p>{@code InitialPoseStabilityVerification} cannot catch this: it steps the
 * solver with {@code dt = 0}, so nothing is ever integrated. The sag needs
 * seconds of simulated time to appear, which is what this fixture spends.
 */
final class GravityRestPoseVerification {
    private static final Vector3f ZERO = new Vector3f();
    private static final float DT = 1.0F / 60.0F;
    private static final int SETTLE_FRAMES = 900;
    /**
     * Radians of drift allowed over fifteen seconds of hanging still. The
     * component along the authored direction survives, and normalizing it away
     * is exact, so anything here is float noise. Before gravity was resolved
     * against the pose a sideways part sagged by roughly {@code atan(0.9/6)},
     * about 0.15 rad, so this leaves more than an order of magnitude of margin.
     */
    private static final float TOLERANCE = 0.008F;

    private GravityRestPoseVerification() {
    }

    static void run() {
        verifiesGravityLeavesTheAuthoredPoseAlone();
        verifiesGravityStillPullsADisplacedPartDown();
    }

    /**
     * Every authored orientation has to survive, not just the hanging one. A
     * part drawn straight down is collinear with world gravity and stays put
     * even under a raw world-down pull, so it would pass on its own and prove
     * nothing; the sideways and raised fixtures are the ones that regress.
     */
    private static void verifiesGravityLeavesTheAuthoredPoseAlone() {
        for (Orientation orientation : Orientation.values()) {
            Fixture fixture = createFixture(orientation);
            Vector3f authored = new Vector3f();
            Vector3f settled = new Vector3f();
            // A zero-length step initializes the spring without integrating,
            // so this reads the authored direction rather than a first guess.
            fixture.solver().solve(ZERO, 0.0F, 0.0F, false);
            require(
                    fixture.solver().copyCurrentDirection(
                            fixture.drivenSlot(),
                            authored
                    ),
                    orientation + " never reported an authored direction"
            );
            for (int frame = 0; frame < SETTLE_FRAMES; frame++) {
                fixture.solver().restoreAnimationPose();
                fixture.solver().solve(ZERO, 0.0F, DT, false);
            }
            require(
                    fixture.solver().copyCurrentDirection(
                            fixture.drivenSlot(),
                            settled
                    ),
                    orientation + " never reported a settled direction"
            );
            float drift = angleBetween(authored, settled);
            require(
                    drift <= TOLERANCE,
                    "Gravity dragged the " + orientation
                            + " part off the pose it was drawn in by "
                            + drift + " rad (authored=" + authored
                            + ", settled=" + settled + ")"
            );
        }
    }

    /**
     * The pose is protected, not gravity removed. Once something has thrown a
     * part out of line, gravity is what brings it down rather than merely
     * back: a part held out sideways has to fall below the authored axis on
     * its way home, which a purely axial restoring pull could never do.
     */
    private static void verifiesGravityStillPullsADisplacedPartDown() {
        Fixture heavy = createFixture(Orientation.SIDEWAYS, 1.0F);
        Fixture weightless = createFixture(Orientation.SIDEWAYS, 0.0F);
        Vector3f heavyDirection = new Vector3f();
        Vector3f weightlessDirection = new Vector3f();
        float lowestHeavy = Float.MAX_VALUE;
        float lowestWeightless = Float.MAX_VALUE;
        /*
         * Lifting the part is done with an upward acceleration for a while and
         * then releasing it, which leaves it swinging back through the pose
         * under its own momentum. The lowest point of that swing is where the
         * two fixtures have to disagree.
         */
        Vector3f lift = new Vector3f(0.0F, -6.0F, 0.0F);
        for (int frame = 0; frame < 420; frame++) {
            Vector3f acceleration = frame < 60 ? lift : ZERO;
            heavy.solver().restoreAnimationPose();
            weightless.solver().restoreAnimationPose();
            heavy.solver().solve(acceleration, 0.0F, DT, false);
            weightless.solver().solve(acceleration, 0.0F, DT, false);
            if (frame < 60) {
                continue;
            }
            heavy.solver().copyCurrentDirection(
                    heavy.drivenSlot(),
                    heavyDirection
            );
            weightless.solver().copyCurrentDirection(
                    weightless.drivenSlot(),
                    weightlessDirection
            );
            lowestHeavy = Math.min(lowestHeavy, heavyDirection.y());
            lowestWeightless = Math.min(
                    lowestWeightless,
                    weightlessDirection.y()
            );
        }
        require(
                lowestHeavy < lowestWeightless - 1.0E-3F,
                "Gravity stopped acting on a displaced part: heavy reached "
                        + lowestHeavy + ", weightless reached "
                        + lowestWeightless
        );
    }

    private static float angleBetween(Vector3f left, Vector3f right) {
        return (float) Math.acos(
                Math.max(-1.0F, Math.min(1.0F, left.dot(right)))
        );
    }

    private static Fixture createFixture(Orientation orientation) {
        return createFixture(orientation, 1.0F);
    }

    private static Fixture createFixture(
            Orientation orientation,
            float gravityScale
    ) {
        String suffix = orientation.name().toLowerCase()
                + '_' + Float.toString(gravityScale).replace('.', '_');
        AnimatedGeoModel model = modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.gravity_%s",
                      "texture_width":16,
                      "texture_height":16
                    },
                    "bones":[
                      {"name":"Root","pivot":[0,12,0]},
                      {"name":"Strand","parent":"Root","pivot":[0,12,0],
                       "cubes":[{
                         "origin":[%s],
                         "size":[%s],
                         "uv":[0,0]
                       }]}
                    ]
                  }]
                }
                """.formatted(
                suffix,
                orientation.origin,
                orientation.size
        ));
        PhysicsMetadata metadata = PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":1,
                          "mode":"explicit",
                          "chains":[{
                            "id":"gravity_%s",
                            "type":"TAIL",
                            "root":"Root/Strand",
                            "profile":{
                              "gravity_scale":%s,
                              "wind_scale":0.0
                            }
                          }]
                        }
                        """.formatted(
                        suffix,
                        Float.toString(gravityScale)
                )).getAsJsonObject(),
                "gravity rest pose verification"
        );
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:gravity_" + suffix,
                model,
                metadata
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        AnimatedGeoBone strand = model.bones().get("Strand");
        int drivenSlot = -1;
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == strand) {
                drivenSlot = layout.node(index).drivenSlot();
                break;
            }
        }
        require(
                drivenSlot >= 0,
                "Gravity fixture " + suffix + " was not driven"
        );
        return new Fixture(new SpringBoneSolver(layout), drivenSlot);
    }

    /**
     * Where the geometry sits relative to the pivot, which is what the solver
     * reads the authored direction from.
     */
    private enum Orientation {
        /** Drawn hanging, collinear with world gravity. */
        HANGING("-0.5,6,-0.5", "1,6,1"),
        /** Drawn out along +X, fully across the world pull. */
        SIDEWAYS("0,11.5,-0.5", "6,1,1"),
        /** Drawn rising away from the world pull. */
        RAISED("-0.5,12,-0.5", "1,6,1"),
        /** Drawn diagonally, so neither component vanishes. */
        DIAGONAL("0,12,-0.5", "4,4,1");

        private final String origin;
        private final String size;

        Orientation(String origin, String size) {
            this.origin = origin;
            this.size = size;
        }
    }

    private record Fixture(SpringBoneSolver solver, int drivenSlot) {
    }
}
