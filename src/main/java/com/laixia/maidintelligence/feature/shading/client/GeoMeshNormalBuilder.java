package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import org.joml.Vector3f;

/** 为不可变 {@link GeoMesh} 构建逐面外法线与外向绕序模板。 */
final class GeoMeshNormalBuilder {
    private GeoMeshNormalBuilder() {
    }

    static GeoMeshNormalTemplate build(GeoMesh mesh) {
        int cubeCount = mesh.getCubeCount();
        float[] normals = new float[
                cubeCount * GeoCubeFaceTable.FACE_COUNT * 3
                ];
        int[] reversedWindingMasks = new int[cubeCount];

        for (int cube = 0; cube < cubeCount; cube++) {
            CubeFaceGeometry geometry = new CubeFaceGeometry(mesh, cube);
            reversedWindingMasks[cube] = geometry.reversedWindingMask();
            for (int face = 0; face < GeoCubeFaceTable.FACE_COUNT; face++) {
                Vector3f normal = geometry.faceNormal(face);
                int offset = GeoMeshNormalTemplate.faceOffset(cube, face);
                normals[offset] = normal.x;
                normals[offset + 1] = normal.y;
                normals[offset + 2] = normal.z;
            }
        }
        return new GeoMeshNormalTemplate(normals, reversedWindingMasks);
    }
}
