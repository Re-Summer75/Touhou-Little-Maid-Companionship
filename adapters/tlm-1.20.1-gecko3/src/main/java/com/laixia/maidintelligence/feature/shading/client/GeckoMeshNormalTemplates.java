package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.laixia.maidintelligence.feature.shading.api.FaceNormalTemplate;
import com.laixia.maidintelligence.feature.shading.api.ModelShadingTemplates;

/**
 * Owns the Gecko model-lifetime bridge for platform-neutral templates.
 */
final class GeckoMeshNormalTemplates {
    private static final ModelShadingTemplates<GeoMesh> TEMPLATES =
            new ModelShadingTemplates<>(GeckoCubeGeometrySource.INSTANCE);

    private GeckoMeshNormalTemplates() {
    }

    static FaceNormalTemplate getOrBuild(GeoMesh mesh) {
        return TEMPLATES.getOrBuild(mesh);
    }

    static void clear() {
        TEMPLATES.clear();
    }

    static int size() {
        return TEMPLATES.size();
    }
}
