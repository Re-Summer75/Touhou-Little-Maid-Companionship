package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.InsightReason;
import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire form for {@link MaidInsight}.
 *
 * <p>Every list and string is bounded on read. A decoder that trusts a length
 * it was sent will allocate whatever a malformed or hostile packet asks for,
 * and this one runs before anything has decided the packet is trustworthy.
 */
final class MaidInsightCodec {
    static final int MAX_ENTRIES = 8;
    private static final int MAX_ID = 128;
    private static final int MAX_TOPIC = 64;

    private static final InsightReason[] REASONS = InsightReason.values();

    private MaidInsightCodec() {
    }

    static void write(FriendlyByteBuf buffer, MaidInsight insight) {
        writeNullableId(buffer, insight.doing());
        buffer.writeUtf(insight.step(), MAX_TOPIC);
        buffer.writeVarInt(insight.doingForTicks());

        List<MaidInsight.Alternative> alternatives = insight.alternatives();
        buffer.writeVarInt(Math.min(alternatives.size(), MAX_ENTRIES));
        for (int index = 0;
             index < alternatives.size() && index < MAX_ENTRIES;
             index++) {
            MaidInsight.Alternative alternative = alternatives.get(index);
            buffer.writeUtf(alternative.intent().toString(), MAX_ID);
            buffer.writeFloat((float) alternative.score());
            buffer.writeByte(alternative.reason().ordinal());
            writeNullableId(buffer, alternative.blockingFact());
        }

        List<MaidInsight.Note> notes = insight.notes();
        buffer.writeVarInt(Math.min(notes.size(), MAX_ENTRIES));
        for (int index = 0;
             index < notes.size() && index < MAX_ENTRIES;
             index++) {
            buffer.writeUtf(notes.get(index).topic(), MAX_TOPIC);
            buffer.writeFloat((float) notes.get(index).value());
        }

        MaidInsight.Hunch hunch = insight.hunch();
        buffer.writeBoolean(hunch != null);
        if (hunch != null) {
            buffer.writeUtf(hunch.activity(), MAX_TOPIC);
            buffer.writeFloat((float) hunch.lift());
            buffer.writeVarInt(hunch.evidence());
        }
    }

    static MaidInsight read(FriendlyByteBuf buffer) {
        OrchestrationId doing = readNullableId(buffer);
        String step = buffer.readUtf(MAX_TOPIC);
        int elapsed = Math.max(0, buffer.readVarInt());

        int alternativeCount = clamp(buffer.readVarInt());
        List<MaidInsight.Alternative> alternatives =
                new ArrayList<>(alternativeCount);
        for (int index = 0; index < alternativeCount; index++) {
            OrchestrationId intent = readId(buffer);
            float score = buffer.readFloat();
            InsightReason reason = reason(buffer.readByte());
            OrchestrationId blocking = readNullableId(buffer);
            if (intent != null) {
                alternatives.add(new MaidInsight.Alternative(
                        intent,
                        Float.isFinite(score) ? score : 0.0D,
                        reason,
                        blocking
                ));
            }
        }

        int noteCount = clamp(buffer.readVarInt());
        List<MaidInsight.Note> notes = new ArrayList<>(noteCount);
        for (int index = 0; index < noteCount; index++) {
            String topic = buffer.readUtf(MAX_TOPIC);
            float value = buffer.readFloat();
            if (!topic.isBlank()) {
                notes.add(new MaidInsight.Note(
                        topic,
                        Float.isFinite(value) ? value : 0.0D
                ));
            }
        }

        MaidInsight.Hunch hunch = null;
        if (buffer.readBoolean()) {
            String activity = buffer.readUtf(MAX_TOPIC);
            float lift = buffer.readFloat();
            int evidence = Math.max(0, buffer.readVarInt());
            if (!activity.isBlank() && Float.isFinite(lift)) {
                // Clamped rather than rejected: the record refuses anything
                // outside [0, 1), and one odd float should cost the hunch line,
                // not the whole panel.
                double bounded = Math.max(0.0D, Math.min(0.999D, lift));
                hunch = new MaidInsight.Hunch(activity, bounded, evidence);
            }
        }
        return new MaidInsight(doing, step, elapsed, alternatives, notes, hunch);
    }

    private static int clamp(int count) {
        return Math.max(0, Math.min(count, MAX_ENTRIES));
    }

    private static InsightReason reason(byte ordinal) {
        return ordinal >= 0 && ordinal < REASONS.length
                ? REASONS[ordinal]
                : InsightReason.CONDITION;
    }

    private static void writeNullableId(
            FriendlyByteBuf buffer,
            OrchestrationId id
    ) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeUtf(id.toString(), MAX_ID);
        }
    }

    private static OrchestrationId readNullableId(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? readId(buffer) : null;
    }

    private static OrchestrationId readId(FriendlyByteBuf buffer) {
        try {
            return OrchestrationId.parse(buffer.readUtf(MAX_ID));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
