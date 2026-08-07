package com.laixia.maidintelligence.feature.physics.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gson-facing codec for the versioned physics sidecar.
 */
public final class PhysicsMetadataJsonParser {
    private PhysicsMetadataJsonParser() {
    }

    public static PhysicsMetadata parse(JsonObject root, String origin) {
        int schema = integer(root, "schema_version", 1);
        if (schema != 1 && schema != 2 && schema != 3) {
            throw new IllegalArgumentException(
                    "Unsupported physics schema_version " + schema
            );
        }

        PhysicsMetadata.Mode mode = parseMode(string(root, "mode", "auto"));
        Set<String> excludes = new LinkedHashSet<>();
        addStrings(root.get("exclude"), excludes);
        addStrings(root.get("exclude_bones"), excludes);

        List<PhysicsMetadata.Chain> chains = new ArrayList<>();
        JsonElement chainsElement = root.get("chains");
        if (chainsElement != null && chainsElement.isJsonArray()) {
            int index = 0;
            for (JsonElement element : chainsElement.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    index++;
                    continue;
                }
                JsonObject chain = element.getAsJsonObject();
                String id = string(chain, "id", "chain_" + index);
                PhysicsBoneSelectionPlan.PartType type =
                        PhysicsBoneSelectionPlan.PartType.parse(
                                string(chain, "type", "GENERIC"),
                                PhysicsBoneSelectionPlan.PartType.GENERIC
                        );
                Set<String> roots = new LinkedHashSet<>();
                addString(chain.get("root"), roots);
                addStrings(chain.get("roots"), roots);
                Set<String> bones = new LinkedHashSet<>();
                addStrings(chain.get("bones"), bones);
                addStrings(chain.get("include_bones"), bones);
                Set<String> chainExcludes = new LinkedHashSet<>();
                addStrings(chain.get("exclude"), chainExcludes);
                addStrings(chain.get("exclude_bones"), chainExcludes);
                chains.add(new PhysicsMetadata.Chain(
                        id,
                        type,
                        List.copyOf(roots),
                        List.copyOf(bones),
                        Set.copyOf(chainExcludes),
                        bool(chain, "include_descendants", true),
                        parseProfile(chain.getAsJsonObject("profile")),
                        ConstraintMetadataParser.parse(
                                chain,
                                type,
                                schema,
                                origin,
                                id
                        )
                ));
                index++;
            }
        }
        return PhysicsMetadata.of(mode, chains, excludes, origin);
    }

    private static PhysicsMetadata.Mode parseMode(String value) {
        try {
            return PhysicsMetadata.Mode.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException ignored) {
            return PhysicsMetadata.Mode.AUTO;
        }
    }

    private static PhysicsBoneSelectionPlan.SpringProfile parseProfile(
            JsonObject profile
    ) {
        if (profile == null) {
            return PhysicsBoneSelectionPlan.SpringProfile.identity();
        }
        return new PhysicsBoneSelectionPlan.SpringProfile(
                scale(profile, "stiffness_scale"),
                scale(profile, "gravity_scale"),
                scale(profile, "wind_scale"),
                number(profile, "mass_scale", 1.0F, 0.25F, 4.0F),
                scale(profile, "drag_scale"),
                scale(profile, "inertia_scale"),
                scale(profile, "turn_scale"),
                scale(profile, "angle_scale"),
                scale(profile, "tip_displacement_scale")
        );
    }

    private static float scale(JsonObject object, String key) {
        return number(object, key, 1.0F, 0.0F, 4.0F);
    }

    private static float number(
            JsonObject object,
            String key,
            float fallback,
            float minimum,
            float maximum
    ) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        float value = element.getAsFloat();
        return Float.isFinite(value)
                ? Math.max(minimum, Math.min(maximum, value))
                : fallback;
    }

    private static String string(
            JsonObject object,
            String key,
            String fallback
    ) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : fallback;
    }

    private static int integer(
            JsonObject object,
            String key,
            int fallback
    ) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt()
                : fallback;
    }

    private static boolean bool(
            JsonObject object,
            String key,
            boolean fallback
    ) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean()
                : fallback;
    }

    private static void addString(
            JsonElement element,
            Set<String> output
    ) {
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return;
        }
        String value = element.getAsString().trim();
        if (!value.isEmpty()) {
            output.add(value);
        }
    }

    private static void addStrings(
            JsonElement element,
            Set<String> output
    ) {
        if (element == null) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                addString(item, output);
            }
        } else {
            addString(element, output);
        }
    }
}
