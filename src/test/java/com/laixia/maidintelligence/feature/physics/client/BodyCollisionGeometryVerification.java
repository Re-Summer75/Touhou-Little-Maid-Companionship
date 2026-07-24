package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.BodyCollisionGeometryAnalyzer;
import com.laixia.maidintelligence.feature.physics.client.solver.collision.build.CapsuleFit;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.modelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

final class BodyCollisionGeometryVerification {
    private static final float EPSILON = 1.0E-5F;

    private BodyCollisionGeometryVerification() {
    }

    static void run() throws Exception {
        verifiesNamedBodyAndLegs();
        verifiesWinefoxSkeletonIsolation();
        verifiesMissingLegFallback();
        verifiesAsymmetricLegRejection();
        verifiesConservativeCapsuleFit();
    }

    private static void verifiesNamedBodyAndLegs() {
        BodyCollisionGeometry geometry = analyze(humanoid(1.0F, -3.0F));
        require(geometry.hasHead(), "Head landmark was not retained");
        require(geometry.hasBody(), "Body landmark was not retained");
        require(
                "Body".equals(geometry.body().bone().getName()),
                "Standard body node was not selected"
        );
        BodyCollisionGeometry.LegPair legs = geometry.legs().orElseThrow(
                () -> new AssertionError("Named leg pair was not selected")
        );
        require(
                "LeftLeg".equals(legs.left().node().bone().getName())
                        && "RightLeg".equals(
                        legs.right().node().bone().getName()
                ),
                "Named leg sides were not preserved"
        );
        require(
                legs.left().bounds() == legs.left().node().subtreeBounds()
                        && legs.right().bounds()
                        == legs.right().node().subtreeBounds(),
                "Leg subtree bounds were not retained"
        );
        BodyCollisionGeometry.BackPlane plane =
                geometry.backPlane().orElseThrow();
        require(
                Math.abs(plane.pointZ() - geometry.bodyBounds().maxZ())
                        <= EPSILON
                        && plane.normalZ() == 1.0D,
                "Body back plane was not derived from the rear AABB face"
        );
    }

    private static void verifiesWinefoxSkeletonIsolation() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadWinefoxGeoModel());
        BodyCollisionGeometry geometry = analyze(model);
        BodyCollisionGeometry.LegPair legs = geometry.legs().orElseThrow(
                () -> new AssertionError("Winefox maid legs were not selected")
        );
        require(
                "UpperBody".equals(geometry.body().bone().getName()),
                "FOX body displaced the head-coherent maid body"
        );
        require(
                "LeftLeg".equals(legs.left().node().bone().getName())
                        && "RightLeg".equals(
                        legs.right().node().bone().getName()
                ),
                "Winefox auxiliary LeftLeg2/RightLeg2 pair leaked in"
        );
        require(
                !legs.left().node().path().contains("/FOX/")
                        && !legs.right().node().path().contains("/FOX/"),
                "Legs were taken from the FOX skeleton"
        );
    }

    private static void verifiesMissingLegFallback() {
        BodyCollisionGeometry geometry = analyze(humanoid(null, null));
        require(geometry.hasBody(), "Missing legs removed valid body geometry");
        require(geometry.legs().isEmpty(), "Missing legs produced a false pair");
        require(
                geometry.backPlane().isPresent(),
                "Missing legs removed the body back plane"
        );
    }

    private static void verifiesAsymmetricLegRejection() {
        BodyCollisionGeometry geometry = analyze(humanoid(1.0F, -9.0F));
        require(
                geometry.legs().isEmpty(),
                "Strongly asymmetric named legs were accepted"
        );
        require(geometry.hasBody(), "Rejected legs removed valid body geometry");
    }

    private static void verifiesConservativeCapsuleFit() {
        PhysicsBoneGeometry.Bounds bounds = new PhysicsBoneGeometry.Bounds(
                -2.0D, 0.0D, -1.0D,
                2.0D, 10.0D, 1.0D
        );
        CapsuleFit fit = CapsuleFit.fromBounds(bounds);
        Vector3f start = fit.start();
        Vector3f end = fit.end();
        require(fit.isFinite(), "Capsule fit contained non-finite values");
        require(!fit.sphereFallback(), "Tall AABB fell back to a sphere");
        require(
                fit.radius() <= 1.0F + EPSILON
                        && start.y - fit.radius() >= bounds.minY() - EPSILON
                        && end.y + fit.radius() <= bounds.maxY() + EPSILON,
                "Capsule fit escaped its source AABB"
        );
        CapsuleFit shortFit = CapsuleFit.fit(
                new PhysicsBoneGeometry.Bounds(
                        -2.0D, 0.0D, -2.0D,
                        2.0D, 1.0D, 2.0D
                )
        );
        require(
                shortFit.isFinite() && shortFit.sphereFallback(),
                "Short AABB did not produce a finite sphere fallback"
        );
    }

    private static BodyCollisionGeometry analyze(AnimatedGeoModel model) {
        return BodyCollisionGeometryAnalyzer.analyze(
                PhysicsBoneGeometry.analyze(model)
        );
    }

    private static AnimatedGeoModel humanoid(
            Float leftOrigin,
            Float rightOrigin
    ) {
        String legs = leftOrigin == null || rightOrigin == null
                ? ""
                : """
                  ,{"name":"LeftLeg","parent":"Body","pivot":[2,8,0],
                    "cubes":[{"origin":[%s,2,-1.5],"size":[2,6,3],"uv":[0,0]}]}
                  ,{"name":"LeftFoot","parent":"LeftLeg","pivot":[2,2,0],
                    "cubes":[{"origin":[%s,0,-2],"size":[2,2,4],"uv":[0,0]}]}
                  ,{"name":"RightLeg","parent":"Body","pivot":[-2,8,0],
                    "cubes":[{"origin":[%s,2,-1.5],"size":[2,6,3],"uv":[0,0]}]}
                  ,{"name":"RightFoot","parent":"RightLeg","pivot":[-2,2,0],
                    "cubes":[{"origin":[%s,0,-2],"size":[2,2,4],"uv":[0,0]}]}
                  """.formatted(
                        leftOrigin,
                        leftOrigin,
                        rightOrigin,
                        rightOrigin
                );
        return modelFromJson("""
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{"identifier":"geometry.body_collision",
                      "texture_width":64,"texture_height":64},
                    "bones":[
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,8,-2],"size":[6,8,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]}
                      %s
                    ]
                  }]
                }
                """.formatted(legs));
    }
}
