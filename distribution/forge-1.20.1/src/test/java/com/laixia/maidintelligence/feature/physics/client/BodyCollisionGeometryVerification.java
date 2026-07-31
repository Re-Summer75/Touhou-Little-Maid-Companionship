package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.geometry.*;

import com.laixia.maidintelligence.feature.physics.engine.collision.bake.automatic.CapsuleFit;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner.BodyCollisionGeometry;
import com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner.BodyCollisionGeometryAnalyzer;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadWinefoxGeoModel;
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
        BoneModelSnapshot model = coreModel(loadWinefoxGeoModel());
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
        PhysicsBoneGeometry.Bounds bounds = bounds(4.0F, 10.0F, 2.0F);
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
                bounds(4.0F, 1.0F, 4.0F)
        );
        require(
                shortFit.isFinite() && shortFit.sphereFallback(),
                "Short AABB did not produce a finite sphere fallback"
        );
    }

    private static BodyCollisionGeometry analyze(BoneModelSnapshot model) {
        return BodyCollisionGeometryAnalyzer.analyze(
                PhysicsBoneGeometry.analyze(model)
        );
    }

    private static BoneModelSnapshot humanoid() {
        return coreModelFromJson("""
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

    private static PhysicsBoneGeometry.Bounds bounds(
            float width,
            float height,
            float depth
    ) {
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bounds",
                    "texture_width":16,"texture_height":16},
                  "bones":[{"name":"Bounds","pivot":[0,0,0],
                    "cubes":[{"origin":[0,0,0],"size":[%s,%s,%s],
                      "uv":[0,0]}]}]}]}
                """.formatted(width, height, depth));
        return PhysicsBoneGeometry.analyze(model).modelBounds();
    }
}
