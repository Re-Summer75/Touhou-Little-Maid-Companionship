package com.laixia.maidintelligence.feature.physics.client.metadata;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class CollisionMetadataParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CollisionMetadataParser() {
    }

    static PhysicsBoneSelectionPlan.CollisionProfile parse(
            JsonObject constraints,
            String origin,
            String chainId
    ) {
        JsonElement element = constraints.get("collision");
        if (element == null) {
            return PhysicsBoneSelectionPlan.CollisionProfile.defaults();
        }
        if (!element.isJsonObject()) {
            warnProfile(origin, chainId, "collision must be an object");
            return PhysicsBoneSelectionPlan.CollisionProfile.defaults();
        }

        JsonObject collision = element.getAsJsonObject();
        boolean automatic = bool(collision, "auto", true);
        JsonElement proxiesElement = collision.get("proxies");
        if (proxiesElement == null) {
            return new PhysicsBoneSelectionPlan.CollisionProfile(automatic, List.of());
        }
        if (!proxiesElement.isJsonArray()) {
            warnProfile(origin, chainId, "proxies must be an array");
            return new PhysicsBoneSelectionPlan.CollisionProfile(automatic, List.of());
        }

        List<PhysicsBoneSelectionPlan.CollisionProxySpec> proxies =
                new ArrayList<>();
        JsonArray array = proxiesElement.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            try {
                proxies.add(parseProxy(array.get(index)));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Skipping invalid collision proxy {} in physics metadata {} chain {}: {}",
                        index,
                        origin,
                        chainId,
                        exception.getMessage()
                );
            }
        }
        return new PhysicsBoneSelectionPlan.CollisionProfile(automatic, proxies);
    }

    private static PhysicsBoneSelectionPlan.CollisionProxySpec parseProxy(
            JsonElement element
    ) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("proxy must be an object");
        }
        JsonObject proxy = element.getAsJsonObject();
        String kind = requiredString(proxy, "kind")
                .toLowerCase(Locale.ROOT);
        String reference = requiredString(proxy, "reference");
        PhysicsBoneSelectionPlan.CollisionShape shape = switch (kind) {
            case "plane" -> new PhysicsBoneSelectionPlan.CollisionShape.Plane(
                    vector(proxy, "point"),
                    vector(proxy, "normal")
            );
            case "sphere" -> new PhysicsBoneSelectionPlan.CollisionShape.Sphere(
                    vector(proxy, "center"),
                    radius(proxy, "radius")
            );
            case "capsule" ->
                    new PhysicsBoneSelectionPlan.CollisionShape.Capsule(
                            vector(proxy, "start"),
                            vector(proxy, "end"),
                            radius(proxy, "radius")
                    );
            default -> throw new IllegalArgumentException(
                    "unknown kind '" + kind + "'"
            );
        };
        return new PhysicsBoneSelectionPlan.CollisionProxySpec(
                reference,
                shape,
                optionalRadius(proxy, "hit_radius")
        );
    }

    private static PhysicsBoneSelectionPlan.CollisionVector vector(
            JsonObject object,
            String key
    ) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be a vector");
        }
        JsonArray values = element.getAsJsonArray();
        if (values.size() != 3) {
            throw new IllegalArgumentException(
                    key + " must contain exactly three numbers"
            );
        }
        return new PhysicsBoneSelectionPlan.CollisionVector(
                finiteNumber(values.get(0), key),
                finiteNumber(values.get(1), key),
                finiteNumber(values.get(2), key)
        );
    }

    private static float radius(JsonObject object, String key) {
        JsonElement element = object.get(key);
        float value = finiteNumber(element, key);
        if (value < 0.0F) {
            throw new IllegalArgumentException(key + " must be non-negative");
        }
        return value;
    }

    private static Optional<Float> optionalRadius(
            JsonObject object,
            String key
    ) {
        return object.has(key)
                ? Optional.of(radius(object, key))
                : Optional.empty();
    }

    private static float finiteNumber(JsonElement element, String key) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be numeric");
        }
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(key + " must be finite");
        }
        return value;
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static boolean bool(
            JsonObject object,
            String key,
            boolean fallback
    ) {
        JsonElement element = object.get(key);
        return element != null
                && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean()
                : fallback;
    }

    private static void warnProfile(
            String origin,
            String chainId,
            String reason
    ) {
        LOGGER.warn(
                "Invalid collision profile in physics metadata {} chain {}: {}",
                origin,
                chainId,
                reason
        );
    }
}
