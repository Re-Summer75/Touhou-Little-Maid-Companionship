package com.laixia.maidintelligence.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Mixins have to still find their targets once the game is obfuscated.
 *
 * <p>Development and the game a player runs disagree about what the vanilla
 * members are called. Everything written here uses the readable names; the
 * shipped game knows them as {@code m_27595_} and friends. Two mechanisms
 * bridge that gap, and a mixin that uses neither works perfectly in every test
 * we have and fails for every player — which is exactly what happened on
 * 2026-08-25: maids stopped working, vanished when put away and taken back
 * out, and the frame rate collapsed under one exception per tick, all from
 * injections that resolved here and nowhere else.
 *
 * <p>The first mechanism is the refmap: the compiler records every remapped
 * reference, but a config only consults that table if it names it. The second
 * is spelling the obfuscated name out in the selector, which is what a mixin
 * has to do when it opts out of remapping yet still points at something
 * vanilla. This suite states both rules against the artifacts the build
 * actually produces — the generated refmap and the game's own mapping table —
 * so the mismatch is caught at the only moment it is cheap to fix.
 */
public final class MixinProductionNamesVerification {
    /** Where the mixin annotation processor leaves its mapping table. */
    private static final Path GENERATED_REFMAP = Path.of(
            "build", "tmp", "compileJava", "tlm_companionship.refmap.json"
    );

    /** The game's readable-to-obfuscated table, as the build resolves it. */
    private static final Path GAME_MAPPINGS = Path.of(
            "build", "createMcpToSrg", "output.tsrg"
    );

    private static final Path CONFIG_DIRECTORY =
            Path.of("src", "main", "resources");

    private static final String CONFIG_SUFFIX = ".mixins.json";

