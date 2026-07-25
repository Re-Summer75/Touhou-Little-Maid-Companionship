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
        verifiesNamedBody();
        verifiesWinefoxBodyIsolation();
        verifiesConservativeCapsuleFit();
    }

    private static void verifiesNamedBody() {
        BodyCollisionGeometry geometry = analyze(humanoid());
        require(geometry.hasBody(), "Body landmark was not retained");
        require(
                "Body".equals(geometry.body().bone().getName()),
                "Standard body node was not selected"
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

    private static void verifiesWinefoxBodyIsolation() throws Exception {
        AnimatedGeoModel model = new AnimatedGeoModel(loadWinefoxGeoModel());
        BodyCollisionGeometry geometry = analyze(model);
        require(
                "UpperBody".equals(geometry.body().bone().getName()),
                "FOX body displaced the head-coherent maid body"
        );
        require(
                geometry.backPlane().isPresent(),
                "Winefox coherent body did not produce a back plane"
        );
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

    private static AnimatedGeoModel humanoid() {
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
                    ]
                  }]
                }
                """);
    }
}
