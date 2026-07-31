package com.laixia.maidintelligence.feature.interaction.client.tracking;

import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class FaceTrackingGeometryCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Object, PlanEntry> PLANS = new WeakHashMap<>();
    private static final Map<Object, EnumSet<FaceGeometry.FailureReason>> REPORTED_FAILURES =
            new WeakHashMap<>();
    private static final Map<Object, EnumSet<FaceGeometry.Source>> REPORTED_SUCCESSES =
            new WeakHashMap<>();

    private FaceTrackingGeometryCache() {
    }

    public static synchronized <T> T getOrCompute(
            Object model,
            Object variantKey,
            Class<T> planType,
            Supplier<T> factory
    ) {
        PlanEntry cached = PLANS.get(model);
        if (cached != null
                && Objects.equals(cached.variantKey(), variantKey)
                && planType.isInstance(cached.plan())) {
            return planType.cast(cached.plan());
        }
        T created = factory.get();
        PLANS.put(model, new PlanEntry(variantKey, created));
        return created;
    }

    public static synchronized void invalidatePlan(Object model) {
        PLANS.remove(model);
    }

    public static synchronized void reportFailureOnce(
            Object model,
            String modelId,
            FaceGeometry.Source source,
            FaceGeometry.FailureReason reason
    ) {
        EnumSet<FaceGeometry.FailureReason> reasons = REPORTED_FAILURES.computeIfAbsent(
                model,
                ignored -> EnumSet.noneOf(FaceGeometry.FailureReason.class)
        );
        if (reasons.add(reason)) {
            LOGGER.debug(
                    "Maid face tracking skipped model {} through {}: {}",
                    modelId,
                    source,
                    reason
            );
        }
    }

    public static synchronized void reportSuccessOnce(
            Object model,
            String modelId,
            FaceGeometry.Source source,
            FaceGeometry.Key key,
            double confidence
    ) {
        EnumSet<FaceGeometry.Source> sources = REPORTED_SUCCESSES.computeIfAbsent(
                model,
                ignored -> EnumSet.noneOf(FaceGeometry.Source.class)
        );
        if (sources.add(source)) {
            LOGGER.debug(
                    "Maid face tracking selected {} for model {} through {} at confidence {}",
                    key,
                    modelId,
                    source,
                    String.format(java.util.Locale.ROOT, "%.3f", confidence)
            );
        }
    }

    public static synchronized void clear() {
        PLANS.clear();
        REPORTED_FAILURES.clear();
        REPORTED_SUCCESSES.clear();
    }

    private record PlanEntry(Object variantKey, Object plan) {
    }
}
