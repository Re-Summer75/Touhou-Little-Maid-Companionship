package com.laixia.maidintelligence.feature.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.advancement.application.layout.AdvancementStripLayout;
import com.laixia.maidintelligence.feature.advancement.application.layout.AdvancementTreeLayout;
import com.laixia.maidintelligence.feature.advancement.criterion.MaidCriteriaTriggers;
import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.MaidAdvancementScope;
import com.laixia.maidintelligence.feature.advancement.domain.MaidStatistics;
import com.laixia.maidintelligence.feature.advancement.domain.ResourceId;
import com.laixia.maidintelligence.feature.advancement.codec.MaidStatisticsCodec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.Criterion;
import net.minecraft.network.FriendlyByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 女仆进度系统里不依赖游戏运行时的部分：统计量编解码、内置进度数据包与语言键、
 * 进度网络包的字节流往返，以及 Tab 页里图标条与树视图的布局换算。
 */
public final class AdvancementDomainVerification {
    private static final Path ADVANCEMENTS =
            Path.of("src/main/resources/data/tlm_companionship/advancements/maid");
    // Translations ship with :shared:assets: their keys are all in the mod's
    // own namespace, so they do not change with the Minecraft version.
    private static final Path LANG = Path.of(
            "../../shared/assets/src/main/resources/assets/tlm_companionship/lang"
    );
    private static final String ROOT_ID = "tlm_companionship:maid/root";
    private static final ItemId CAKE =
            ItemId.of("minecraft", "cake");
    private static final ItemId BREAD =
            ItemId.of("minecraft", "bread");

    private AdvancementDomainVerification() {
    }

    public static void main(String[] args) throws IOException {
        verifiesStatisticsCodecRoundTrip();
        verifiesStatisticsAccumulation();
        verifiesLegacyLift();
        verifiesBundledAdvancementsFormATree();
        verifiesBundledAdvancementsUseRegisteredTriggers();
        verifiesBundledAdvancementsAreTranslated();
        verifiesBundledAdvancementsAreInScope();
        verifiesTouhouLittleMaidIsOutOfScope();
        verifiesProgressPacketRoundTrip();
        verifiesStripPaging();
        verifiesTreeScrolling();
        System.out.println("Advancement domain verification passed.");
    }

    private static void verifiesStatisticsCodecRoundTrip() {
        MaidStatistics expected = MaidStatistics.empty()
                .withFeed(CAKE)
                .withFeed(BREAD)
                .withFeed(CAKE)
                .withExperience(37);
        JsonElement encoded = MaidStatisticsCodec.CODEC.encodeStart(
                JsonOps.INSTANCE,
                expected
        )
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        MaidStatistics decoded = MaidStatisticsCodec.CODEC.parse(
                JsonOps.INSTANCE,
                encoded
        )
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        require(expected.equals(decoded), "Codec round trip changed maid statistics");

        MaidStatistics fresh = MaidStatisticsCodec.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{}")
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
        require(fresh.isEmpty(), "An empty statistics object should decode to the empty value");
    }

    private static void verifiesStatisticsAccumulation() {
        MaidStatistics fed = MaidStatistics.empty().withFeed(CAKE).withFeed(BREAD).withFeed(CAKE);
        require(fed.feedCount() == 3, "Total feed count is wrong: " + fed.feedCount());
        require(fed.feedCount(CAKE) == 2, "Per-item feed count is wrong: " + fed.feedCount(CAKE));
        require(fed.feedCount(BREAD) == 1, "Per-item feed count is wrong: " + fed.feedCount(BREAD));
        require(
                MaidStatistics.empty().withExperience(0).isEmpty(),
                "Gaining no experience should leave the statistics untouched"
        );
        require(
                fed.withExperience(10).withExperience(5).experienceGained() == 15,
                "Experience should accumulate"
        );
    }

    private static void verifiesLegacyLift() {
        MaidStatistics lifted = MaidStatistics.empty()
                .withFeed(CAKE)
                .withExperience(50)
                .atLeast(32, CAKE, 8, 1000);
        require(lifted.feedCount() == 32, "Migration should raise the feed count to the legacy goal");
        require(lifted.feedCount(CAKE) == 8, "Migration should raise the per-item count to the legacy goal");
        require(lifted.experienceGained() == 1000, "Migration should raise the experience to the legacy goal");

        MaidStatistics richer = MaidStatistics.empty()
                .withExperience(5000)
                .atLeast(1, CAKE, 1, 1000);
        require(
                richer.experienceGained() == 5000,
                "Migration must never lower a statistic that is already higher"
        );
    }

