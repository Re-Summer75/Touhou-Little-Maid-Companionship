package com.laixia.maidintelligence.feature.orchestration.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentDefinition;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reaches the shipped intent data pack from a verification.
 *
 * <p>Shared because two suites now read it and the directory is easy to get
 * subtly wrong: the data pack ships with {@code :features:ai:behavior} rather
 * than with this distribution, so a new version target inherits it instead of
 * copying it, and the path out of {@code distribution/<loader>-<mc>} is two
 * levels of {@code ..} that read as a mistake until you know why.
 *
 * <p>Names are discovered, never listed. A hardcoded list means a new intent
 * file ships untested by the suites that judge the catalog as a whole, and
 * nothing about adding the file would reveal that.
 */
final class BundledIntentResources {
    private static final String RESOURCE_ROOT = "data/tlm_companionship/";

    private BundledIntentResources() {
    }

    /** Every bundled intent, by file name without the extension. */
    static List<String> intentNames() throws IOException {
        return names(MaidIntentReloadListener.INTENT_PREFIX);
    }

    /** Every bundled plan, by file name without the extension. */
    static List<String> planNames() throws IOException {
        return names(MaidIntentReloadListener.PLAN_PREFIX);
    }

    static IntentDefinition intent(String name) throws IOException {
        OrchestrationId id =
                new OrchestrationId("tlm_companionship", name);
        return IntentDefinitionCodec.parse(
                id,
                json(MaidIntentReloadListener.INTENT_PREFIX + "/" + name
                        + ".json")
        ).result().orElseThrow(() ->
                new AssertionError("Failed to parse intent " + name));
    }

    /** Raw json for one path under the mod's data root. */
    static JsonElement json(String path) throws IOException {
        String fullPath = RESOURCE_ROOT + path;
        InputStream stream = BundledIntentResources.class
                .getClassLoader()
                .getResourceAsStream(fullPath);
        if (stream == null) {
            throw new IOException("Missing classpath resource " + fullPath);
        }
        try (InputStreamReader reader = new InputStreamReader(
                stream,
                StandardCharsets.UTF_8
        )) {
            return JsonParser.parseReader(reader);
        }
    }

    private static List<String> names(String prefix) throws IOException {
        Path directory = Path.of(
                "..", "..", "features", "ai", "behavior",
                "src", "main", "resources"
        ).resolve(RESOURCE_ROOT + prefix);
        if (!Files.isDirectory(directory)) {
            throw new IOException("Missing resource directory " + directory);
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .map(entry -> entry.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted()
                    .toList();
        }
    }
}
