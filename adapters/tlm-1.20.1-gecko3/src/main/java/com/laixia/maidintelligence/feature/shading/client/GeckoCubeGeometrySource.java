package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.shading.port.CubeGeometrySource;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import org.joml.Vector3f;

/**
 * Converts Gecko cube axes into the stable shading geometry contract.
 */
final class GeckoCubeGeometrySource implements CubeGeometrySource<GeoMesh> {
    static final GeckoCubeGeometrySource INSTANCE =
            new GeckoCubeGeometrySource();

    private GeckoCubeGeometrySource() {
    }

    @Override
    public int cubeCount(GeoMesh mesh) {
        return mesh.getCubeCount();
    }

    @Override
    public Vec3d origin(GeoMesh mesh, int cube) {
        return stableVector(mesh.position(cube));
    }

    @Override
    public Vec3d xEdge(GeoMesh mesh, int cube) {
        return stableVector(mesh.dx(cube));
    }

    @Override
    public Vec3d yEdge(GeoMesh mesh, int cube) {
        return stableVector(mesh.dy(cube));
    }

    @Override
    public Vec3d zEdge(GeoMesh mesh, int cube) {
        return stableVector(mesh.dz(cube));
    }

    private static Vec3d stableVector(Vector3f vector) {
        // Conversion occurs only on a weak-cache miss, never in vertex emission.
        return new Vec3d(vector.x, vector.y, vector.z);
    }
}
