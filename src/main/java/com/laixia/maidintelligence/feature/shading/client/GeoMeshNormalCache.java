package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 以不可变 {@link GeoMesh} 身份缓存逐面外法线与绕序模板。弱键保证热加载废弃的模型
 * 不会被本模块延长生命周期。
 */
final class GeoMeshNormalCache {
    private static final Map<GeoMesh, GeoMeshNormalTemplate> TEMPLATES =
            new WeakHashMap<>();

    private GeoMeshNormalCache() {
    }

    static synchronized GeoMeshNormalTemplate getOrBuild(GeoMesh mesh) {
        GeoMeshNormalTemplate cached = TEMPLATES.get(mesh);
        if (cached != null) {
            return cached;
        }
        GeoMeshNormalTemplate created = GeoMeshNormalBuilder.build(mesh);
        TEMPLATES.put(mesh, created);
        return created;
    }

    static synchronized void clear() {
        TEMPLATES.clear();
    }

    static synchronized int size() {
        return TEMPLATES.size();
    }
}
