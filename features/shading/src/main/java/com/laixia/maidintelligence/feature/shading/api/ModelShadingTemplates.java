package com.laixia.maidintelligence.feature.shading.api;

import com.laixia.maidintelligence.feature.shading.application.FaceNormalTemplateBuilder;
import com.laixia.maidintelligence.feature.shading.port.CubeGeometrySource;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Weakly caches normal templates without extending adapter model lifetimes.
 *
 * @param <M> adapter-owned immutable mesh type
 */
public final class ModelShadingTemplates<M> {
    private final CubeGeometrySource<? super M> source;
    private final Map<M, FaceNormalTemplate> templates =
            new WeakHashMap<>();

    public ModelShadingTemplates(CubeGeometrySource<? super M> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public synchronized FaceNormalTemplate getOrBuild(M mesh) {
        Objects.requireNonNull(mesh, "mesh");
        FaceNormalTemplate cached = templates.get(mesh);
        if (cached != null) {
            return cached;
        }
        FaceNormalTemplate created = FaceNormalTemplateBuilder.build(
                mesh,
                source
        );
        templates.put(mesh, created);
        return created;
    }

    public synchronized void clear() {
        templates.clear();
    }

    public synchronized int size() {
        return templates.size();
    }
}
