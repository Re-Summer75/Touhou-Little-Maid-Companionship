package com.laixia.maidintelligence.feature.physics.client.benchmark;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.laixia.maidintelligence.feature.physics.client.CollisionBenchmarkAccess;
import com.laixia.maidintelligence.feature.physics.client.model.GeckoBoneModelPort;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;

final class CollisionBenchmarkFixture {
    private CollisionBenchmarkFixture() {
    }

    static Scenario create(Kind kind, int schema) {
        BoneModelSnapshot model = model(kind.geometry);
        String modelId = "benchmark:" + kind.name().toLowerCase()
                + "_schema" + schema;
        PhysicsSolverLayout layout = CollisionBenchmarkAccess.buildLayout(
                modelId,
                model,
                metadata(kind, schema)
        );
        CollisionBenchmarkAssertions.verify(kind, schema, layout);
        String label = switch (kind) {
            case HEAD -> schema == 2
                    ? "head/schema2-auto-disabled"
                    : "head/schema3-auto-disabled";
            case SKIRT -> schema == 2
                    ? "skirt/schema2-body-disabled"
                    : "skirt/schema3-body-only";
        };
        return new Scenario(
                label,
                model,
                layout,
                new SpringBoneSolver(layout)
        );
    }

    private static BoneModelSnapshot model(String json) {
        RawGeoModel raw = Converter.fromJsonString(json);
        return GeckoBoneModelPort.snapshotOf(new AnimatedGeoModel(
                GeoBuilder.getGeoBuilder().constructGeoModel(
                        RawGeometryTree.parseHierarchy(raw)
                )
        ));
    }

    private static String metadata(Kind kind, int schema) {
        String type = kind == Kind.HEAD ? "HAIR" : "SKIRT";
        String root = kind == Kind.HEAD
                ? "Root/Body/Head/HairA"
                : "Root/Body/SkirtA";
        String space = kind == Kind.HEAD ? "HEAD_LOCAL" : "BODY_LOCAL";
        return """
                {"schema_version":%d,"mode":"explicit","chains":[{
                  "id":"collision_breakdown","type":"%s","root":"%s",
                  "include_descendants":true,
                  "constraints":{"simulation_space":"%s"}
                }]}
                """.formatted(schema, type, root, space);
    }

    enum Kind {
        HEAD("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.benchmark_head",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"HairA","parent":"Head","pivot":[20,20,0],
                     "cubes":[{"origin":[20,16,-0.5],"size":[1,4,1],"uv":[0,0]}]},
                    {"name":"HairB","parent":"HairA","pivot":[21,16,0],
                     "cubes":[{"origin":[21,12,-0.5],"size":[1,4,1],"uv":[0,0]}]}
                  ]}]}
                """),
        SKIRT("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.benchmark_skirt",
                    "texture_width":64,"texture_height":64},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"Body","parent":"Root","pivot":[0,8,0],
                     "cubes":[{"origin":[-3,8,-2],"size":[6,8,4],"uv":[0,0]}]},
                    {"name":"Head","parent":"Body","pivot":[0,16,0],
                     "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                    {"name":"SkirtA","parent":"Body","pivot":[0,8,3],
                     "cubes":[{"origin":[-2,4,2],"size":[4,4,1],"uv":[0,0]}]},
                    {"name":"SkirtB","parent":"SkirtA","pivot":[0,4,3],
                     "cubes":[{"origin":[-2,0,2],"size":[4,4,1],"uv":[0,0]}]},
                    {"name":"LeftLeg","parent":"Body","pivot":[2,8,0],
                     "cubes":[{"origin":[1,2,-1.5],"size":[2,6,3],"uv":[0,0]}]},
                    {"name":"LeftFoot","parent":"LeftLeg","pivot":[2,2,0],
                     "cubes":[{"origin":[1,0,-2],"size":[2,2,4],"uv":[0,0]}]},
                    {"name":"RightLeg","parent":"Body","pivot":[-2,8,0],
                     "cubes":[{"origin":[-3,2,-1.5],"size":[2,6,3],"uv":[0,0]}]},
                    {"name":"RightFoot","parent":"RightLeg","pivot":[-2,2,0],
                     "cubes":[{"origin":[-3,0,-2],"size":[2,2,4],"uv":[0,0]}]}
                  ]}]}
                """);

        private final String geometry;

        Kind(String geometry) {
            this.geometry = geometry;
        }
    }

    record Scenario(
            String label,
            BoneModelSnapshot model,
            PhysicsSolverLayout layout,
            SpringBoneSolver solver
    ) {
    }
}