    private MixinProductionNamesVerification() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> configs = findConfigs();
        require(!configs.isEmpty(), "No mixin configs under " + CONFIG_DIRECTORY);
        Map<String, JsonObject> refmap = readGeneratedMappings();
        VanillaNames vanilla = readVanillaNames();
        verifiesConfigsThatNeedMappingsLoadThem(configs, refmap);
        verifiesDeclaredRefmapsPointAtTheGeneratedOne(configs);
        verifiesUnmappedSelectorsSurviveObfuscation(configs, vanilla);
        MixinInjectionPoints.verify(configs, vanilla);
        System.out.println("Mixin production names verification passed.");
    }

    /** A config whose mixins produced mappings must name the refmap. */
    private static void verifiesConfigsThatNeedMappingsLoadThem(
            List<Path> configs,
            Map<String, JsonObject> refmap
    ) throws IOException {
        for (Path config : configs) {
            JsonObject json = read(config);
            if (json.has("refmap")) {
                continue;
            }
            Map<String, JsonObject> needed = new TreeMap<>();
            for (String mixin : mixinClassNames(json)) {
                JsonObject entries = refmap.get(mixin.replace('.', '/'));
                if (entries != null && entries.size() > 0) {
                    needed.put(mixin, entries);
                }
            }
            if (needed.isEmpty()) {
                continue;
            }
            Map.Entry<String, JsonObject> offender =
                    needed.entrySet().iterator().next();
            Map.Entry<String, JsonElement> sample =
                    offender.getValue().entrySet().iterator().next();
            throw new AssertionError(
                    config.getFileName() + " declares no refmap while "
                            + needed.size() + " of its mixins need one — "
                            + offender.getKey() + " maps " + sample.getKey()
                            + " to " + sample.getValue().getAsString()
                            + ". Unmapped, that lookup uses the readable name "
                            + "against an obfuscated game, the injection fails "
                            + "its check, and a required config takes the "
                            + "target class down with it. Add \"refmap\": \""
                            + GENERATED_REFMAP.getFileName() + "\"."
            );
        }
    }

    /** A declared refmap has to be the one the compiler writes. */
    private static void verifiesDeclaredRefmapsPointAtTheGeneratedOne(
            List<Path> configs
    ) throws IOException {
        String expected = GENERATED_REFMAP.getFileName().toString();
        for (Path config : configs) {
            JsonObject json = read(config);
            if (!json.has("refmap")) {
                continue;
            }
            String declared = json.get("refmap").getAsString();
            require(
                    expected.equals(declared),
                    config.getFileName() + " names refmap '" + declared
                            + "' but the build writes '" + expected
                            + "' — those mappings would never be found"
            );
        }
    }

    /**
     * A selector that opts out of remapping must name something that keeps its
     * name — or spell the obfuscated one out beside it.
     *
     * <p>A bare name that a vanilla class also uses is the dangerous shape: an
     * override of it is renamed on the way out, and the selector then matches
     * nothing. A selector carrying a descriptor is only at risk when that exact
     * signature is vanilla's; when it differs — a task overriding
     * {@code start(ServerLevel, EntityMaid, long)} against vanilla's
     * {@code (ServerLevel, LivingEntity, long)} — the name survives untouched
     * and the selector is fine.
     */
    private static void verifiesUnmappedSelectorsSurviveObfuscation(
            List<Path> configs,
            VanillaNames vanilla
    ) throws Exception {
        for (Path config : configs) {
            JsonObject json = read(config);
            for (String mixinName : mixinClassNames(json)) {
                Class<?> mixin = Class.forName(
                        mixinName,
                        false,
                        MixinProductionNamesVerification.class.getClassLoader()
                );
                boolean classRemaps = remapFlag(mixin.getAnnotations(), true);
                List<MixinTargets.Target> targets =
                        MixinTargets.of(mixinName);
                for (Method method : mixin.getDeclaredMethods()) {
                    for (Annotation annotation : method.getAnnotations()) {
                        String[] selectors = selectorsOf(annotation);
                        if (selectors == null) {
                            continue;
                        }
                        boolean remapped = classRemaps && remapFlag(
                                new Annotation[]{annotation}, true);
                        if (remapped) {
                            // Remapped selectors are the refmap's business.
                            continue;
                        }
                        checkSelectors(
                                config,
                                mixinName,
                                method.getName(),
                                selectors,
                                targets,
                                vanilla
                        );
                    }
                }
            }
        }
    }

    private static void checkSelectors(
            Path config,
            String mixinName,
            String handler,
            String[] selectors,
            List<MixinTargets.Target> targets,
            VanillaNames vanilla
    ) {
        Set<String> spelled = new LinkedHashSet<>(List.of(selectors));
        // A selector may carry a descriptor, so judge the name half of it.
        boolean hasObfuscated = spelled.stream()
                .map(MixinProductionNamesVerification::nameOf)
                .anyMatch(MixinProductionNamesVerification::looksObfuscated);
        for (String selector : selectors) {
            int parenthesis = selector.indexOf('(');
            String name = nameOf(selector);
            String descriptor = parenthesis < 0
                    ? null
                    : selector.substring(parenthesis);
            if (looksObfuscated(name)) {
                continue;
            }
            Set<String> obfuscated = descriptor == null
                    ? vanilla.byName(name)
                    : vanilla.byNameAndDescriptor(name, descriptor);
            if (obfuscated.isEmpty() || hasObfuscated) {
                continue;
            }
            // Sharing a name with something vanilla is not the problem; only
            // an override is, because only an override gets renamed. A host
            // method that merely reads like one — EntityMaid#refreshBrain
            // against Villager's — keeps its name and its selector.
            if (!inheritsFromVanilla(targets, name, descriptor)) {
                continue;
            }
            throw new AssertionError(
                    config.getFileName() + ":" + simpleName(mixinName)
                            + "#" + handler + " selects '" + selector
                            + "' without remapping, but vanilla renames that "
                            + "member to " + obfuscated
                            + " in the game players run, so an override of it "
                            + "carries the new name and the selector matches "
                            + "nothing. List the obfuscated name beside it, "
                            + "e.g. method = {\"" + name + "\", \""
                            + obfuscated.iterator().next() + "\"}."
            );
        }
    }

    /**
     * Whether a target class inherits this member from vanilla, which is what
     * makes an override of it carry the obfuscated name.
     */
    private static boolean inheritsFromVanilla(
            List<MixinTargets.Target> targets,
            String name,
            String descriptor
    ) {
        for (MixinTargets.Target target : targets) {
            if (target.type() == null) {
                // Unprovable here, so assume the strict answer.
                return true;
            }
            for (Class<?> type = target.type();
                 type != null;
                 type = type.getSuperclass()) {
                if (!type.getName().startsWith("net.minecraft.")) {
                    continue;
                }
                for (Method declared : type.getDeclaredMethods()) {
                    if (!declared.getName().equals(name)) {
                        continue;
                    }
                    if (descriptor == null
                            || descriptor.equals(descriptorOf(declared))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** JVM descriptor of a method, as a selector spells it. */
    private static String descriptorOf(Method method) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            descriptor.append(typeDescriptor(parameter));
        }
        return descriptor.append(')')
                .append(typeDescriptor(method.getReturnType()))
                .toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) {
            return "[" + typeDescriptor(type.getComponentType());
        }
        if (!type.isPrimitive()) {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        return switch (type.getName()) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            default -> "D";
        };
    }

    /** {@code method()} of any injector annotation, or null if it has none. */
    private static String[] selectorsOf(Annotation annotation) {
        try {
            Method selector = annotation.annotationType()
                    .getDeclaredMethod("method");
            annotation.annotationType().getDeclaredMethod("remap");
            Object value = selector.invoke(annotation);
            return value instanceof String[] strings ? strings : null;
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    static boolean remapFlag(
            Annotation[] annotations,
            boolean fallback
    ) {
        for (Annotation annotation : annotations) {
            try {
                Method remap = annotation.annotationType()
                        .getDeclaredMethod("remap");
                return (Boolean) remap.invoke(annotation);
            } catch (ReflectiveOperationException absent) {
                // Not an annotation that carries the flag; keep looking.
            }
        }
        return fallback;
    }

    /** The name half of a selector, dropping any descriptor. */
    static String nameOf(String selector) {
        int parenthesis = selector.indexOf('(');
        return parenthesis < 0 ? selector : selector.substring(0, parenthesis);
    }

    static boolean looksObfuscated(String name) {
        return name.startsWith("m_") && name.endsWith("_")
                || name.startsWith("f_") && name.endsWith("_");
    }

    static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    static Set<String> mixinClassNames(JsonObject config) {
        String packageName = config.has("package")
                ? config.get("package").getAsString()
                : "";
        Set<String> names = new LinkedHashSet<>();
        for (String side : new String[]{"mixins", "client", "server"}) {
            if (!config.has(side)) {
                continue;
            }
            JsonArray entries = config.getAsJsonArray(side);
            for (JsonElement entry : entries) {
                names.add(packageName + "." + entry.getAsString());
            }
        }
        return names;
    }

    private static Map<String, JsonObject> readGeneratedMappings()
            throws IOException {
        require(
                Files.isRegularFile(GENERATED_REFMAP),
                "No generated refmap at " + GENERATED_REFMAP.toAbsolutePath()
                        + " — compile the distribution first; this check reads "
                        + "what the annotation processor actually produced"
        );
        JsonObject refmap = read(GENERATED_REFMAP);
        Map<String, JsonObject> mappings = new TreeMap<>();
        if (refmap.has("mappings")) {
            JsonObject entries = refmap.getAsJsonObject("mappings");
            for (String mixin : entries.keySet()) {
                mappings.put(mixin, entries.getAsJsonObject(mixin));
            }
        }
        return mappings;
    }

    /** Vanilla methods that get renamed, from the build's own table. */
    private static VanillaNames readVanillaNames() throws IOException {
        require(
                Files.isRegularFile(GAME_MAPPINGS),
                "No mapping table at " + GAME_MAPPINGS.toAbsolutePath()
                        + " — compile the distribution first"
        );
        Map<String, Set<String>> byName = new TreeMap<>();
        Map<String, Set<String>> byNameAndDescriptor = new TreeMap<>();
        try (Stream<String> lines = Files.lines(
                GAME_MAPPINGS, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (!line.startsWith("\t") || line.startsWith("\t\t")) {
                    return;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 3 || !parts[1].startsWith("(")) {
                    return;
                }
                if (!looksObfuscated(parts[2])) {
                    return;
                }
                byName.computeIfAbsent(parts[0], key -> new TreeSet<>())
                        .add(parts[2]);
                byNameAndDescriptor
                        .computeIfAbsent(
                                parts[0] + parts[1], key -> new TreeSet<>())
                        .add(parts[2]);
            });
        }
        return new VanillaNames(byName, byNameAndDescriptor);
    }

    record VanillaNames(
            Map<String, Set<String>> byName,
            Map<String, Set<String>> byNameAndDescriptor
    ) {
        Set<String> byName(String name) {
            return byName.getOrDefault(name, Set.of());
        }

        Set<String> byNameAndDescriptor(String name, String descriptor) {
            return byNameAndDescriptor.getOrDefault(
                    name + descriptor, Set.of());
        }
    }

    private static List<Path> findConfigs() throws IOException {
        require(
                Files.isDirectory(CONFIG_DIRECTORY),
                "Run from the distribution project directory; "
                        + CONFIG_DIRECTORY.toAbsolutePath() + " is missing"
        );
        List<Path> configs = new ArrayList<>();
        try (Stream<Path> files = Files.list(CONFIG_DIRECTORY)) {
            files.filter(path -> path.getFileName().toString()
                            .endsWith(CONFIG_SUFFIX))
                    .sorted()
                    .forEach(configs::add);
        }
        return configs;
    }

    static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static void require(boolean condition, String complaint) {
        if (!condition) {
            throw new AssertionError(complaint);
        }
    }
}
