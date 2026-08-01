package com.laixia.maidintelligence.feature.physics.engine.collision.bake.planner;

import com.laixia.maidintelligence.feature.physics.discovery.classifier.PhysicsBoneClassifier;
import com.laixia.maidintelligence.feature.physics.discovery.classifier.RigidEquipmentClassifier;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.geometry.PhysicsBoneGeometry;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether rigid model geometry may become an automatic collider.
 *
 * <p>Being rigid is not sufficient: locators, held equipment, expression
 * layers and render effects have geometry for rendering but are not structural
 * surfaces that should push secondary motion.
 */
public final class AutomaticCollisionSourcePolicy {
    private static final List<String> EFFECT_MARKERS = List.of(
            "ysmglow", "glow", "effect", "particle", "emitter", "aura"
    );

    private AutomaticCollisionSourcePolicy() {
    }

    public static boolean allows(
            PhysicsBoneGeometry.Node node,
            PhysicsBoneGeometry.Analysis geometry,
            BoneModelSnapshot model
    ) {
        if (node == null
                || !node.hasGeometry()
                || Boolean.TRUE.equals(node.bone().geometry().dontRender())) {
            return false;
        }
        if (RigidEquipmentClassifier.isHandAttachmentScope(
                node, geometry, model
        )) {
            return false;
        }
        if (RigidEquipmentClassifier.classify(node, geometry, model).kind()
                != RigidEquipmentClassifier.Kind.NONE) {
            return false;
        }
        PhysicsBoneGeometry.Node cursor = node;
        while (cursor != null) {
            String name = cursor.bone().getName();
            if (PhysicsBoneClassifier.isFacialFeature(name)
                    || isRenderEffect(name)) {
                return false;
            }
            cursor = cursor.parent();
        }
        return true;
    }

    private static boolean isRenderEffect(String name) {
        String compact = name == null
                ? ""
                : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return EFFECT_MARKERS.stream().anyMatch(compact::contains);
    }
}
