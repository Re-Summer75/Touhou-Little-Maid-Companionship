package com.laixia.maidintelligence.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which classes a mixin is applied to, read from its source.
 *
 * <p>Reflection cannot answer this: {@code @Mixin} is kept only to the class
 * file, not to runtime, so asking a loaded mixin for its annotations returns
 * nothing at all. A check built on that answer does not report a problem — it
 * silently stops looking, which is worse than the bug it was written to catch,
 * and is exactly how the first draft of {@link MixinProductionNamesVerification}
 * passed a defect it was staring at. So the target is read from the one place
 * that always states it plainly, and anything unreadable is an error rather
 * than an empty answer.
 */
final class MixinTargets {
    private static final Pattern DECLARATION =
            Pattern.compile("@Mixin\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    private static final Pattern TARGET =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*)\\.class");

    /** {@code targets = "a.b.C"} — for a class a compat mod may not ship. */
    private static final Pattern NAMED_TARGET =
            Pattern.compile("\"([A-Za-z_][A-Za-z0-9_.$]*)\"");

    private MixinTargets() {
    }

    /**
     * One class a mixin is applied to. {@code type} is null when the class
     * cannot be loaded outside the game — some host classes override methods
     * that are only non-final once the loader has patched them, and linking
     * them here throws. Callers treat that as "cannot prove it is safe".
     */
    record Target(String name, Class<?> type) {
    }

    /** Classes the named mixin is applied to; never empty. */
    static List<Target> of(String mixinClassName) throws IOException {
        Path source = sourceOf(mixinClassName);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        Matcher declaration = DECLARATION.matcher(text);
        require(
                declaration.find(),
                "No @Mixin declaration in " + source
        );
        Set<String> names = new LinkedHashSet<>();
        Matcher target = TARGET.matcher(declaration.group(1));
        while (target.find()) {
            names.add(target.group(1));
        }
        Matcher named = NAMED_TARGET.matcher(declaration.group(1));
        while (named.find()) {
            names.add(named.group(1));
        }
        require(
                !names.isEmpty(),
                "@Mixin in " + source + " names no target class"
        );
        List<Target> targets = new ArrayList<>();
        for (String name : names) {
            targets.add(resolve(name, text, source));
        }
        return targets;
    }

    private static Target resolve(String name, String text, Path source) {
        String qualified = name.indexOf('.') > 0
                ? name
                : importedName(name, text, source);
        try {
            return new Target(qualified, Class.forName(
                    qualified, false, MixinTargets.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError unavailable) {
            // Absent (an optional compat target) or loadable only inside the
            // game. Either way its selectors get judged the strict way.
            return new Target(qualified, null);
        }
    }

    private static String importedName(String name, String text, Path source) {
        Matcher imported = Pattern.compile(
                "^import\\s+(?:static\\s+)?([\\w.]*\\." + name + ");",
                Pattern.MULTILINE
        ).matcher(text);
        require(
                imported.find(),
                "No import for mixin target " + name + " in " + source
        );
        return imported.group(1);
    }

    private static Path sourceOf(String mixinClassName) throws IOException {
        String relative = mixinClassName.replace('.', '/') + ".java";
        for (Path root : sourceRoots()) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError(
                "No source found for mixin " + mixinClassName
                        + " under " + sourceRoots()
        );
    }

    /** Every place a mixin of this distribution can be written. */
    private static List<Path> sourceRoots() throws IOException {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of("src", "main", "java"));
        Path adapters = Path.of("..", "..", "adapters");
        if (Files.isDirectory(adapters)) {
            try (Stream<Path> entries = Files.list(adapters)) {
                entries.sorted()
                        .map(entry -> entry.resolve(
                                Path.of("src", "main", "java")))
                        .filter(Files::isDirectory)
                        .forEach(roots::add);
            }
        }
        return roots;
    }

    private static void require(boolean condition, String complaint) {
        if (!condition) {
            throw new AssertionError(complaint);
        }
    }
}