    private static void verifiesBundledAdvancementsFormATree() throws IOException {
        Map<String, JsonObject> advancements = bundledAdvancements();
        require(advancements.containsKey(ROOT_ID), "The maid advancement tab needs a root advancement");
        require(
                !advancements.get(ROOT_ID).has("parent"),
                "The root advancement must not have a parent"
        );
        for (Map.Entry<String, JsonObject> entry : advancements.entrySet()) {
            if (entry.getKey().equals(ROOT_ID)) {
                continue;
            }
            JsonObject advancement = entry.getValue();
            require(advancement.has("parent"), entry.getKey() + " has no parent and would open a second tab");
            String parent = advancement.get("parent").getAsString();
            require(
                    advancements.containsKey(parent),
                    entry.getKey() + " points at a missing parent " + parent
            );
            require(
                    advancement.has("display") && advancement.getAsJsonObject("display").has("icon"),
                    entry.getKey() + " has no icon and would render as a hole in the tree"
            );
        }
    }

    private static void verifiesBundledAdvancementsUseRegisteredTriggers() throws IOException {
        Set<String> registered = Stream.of(
                MaidCriteriaTriggers.MAID_FED,
                MaidCriteriaTriggers.MAID_LEVEL,
                MaidCriteriaTriggers.MAID_FAVORABILITY_LEVEL,
                MaidCriteriaTriggers.MAID_EXPERIENCE
        ).map(trigger -> trigger.getId().toString()).collect(java.util.stream.Collectors.toSet());
        Set<String> used = new TreeSet<>();
        for (Map.Entry<String, JsonObject> entry : bundledAdvancements().entrySet()) {
            JsonObject criteria = entry.getValue().getAsJsonObject("criteria");
            require(
                    criteria != null && !criteria.keySet().isEmpty(),
                    entry.getKey() + " has no criteria and could never complete"
            );
            for (String name : criteria.keySet()) {
                String trigger = criteria.getAsJsonObject(name).get("trigger").getAsString();
                require(
                        trigger.startsWith("tlm_companionship:"),
                        entry.getKey() + " uses a foreign trigger " + trigger
                );
                require(
                        registered.contains(trigger),
                        entry.getKey() + " uses " + trigger + ", which nothing registers"
                );
                used.add(trigger);
            }
        }
        require(
                used.equals(new TreeSet<>(registered)),
                "Every registered trigger should back at least one bundled advancement, missing: "
                        + difference(registered, used)
        );
    }

    private static void verifiesBundledAdvancementsAreTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (JsonObject advancement : bundledAdvancements().values()) {
            JsonObject display = advancement.getAsJsonObject("display");
            keys.add(display.getAsJsonObject("title").get("translate").getAsString());
            keys.add(display.getAsJsonObject("description").get("translate").getAsString());
        }
        for (String language : List.of("en_us", "zh_cn")) {
            JsonObject translations = readJson(LANG.resolve(language + ".json"));
            for (String key : keys) {
                require(translations.has(key), language + " is missing " + key);
            }
        }
    }

    private static void verifiesBundledAdvancementsAreInScope() throws IOException {
        for (String id : bundledAdvancements().keySet()) {
            require(
                    MaidAdvancementScope.includes(ResourceId.parse(id)),
                    id + " is bundled for maids but excluded from their scope"
            );
        }
    }

    /**
     * 车万女仆本体那套进度讲的是玩家怎么和女仆相处，判给女仆没有意义，
     * 而且其中有用原版判定的条目（制作御币给 50 点经验），桥接会真的判给女仆。
     */
    private static void verifiesTouhouLittleMaidIsOutOfScope() {
        for (String path : List.of("base/craft_gohei", "base/tamed_maid", "give_smart_slab")) {
            require(
                    !MaidAdvancementScope.includes(
                            new ResourceId(
                                    "touhou_little_maid",
                                    path
                            )
                    ),
                    "touhou_little_maid:" + path + " should not count as a maid advancement"
            );
        }
        require(
                MaidAdvancementScope.includes(
                        new ResourceId(
                                "minecraft",
                                "story/mine_diamond"
                        )
                ),
                "Vanilla advancements must stay in scope"
        );
    }

    private static void verifiesProgressPacketRoundTrip() {
        Map<String, Criterion> criteria = new LinkedHashMap<>();
        criteria.put("done", new Criterion());
        criteria.put("pending", new Criterion());
        AdvancementProgress sent = new AdvancementProgress();
        sent.update(criteria, new String[][]{{"done"}, {"pending"}});
        require(sent.grantProgress("done"), "Granting an untouched criterion should report a change");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        sent.serializeToNetwork(buffer);
        AdvancementProgress received = AdvancementProgress.fromNetwork(buffer);
        require(buffer.readableBytes() == 0, "The progress stream left unread bytes behind");
        require(!received.isDone(), "A half-finished advancement must not arrive as completed");
        require(
                received.getCriterion("done") != null && received.getCriterion("done").isDone(),
                "The completed criterion did not survive the round trip"
        );
        require(
                received.getCriterion("pending") != null && !received.getCriterion("pending").isDone(),
                "The pending criterion did not survive the round trip"
        );
        require(
                sent.getCriterion("done").getObtained().equals(received.getCriterion("done").getObtained()),
                "The completion time did not survive the round trip"
        );
        // 线上格式只带每条 criterion 的状态，完成条件本身要客户端自己那份进度表补上。
        received.update(criteria, new String[][]{{"done"}, {"pending"}});
        require(
                sent.getPercent() == received.getPercent(),
                "Progress percentage changed once the requirements were restored"
        );
        verifiesCompletionNeedsRestoredRequirements();
    }

    /**
     * 界面的高亮与完成数都看 {@code isDone}，而它只认 requirements。
     * 收包后不补这一步的话，做完的进度在界面上看着像没做。
     */
    private static void verifiesCompletionNeedsRestoredRequirements() {
        Map<String, Criterion> criteria = Map.of("only", new Criterion());
        String[][] requirements = {{"only"}};
        AdvancementProgress sent = new AdvancementProgress();
        sent.update(criteria, requirements);
        sent.grantProgress("only");
        require(sent.isDone(), "A fully granted advancement should be done on the sending side");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        sent.serializeToNetwork(buffer);
        AdvancementProgress received = AdvancementProgress.fromNetwork(buffer);
        require(
                !received.isDone(),
                "The wire format unexpectedly carried the requirements, this check is now meaningless"
        );

        received.update(criteria, requirements);
        require(received.isDone(), "Restoring the requirements should mark the advancement done again");
        require(received.getPercent() == 1.0F, "A done advancement should read as one hundred percent");
    }

    private static void verifiesStripPaging() {
        // 女仆界面内容区 168 像素宽，是这套换算真正要伺候的尺寸。
        int stripWidth = 168;
        int perPage = AdvancementStripLayout.perPage(stripWidth);
        require(perPage == 8, "Expected eight root icons per page, got " + perPage);
        require(
                AdvancementStripLayout.slotX(0, perPage - 1) + AdvancementStripLayout.ICON_SIZE
                        <= AdvancementStripLayout.nextArrowX(0, stripWidth),
                "The last icon of a page overlaps the next-page arrow"
        );

        require(AdvancementStripLayout.pageCount(0, perPage) == 1, "An empty strip still needs one page");
        require(AdvancementStripLayout.pageCount(8, perPage) == 1, "Eight roots should fit on one page");
        require(AdvancementStripLayout.pageCount(9, perPage) == 2, "The ninth root should open a second page");
        require(AdvancementStripLayout.pageOf(8, perPage) == 1, "The ninth root lives on the second page");
        require(
                AdvancementStripLayout.rootIndex(1, 0, perPage, 9) == 8,
                "The first slot of the second page should be the ninth root"
        );
        require(
                AdvancementStripLayout.rootIndex(1, 1, perPage, 9) == AdvancementStripLayout.NO_SLOT,
                "Slots past the last root must stay empty"
        );

        int firstIconX = AdvancementStripLayout.slotX(0, 0);
        require(
                AdvancementStripLayout.slotAt(0, stripWidth, firstIconX) == 0,
                "The left edge of an icon should hit that icon"
        );
        require(
                AdvancementStripLayout.slotAt(0, stripWidth, firstIconX + AdvancementStripLayout.ICON_SIZE)
                        == AdvancementStripLayout.NO_SLOT,
                "The gap between icons must not select either of them"
        );
        require(
                AdvancementStripLayout.slotAt(0, stripWidth, 0) == AdvancementStripLayout.NO_SLOT,
                "The arrow column must not select an icon"
        );
        require(
                AdvancementStripLayout.inPreviousArrow(0, 0, 0, 0),
                "The previous-page arrow should accept its own top-left corner"
        );
        require(
                AdvancementStripLayout.inNextArrow(0, stripWidth, 0, stripWidth - 1, 0),
                "The next-page arrow should accept its own right edge"
        );
    }

    private static void verifiesTreeScrolling() {
        int viewport = 160;
        require(
                AdvancementTreeLayout.center(viewport, 0, 100) == 30.0D,
                "Centering does not match the vanilla formula"
        );
        require(
                AdvancementTreeLayout.clampScroll(-40.0D, 0, viewport, viewport) == -40.0D,
                "Content that fits the viewport should not be clamped"
        );
        require(
                AdvancementTreeLayout.clampScroll(20.0D, 0, 400, viewport) == 0.0D,
                "Dragging past the start should stop at the start"
        );
        require(
                AdvancementTreeLayout.clampScroll(-500.0D, 0, 400, viewport) == -240.0D,
                "Dragging past the end should stop with the content edge at the viewport edge"
        );
    }

    private static Map<String, JsonObject> bundledAdvancements() throws IOException {
        require(Files.isDirectory(ADVANCEMENTS), "Missing bundled advancements at " + ADVANCEMENTS);
        Map<String, JsonObject> advancements = new HashMap<>();
        try (Stream<Path> files = Files.list(ADVANCEMENTS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                advancements.put("tlm_companionship:maid/" + name, readJson(file));
            }
        }
        require(!advancements.isEmpty(), "No bundled advancements were found");
        return advancements;
    }

    private static JsonObject readJson(Path file) throws IOException {
        require(Files.isRegularFile(file), "Missing " + file);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
