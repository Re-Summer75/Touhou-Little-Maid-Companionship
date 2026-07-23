package com.laixia.maidintelligence.feature.physics.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import com.laixia.maidintelligence.feature.physics.client.solver.BoneKinematics;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Keeps instance-local plans while sharing expensive discovery across animated
 * skeletons created from the same immutable {@link GeoModel}.
 */
final class PhysicsBonePlanCache {
    private static final Map<AnimatedGeoModel, Entry> PLANS = new WeakHashMap<>();
    private static final Map<GeoModel, Map<String, Template>> TEMPLATES =
            new WeakHashMap<>();

    private static int fullDiscoveryCount;
    private static int templateBindingCount;

    private PhysicsBonePlanCache() {
    }

    static synchronized PhysicsBoneSelectionPlan getOrCompute(
            String modelId,
            AnimatedGeoModel model
    ) {
        Entry cached = PLANS.get(model);
        if (cached != null && Objects.equals(cached.modelId(), modelId)) {
            return cached.plan();
        }

        Map<String, Template> byModelId = TEMPLATES.computeIfAbsent(
                model.geoModel(),
                ignored -> new HashMap<>()
        );
        Template template = byModelId.get(modelId);
        PhysicsBoneSelectionPlan plan = template == null
                ? null
                : template.bind(model);
        if (plan != null) {
            templateBindingCount++;
        } else {
            PhysicsMetadata metadata = PhysicsMetadataLoader.find(modelId);
            plan = PhysicsBoneDiscoverer.discover(modelId, model, metadata);
            byModelId.put(modelId, Template.capture(model, plan));
            fullDiscoveryCount++;
        }
        PLANS.put(model, new Entry(modelId, plan));
        return plan;
    }

    static synchronized void clear() {
        PLANS.clear();
        TEMPLATES.clear();
        fullDiscoveryCount = 0;
        templateBindingCount = 0;
    }

    static synchronized int fullDiscoveryCount() {
        return fullDiscoveryCount;
    }

    static synchronized int templateBindingCount() {
        return templateBindingCount;
    }

    private record Entry(String modelId, PhysicsBoneSelectionPlan plan) {
    }

    private static final class Template {
        private final String modelId;
        private final Map<GeoBone, TemplateBone> bones;

        private Template(
                String modelId,
                IdentityHashMap<GeoBone, TemplateBone> bones
        ) {
            this.modelId = modelId;
            this.bones = Collections.unmodifiableMap(
                    new IdentityHashMap<>(bones)
            );
        }

        private static Template capture(
                AnimatedGeoModel model,
                PhysicsBoneSelectionPlan plan
        ) {
            IdentityHashMap<GeoBone, TemplateBone> bones =
                    new IdentityHashMap<>();
            for (AnimatedGeoBone bone : model.topLevelBones()) {
                captureBone(bone, plan, bones);
            }
            return new Template(plan.modelId(), bones);
        }

        private static void captureBone(
                AnimatedGeoBone bone,
                PhysicsBoneSelectionPlan plan,
                IdentityHashMap<GeoBone, TemplateBone> output
        ) {
            output.put(
                    bone.geoBone(),
                    new TemplateBone(
                            plan.decision(bone),
                            plan.path(bone),
                            plan.kinematics(bone)
                    )
            );
            for (AnimatedGeoBone child : bone.children()) {
                captureBone(child, plan, output);
            }
        }

        private PhysicsBoneSelectionPlan bind(AnimatedGeoModel model) {
            PhysicsBoneSelectionPlan.Builder output =
                    PhysicsBoneSelectionPlan.builder(modelId);
            Binding binding = new Binding(output);
            for (AnimatedGeoBone bone : model.topLevelBones()) {
                if (!bindBone(bone, binding)) {
                    return null;
                }
            }
            if (binding.boundCount != bones.size()) {
                return null;
            }
            return output.build();
        }

        private boolean bindBone(
                AnimatedGeoBone bone,
                Binding binding
        ) {
            TemplateBone templateBone = bones.get(bone.geoBone());
            if (templateBone == null) {
                return false;
            }
            binding.output.path(bone, templateBone.path());
            binding.output.decide(bone, templateBone.decision());
            binding.output.kinematics(bone, templateBone.kinematics());
            binding.boundCount++;
            for (AnimatedGeoBone child : bone.children()) {
                if (!bindBone(child, binding)) {
                    return false;
                }
            }
            return true;
        }

        private static final class Binding {
            private final PhysicsBoneSelectionPlan.Builder output;
            private int boundCount;

            private Binding(PhysicsBoneSelectionPlan.Builder output) {
                this.output = output;
            }
        }
    }

    private record TemplateBone(
            PhysicsBoneSelectionPlan.Decision decision,
            String path,
            BoneKinematics.Metrics kinematics
    ) {
    }
}
