package com.laixia.maidintelligence.feature.physics.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Optional per-model authoring data. Names/paths here are explicit references,
 * not naming conventions: an author can call a bone anything and bind it
 * exactly once in the sidecar.
 */
public final class PhysicsMetadata {
    public static final PhysicsMetadata EMPTY = new PhysicsMetadata(
            Mode.AUTO,
            List.of(),
            Set.of(),
            "none"
    );

    private final Mode mode;
    private final List<Chain> chains;
    private final Set<String> excludes;
    private final String origin;

    private PhysicsMetadata(
            Mode mode,
            List<Chain> chains,
            Set<String> excludes,
            String origin
    ) {
        this.mode = mode;
        this.chains = List.copyOf(chains);
        this.excludes = Set.copyOf(excludes);
        this.origin = origin;
    }

    public Mode mode() {
        return mode;
    }

    public List<Chain> chains() {
        return chains;
    }

    public Set<String> excludes() {
        return excludes;
    }

    public String origin() {
        return origin;
    }

    public boolean isPresent() {
        return this != EMPTY && (!chains.isEmpty() || !excludes.isEmpty() || mode == Mode.EXPLICIT);
    }

    static PhysicsMetadata parse(JsonObject root, String origin) {
        int schema = integer(root, "schema_version", 1);
        if (schema != 1 && schema != 2) {
            throw new IllegalArgumentException("Unsupported physics schema_version " + schema);
        }

        Mode mode = Mode.parse(string(root, "mode", "auto"));
        Set<String> excludes = new LinkedHashSet<>();
        addStrings(root.get("exclude"), excludes);
        addStrings(root.get("exclude_bones"), excludes);

        List<Chain> chains = new ArrayList<>();
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
                boolean includeDescendants = bool(chain, "include_descendants", true);
                PhysicsBoneSelectionPlan.SpringProfile profile =
                        parseProfile(chain.getAsJsonObject("profile"));
                PhysicsBoneSelectionPlan.ConstraintProfile constraints =
                        parseConstraints(chain, type, schema);
                chains.add(new Chain(
                        id,
                        type,
                        List.copyOf(roots),
                        List.copyOf(bones),
                        Set.copyOf(chainExcludes),
                        includeDescendants,
                        profile,
                        constraints
                ));
                index++;
            }
        }
        return new PhysicsMetadata(mode, chains, excludes, origin);
    }

    private static PhysicsBoneSelectionPlan.SpringProfile parseProfile(JsonObject profile) {
        if (profile == null) {
            return PhysicsBoneSelectionPlan.SpringProfile.identity();
        }
        return new PhysicsBoneSelectionPlan.SpringProfile(
                scale(profile, "stiffness_scale"),
                scale(profile, "gravity_scale"),
                scale(profile, "drag_scale"),
                scale(profile, "inertia_scale"),
                scale(profile, "turn_scale"),
                scale(profile, "angle_scale"),
                scale(profile, "tip_displacement_scale")
        );
    }

    private static PhysicsBoneSelectionPlan.ConstraintProfile parseConstraints(
            JsonObject chain,
            PhysicsBoneSelectionPlan.PartType type,
            int schema
    ) {
        PhysicsBoneSelectionPlan.ConstraintProfile defaults =
                PhysicsBoneSelectionPlan.ConstraintProfile.defaults(type);
        JsonObject constraints = chain.getAsJsonObject("constraints");
        if (constraints == null) {
            constraints = chain.getAsJsonObject("constraint");
        }
        if (constraints == null) {
            return schema >= 2
                    ? defaults
                    : PhysicsBoneSelectionPlan.ConstraintProfile.legacy();
        }

        PhysicsBoneSelectionPlan.SimulationSpace simulationSpace =
                PhysicsBoneSelectionPlan.SimulationSpace.parse(
                        string(
                                constraints,
                                "simulation_space",
                                defaults.simulationSpace().name()
                        ),
                        defaults.simulationSpace()
                );
        float rotationInertia = number(
                constraints,
                "rotation_inertia_scale",
                defaults.rotationInertiaScale(),
                0.0F,
                1.0F
        );
        PhysicsBoneSelectionPlan.SwingLimits swingLimits =
                parseSwingLimits(
                        constraints.getAsJsonObject("swing_limits"),
                        defaults.swingLimits()
                );
        return new PhysicsBoneSelectionPlan.ConstraintProfile(
                simulationSpace,
                rotationInertia,
                swingLimits,
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
                true
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
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        return element.getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static void addString(JsonElement element, Set<String> output) {
        if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString().trim();
            if (!value.isEmpty()) {
                output.add(value);
            }
        }
    }

    private static void addStrings(JsonElement element, Set<String> output) {
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

    public enum Mode {
        AUTO,
        EXPLICIT;

        private static Mode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AUTO;
            }
        }
    }

    public record Chain(
            String id,
            PhysicsBoneSelectionPlan.PartType type,
            List<String> roots,
            List<String> bones,
            Set<String> excludes,
            boolean includeDescendants,
            PhysicsBoneSelectionPlan.SpringProfile profile,
            PhysicsBoneSelectionPlan.ConstraintProfile constraints
    ) {
    }
}
