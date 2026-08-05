package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.feature.orchestration.api.insight.MaidInsight;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side cache of the last insight received for each maid.
 *
 * <p>Entries expire. The server stops sending for a maid the moment it leaves
 * range or the lens is put away, and without a deadline the panel would keep
 * displaying whatever it was told last — a plan that finished minutes ago,
 * presented as if it were happening now.
 */
public final class ClientMaidInsights {
    /**
     * Slightly over twice the broadcast interval, so one dropped or delayed
     * packet does not make the panel flicker while a genuinely stale entry
     * still disappears quickly.
     */
    private static final long LIFETIME_MILLIS = 1_500L;

    private static final int MAX_TRACKED = 64;

    private static final Map<Integer, Entry> ENTRIES =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Integer, Entry> eldest
                ) {
                    return size() > MAX_TRACKED;
                }
            };

    private ClientMaidInsights() {
    }

    public static void accept(int maidEntityId, MaidInsight insight) {
        ENTRIES.put(
                maidEntityId,
                new Entry(insight, System.currentTimeMillis())
        );
    }

    /**
     * @return the last insight for this maid, or {@code null} when nothing
     *         fresh is known
     */
    public static MaidInsight get(int maidEntityId) {
        Entry entry = ENTRIES.get(maidEntityId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.receivedAt() > LIFETIME_MILLIS) {
            ENTRIES.remove(maidEntityId);
            return null;
        }
        return entry.insight();
    }

    /** Called on disconnect so a new world never sees the old one's maids. */
    public static void clear() {
        ENTRIES.clear();
    }

    private record Entry(MaidInsight insight, long receivedAt) {
    }
}
