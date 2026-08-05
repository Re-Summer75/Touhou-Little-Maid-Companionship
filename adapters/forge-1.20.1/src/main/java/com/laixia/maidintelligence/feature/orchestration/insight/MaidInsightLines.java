package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.InsightReason;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns one insight into the lines the panel shows.
 *
 * <p>All of the wording lives here rather than in the summary, so the decision
 * about <em>what</em> is worth saying stays testable without a locale and the
 * decision about <em>how</em> to say it stays translatable.
 *
 * <p>Every id falls back to its own path when no translation exists. A data
 * pack can add an intent this mod has never heard of, and the panel should read
 * a little raw rather than print a translation key at the player.
 */
final class MaidInsightLines {
    private static final int TICKS_PER_SECOND = 20;

    private MaidInsightLines() {
    }

    static List<Component> build(MaidInsight insight) {
        List<Component> lines = new ArrayList<>();
        lines.add(headline(insight));
        if (insight.busy() && !insight.step().isBlank()) {
            lines.add(gui("step", Component.literal(insight.step()))
                    .withStyle(ChatFormatting.GRAY));
        }
        for (MaidInsight.Alternative alternative : insight.alternatives()) {
            lines.add(notDoing(alternative));
        }
        for (MaidInsight.Note note : insight.notes()) {
            lines.add(gui("note", named("note", note.topic()))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (insight.hasHunch()) {
            lines.add(hunch(insight.hunch()));
        }
        return List.copyOf(lines);
    }

    private static Component headline(MaidInsight insight) {
        if (!insight.busy()) {
            return gui("idle").withStyle(ChatFormatting.GRAY);
        }
        MutableComponent line = gui("doing", intentName(insight.doing()))
                .withStyle(ChatFormatting.GREEN);
        int seconds = insight.doingForTicks() / TICKS_PER_SECOND;
        if (seconds > 0) {
            line.append(Component.literal(" "))
                    .append(gui("elapsed", Component.literal(
                            Integer.toString(seconds)
                    )).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static Component notDoing(MaidInsight.Alternative alternative) {
        MutableComponent line =
                gui("not_doing", intentName(alternative.intent()))
                        .withStyle(ChatFormatting.YELLOW);
        return line.append(Component.literal(" "))
                .append(reason(alternative).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent reason(MaidInsight.Alternative alternative) {
        InsightReason reason = alternative.reason();
        if (reason != InsightReason.CONDITION) {
            return gui("reason." + reason.name().toLowerCase(
                    java.util.Locale.ROOT
            ));
        }
        OrchestrationId fact = alternative.blockingFact();
        if (fact == null) {
            return gui("reason.condition_unknown");
        }
        return gui("reason.condition", factName(fact));
    }

    private static Component hunch(MaidInsight.Hunch hunch) {
        return gui("hunch", named("activity", hunch.activity()))
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(" "))
                .append(gui(
                        "hunch.evidence",
                        Component.literal(Integer.toString(hunch.evidence()))
                ).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Ids are {@code namespace:kind/path}; the display key drops the namespace
     * and the kind, so {@code tlm_companionship:intent/wander_return} looks up
     * {@code intent.tlm_companionship.wander_return}.
     */
    private static Component intentName(OrchestrationId intent) {
        return named("intent", tail(intent));
    }

    private static Component factName(OrchestrationId fact) {
        String key = ModResources.translationKey("fact", tail(fact));
        return Language.getInstance().has(key)
                ? Component.translatable(key)
                : Component.translatable(
                        ModResources.translationKey("fact", "unknown"),
                        Component.literal(tail(fact))
                );
    }

    private static Component named(String category, String path) {
        String key = ModResources.translationKey(category, path);
        return Language.getInstance().has(key)
                ? Component.translatable(key)
                : Component.literal(path);
    }

    private static String tail(OrchestrationId id) {
        String path = id.path();
        int slash = path.indexOf('/');
        return slash < 0 || slash + 1 >= path.length()
                ? path
                : path.substring(slash + 1).replace('/', '.');
    }

    private static MutableComponent gui(String path, Component... arguments) {
        String key = ModResources.translationKey("gui", "insight." + path);
        return arguments.length == 0
                ? Component.translatable(key)
                : Component.translatable(key, (Object[]) arguments);
    }
}
