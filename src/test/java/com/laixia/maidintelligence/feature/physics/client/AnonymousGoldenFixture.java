package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.google.gson.JsonParser;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.geoModelFromJson;

final class AnonymousGoldenFixture {
    private AnonymousGoldenFixture() {
    }

    static GeoModel geoModel() {
        return geoModelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.anonymous_golden",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],"uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[0,0]}]},
                      {"name":"bone17","parent":"Body","pivot":[0,9,2],
                       "cubes":[{"origin":[-1,2,7],"size":[2,7,2],"uv":[0,0]}]},
                      {"name":"bone18","parent":"bone17","pivot":[0,4,8],
                       "cubes":[{"origin":[-0.8,-2,9],"size":[1.6,6,1.5],"uv":[0,0]}]},
                      {"name":"RigidDecoration","parent":"Head","pivot":[0,22,0],
                       "cubes":[{"origin":[-1,22,-1],"size":[2,2,2],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
    }

    static PhysicsMetadata metadata() {
        return PhysicsMetadata.parse(
                JsonParser.parseString("""
                        {
                          "mode": "explicit",
                          "chains": [{
                            "id": "anonymous_chain",
                            "type": "TAIL",
                            "root": "Root/Body/bone17",
                            "include_descendants": true
                          }]
                        }
                        """).getAsJsonObject(),
                "anonymous golden verification"
        );
    }
}
