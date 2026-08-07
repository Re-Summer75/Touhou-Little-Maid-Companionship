package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;

final class RuntimeEndpointHierarchyFixture {
    private RuntimeEndpointHierarchyFixture() {
    }

    static BoneModelSnapshot model() {
        return coreModelFromJson("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.runtime_endpoints",
                      "texture_width": 64,
                      "texture_height": 64,
                      "visible_bounds_width": 4,
                      "visible_bounds_height": 4,
                      "visible_bounds_offset": [0, 1, 0]
                    },
                    "bones": [
                      {"name":"Root","pivot":[0,0,0]},
                      {"name":"Body","parent":"Root","pivot":[0,8,0],
                       "cubes":[{"origin":[-3,0,-2],"size":[6,16,4],
                                 "uv":[0,0]}]},
                      {"name":"Head","parent":"Body","pivot":[0,16,0],
                       "cubes":[{"origin":[-4,16,-4],"size":[8,8,8],
                                 "uv":[0,0]}]},
                      {"name":"ParentHair","parent":"Head","pivot":[32,32,32],
                       "cubes":[{"origin":[-0.25,20,-0.25],
                                 "size":[0.5,4,0.5],"uv":[0,0]}]},
                      {"name":"ChildHair","parent":"ParentHair","pivot":[0,18,0],
                       "cubes":[{"origin":[-0.2,14,-0.2],
                                 "size":[0.4,4,0.4],"uv":[0,0]}]}
                    ]
                  }]
                }
                """);
    }

    static PhysicsMetadata metadata() {
        return PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "schema_version": 2,
                          "mode": "explicit",
                          "chains": [{
                            "id": "runtime_endpoint_chain",
                            "type": "HAIR",
                            "root": "Root/Body/Head/ParentHair",
                            "include_descendants": true,
                            "profile": {
                              "gravity_scale": 0.0,
                              "stiffness_scale": 0.7
                            },
                            "constraints": {
                              "simulation_space": "HEAD_LOCAL",
                              "rotation_inertia_scale": 1.0,
                              "swing_limits": {
                                "left_degrees": 70.0,
                                "right_degrees": 70.0,
                                "outward_degrees": 70.0,
                                "inward_degrees": 70.0
                              },
                              "backstop": false,
                              "head_collision": false
                            }
                          }]
                        }
                        """).getAsJsonObject(),
                "runtime endpoint hierarchy verification"
        );
    }

    static PhysicsMetadata collisionMetadata() {
        return PhysicsMetadataJsonParser.parse(
                JsonParser.parseString("""
                        {
                          "schema_version":3,
                          "mode":"explicit",
                          "chains":[
                            {
                              "id":"runtime_parent",
                              "type":"HAIR",
                              "root":"Root/Body/Head/ParentHair",
                              "include_descendants":false,
                              "profile":{"gravity_scale":0.0},
                              "constraints":{
                                "simulation_space":"MODEL",
                                "collision":{"auto":false}
                              }
                            },
                            {
                              "id":"runtime_child_collision",
                              "type":"HAIR",
                              "root":"Root/Body/Head/ParentHair/ChildHair",
                              "include_descendants":false,
                              "profile":{"gravity_scale":0.0},
                              "constraints":{
                                "simulation_space":"MODEL",
                                "collision":{"auto":false,"proxies":[{
                                  "kind":"plane",
                                  "reference":"MODEL",
                                  "point":[0,15,0],
                                  "normal":[0,1,0],
                                  "hit_radius":0
                                }]}
                              }
                            }
                          ]
                        }
                        """).getAsJsonObject(),
                "runtime segment collision verification"
        );
    }
}
