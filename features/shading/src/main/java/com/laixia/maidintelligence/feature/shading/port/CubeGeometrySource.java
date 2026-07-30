package com.laixia.maidintelligence.feature.shading.port;

import com.laixia.maidintelligence.shared.geometry.Vec3d;

/**
 * Narrow adapter boundary for reading immutable parallelepiped model elements.
 *
 * @param <M> adapter-owned mesh type
 */
public interface CubeGeometrySource<M> {
    int cubeCount(M mesh);

    Vec3d origin(M mesh, int cube);

    Vec3d xEdge(M mesh, int cube);

    Vec3d yEdge(M mesh, int cube);

    Vec3d zEdge(M mesh, int cube);
}
