package com.laixia.maidintelligence.feature.physics.client.metadata;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;

import java.util.Locale;

public final class ConstraintMetadataParser {
    private ConstraintMetadataParser() {
    }

    public static PhysicsBoneSelectionPlan.ConstraintProfile parse(
            JsonObject chain,
            PhysicsBoneSelectionPlan.PartType type,
            int schema,
            String origin,
            String chainId
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile defaults =
                PhysicsBoneSelectionPlan.ConstraintProfile.defaults(type);
        JsonObject constraints = chain.getAsJsonObject("constraints");
        if (constraints == null) {
            constraints = chain.getAsJsonObject("constraint");
        }
        if (constraints == null) {
            if (schema < 2) {
                return PhysicsBoneSelectionPlan.ConstraintProfile.legacy();
            }
            return schema >= 3 ? defaults : withLegacyCollision(defaults);
        }

        PhysicsBoneSelectionPlan.SimulationSpace simulationSpace =
                parseSimulationSpace(
                        string(
                                constraints,
                                "simulation_space",
                                defaults.simulationSpace().name()
                        ),
                        defaults.simulationSpace()
                );
        PhysicsBoneSelectionPlan.CollisionProfile collision = schema >= 3
                ? CollisionMetadataParser.parse(
                        constraints,
                        origin,
                        chainId
                )
                : PhysicsBoneSelectionPlan.CollisionProfile
                        .legacyAutomatic();
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                simulationSpace,
                number(
                        constraints,
                        "rotation_inertia_scale",
                        defaults.rotationInertiaScale(),
                        0.0F,
                        1.0F
                ),
                parseSwingLimits(
                        constraints.getAsJsonObject("swing_limits"),
                        defaults.swingLimits()
                ),
                bool(constraints, "backstop", defaults.backstop()),
                bool(
                        constraints,
                        "head_collision",
                        defaults.headCollision()
                ),
                number(
                        constraints,
                        "hit_radius_scale",
                        defaults.hitRadiusScale(),
                        0.0F,
                        4.0F
                ),
                collision,
                true
        );
    }

    private static PhysicsBoneSelectionPlan.ConstraintProfile
    withLegacyCollision(
            PhysicsBoneSelectionPlan.ConstraintProfile profile
    ) {
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                profile.simulationSpace(),
                profile.rotationInertiaScale(),
                profile.swingLimits(),
                profile.backstop(),
                profile.headCollision(),
                profile.hitRadiusScale(),
                PhysicsBoneSelectionPlan.CollisionProfile.legacyAutomatic(),
                profile.enabled()
        );
    }

    private static PhysicsBoneSelectionPlan.SwingLimits parseSwingLimits(
            JsonObject limits,
            PhysicsBoneSelectionPlan.SwingLimits defaults
    ) {
        if (limits == null) {
            return defaults;
        }
        return new PhysicsBoneSelectionPlan.SwingLimits(
                angle(limits, "left_degrees", defaults.left()),
                angle(limits, "right_degrees", defaults.right()),
                angle(limits, "outward_degrees", defaults.outward()),
                angle(limits, "inward_degrees", defaults.inward())
        );
    }

    private static PhysicsBoneSelectionPlan.SimulationSpace
    parseSimulationSpace(
            String value,
            PhysicsBoneSelectionPlan.SimulationSpace fallback
    ) {
        try {
            return PhysicsBoneSelectionPlan.SimulationSpace.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static float angle(
            JsonObject object,
            String key,
            float fallbackRadians
    ) {
        float degrees = number(
                object,
                key,
                (float) Math.toDegrees(fallbackRadians),
                0.0F,
                89.0F
        );
        return (float) Math.toRadians(degrees);
    }

    private static float number(
            JsonObject object,
            String key,
            float fallback,
            float minimum,
            float maximum
    ) {
        JsonElement element = object.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String string(
            JsonObject object,
            String key,
            String fallback
    ) {
        JsonElement element = object.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static boolean bool(
            JsonObject object,
            String key,
            boolean fallback
    ) {
        JsonElement element = object.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }
}
