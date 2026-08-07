package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads optional model-authored spring-bone sidecars from both normal resource
 * packs and TLM's separate {@code tlm_custom_pack} folder.
 */
final class PhysicsMetadataLoader {
    static final String RESOURCE_PREFIX = "tlm_companionship/physics";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern PACK_ENTRY = Pattern.compile(
            "^assets/([^/]+)/" + RESOURCE_PREFIX + "/(.+)\\.json$"
    );
    private static volatile Map<ResourceLocation, PhysicsMetadata> metadata = Map.of();

    private PhysicsMetadataLoader() {
    }

    static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, PhysicsMetadata> loaded = new LinkedHashMap<>();
        loadResourcePacks(resourceManager, loaded);
        loadTlmCustomPacks(loaded);
        metadata = Map.copyOf(loaded);
        LOGGER.info("Loaded {} maid physics metadata sidecar(s)", loaded.size());
    }

    static PhysicsMetadata find(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return PhysicsMetadata.EMPTY;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(modelId);
        if (parsed == null) {
            return PhysicsMetadata.EMPTY;
        }
        return metadata.getOrDefault(parsed, PhysicsMetadata.EMPTY);
    }

    private static void loadResourcePacks(
            ResourceManager resourceManager,
            Map<ResourceLocation, PhysicsMetadata> output
    ) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                RESOURCE_PREFIX,
                location -> location.getPath().endsWith(".json")
        );
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation sidecar = entry.getKey();
                    String prefix = RESOURCE_PREFIX + "/";
                    String path = sidecar.getPath();
                    if (!path.startsWith(prefix) || !path.endsWith(".json")) {
                        return;
                    }
                    ResourceLocation modelId = ResourceLocation.tryBuild(
                            sidecar.getNamespace(),
                            path.substring(prefix.length(), path.length() - ".json".length())
                    );
                    if (modelId == null) {
                        LOGGER.warn("Invalid model id derived from physics metadata {}", sidecar);
                        return;
                    }
                    try (InputStream stream = entry.getValue().open()) {
                        loadOne(
                                modelId,
                                stream,
                                sidecar.toString(),
                                output
                        );
                    } catch (IOException exception) {
                        LOGGER.warn("Failed to read maid physics metadata {}", sidecar, exception);
                    }
                });
    }

    private static void loadTlmCustomPacks(Map<ResourceLocation, PhysicsMetadata> output) {
        Path folder = CustomPackLoader.PACK_FOLDER;
        if (!Files.isDirectory(folder)) {
            return;
        }
        File[] packs = folder.toFile().listFiles();
        if (packs == null) {
            LOGGER.warn("Failed to list TLM custom packs for physics metadata");
            return;
        }
        // Match CustomPackLoader's File.listFiles iteration so duplicate model
        // IDs resolve to the sidecar from the same pack as the loaded model.
        for (File packFile : packs) {
            Path pack = packFile.toPath();
            if (Files.isDirectory(pack)) {
                loadFolderPack(pack, output);
            } else if (pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                loadZipPack(pack, output);
            }
        }
    }

    private static void loadFolderPack(
            Path root,
            Map<ResourceLocation, PhysicsMetadata> output
    ) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(assets)) {
            stream.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> {
                        String entry = root.relativize(file)
                                .toString()
                                .replace('\\', '/');
                        Matcher matcher = PACK_ENTRY.matcher(entry);
                        if (!matcher.matches()) {
                            return;
                        }
                        ResourceLocation modelId = ResourceLocation.tryBuild(
                                matcher.group(1),
                                matcher.group(2)
                        );
                        if (modelId == null) {
                            LOGGER.warn("Invalid model id in physics metadata {}", file);
                            return;
                        }
                        try (InputStream input = Files.newInputStream(file)) {
                            loadOne(modelId, input, file.toString(), output);
                        } catch (IOException exception) {
                            LOGGER.warn(
                                    "Failed to read maid physics metadata {}",
                                    file,
                                    exception
                            );
                        }
                    });
        } catch (IOException exception) {
            LOGGER.warn("Failed to scan TLM custom pack {}", root, exception);
        }
    }

    private static void loadZipPack(
            Path file,
            Map<ResourceLocation, PhysicsMetadata> output
    ) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<? extends ZipEntry> entries = enumerationToList(zip.entries());
            entries.stream()
                    .filter(entry -> !entry.isDirectory())
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .forEach(entry -> {
                        Matcher matcher = PACK_ENTRY.matcher(entry.getName());
                        if (!matcher.matches()) {
                            return;
                        }
                        ResourceLocation modelId = ResourceLocation.tryBuild(
                                matcher.group(1),
                                matcher.group(2)
                        );
                        if (modelId == null) {
                            LOGGER.warn(
                                    "Invalid model id in physics metadata {} from {}",
                                    entry.getName(),
                                    file
                            );
                            return;
                        }
                        try (InputStream input = zip.getInputStream(entry)) {
                            loadOne(
                                    modelId,
                                    input,
                                    file + "!/" + entry.getName(),
                                    output
                            );
                        } catch (IOException exception) {
                            LOGGER.warn(
                                    "Failed to read maid physics metadata {} from {}",
                                    entry.getName(),
                                    file,
                                    exception
                            );
                        }
                    });
        } catch (IOException exception) {
            LOGGER.warn("Failed to inspect TLM custom pack {}", file, exception);
        }
    }

    private static List<? extends ZipEntry> enumerationToList(
            Enumeration<? extends ZipEntry> entries
    ) {
        List<ZipEntry> result = new ArrayList<>();
        while (entries.hasMoreElements()) {
            result.add(entries.nextElement());
        }
        return result;
    }

    private static void loadOne(
            ResourceLocation modelId,
            InputStream stream,
            String origin,
            Map<ResourceLocation, PhysicsMetadata> output
    ) {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("metadata root must be an object");
            }
            output.put(
                    modelId,
                    PhysicsMetadataJsonParser.parse(
                            parsed.getAsJsonObject(),
                            origin
                    )
            );
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Failed to parse maid physics metadata {} for model {}",
                    origin,
                    modelId,
                    exception
            );
        }
    }

}
