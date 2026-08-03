package com.laixia.maidintelligence.feature.behavior.domain.learning;

import java.util.ArrayList;
import java.util.List;

public record HabitForecast(
        List<Integer> dayBuckets,
        int samples,
        long lastUpdatedTick
) {
    private static final int BUCKET_COUNT = 24;
    private static final int MAX_SAMPLES = 1_000;

    public HabitForecast {
        dayBuckets = List.copyOf(dayBuckets);
        if (dayBuckets.size() != BUCKET_COUNT
                || dayBuckets.stream().anyMatch(count -> count < 0)
                || samples < 0
                || samples > MAX_SAMPLES
                || dayBuckets.stream().mapToInt(Integer::intValue).sum()
                != samples) {
            throw new IllegalArgumentException("Invalid habit forecast");
        }
    }

    public static HabitForecast initial() {
        return new HabitForecast(
                java.util.Collections.nCopies(BUCKET_COUNT, 0),
                0,
                0L
        );
    }

    public HabitForecast observe(int bucket, long gameTime) {
        if (bucket < 0 || bucket >= BUCKET_COUNT) {
            throw new IllegalArgumentException("Invalid habit bucket");
        }
        List<Integer> updated = new ArrayList<>(dayBuckets);
        int nextSamples = samples;
        if (nextSamples >= MAX_SAMPLES) {
            nextSamples = 0;
            for (int index = 0; index < updated.size(); index++) {
                int decayed = updated.get(index) / 2;
                updated.set(index, decayed);
                nextSamples += decayed;
            }
        }
        updated.set(bucket, updated.get(bucket) + 1);
        return new HabitForecast(updated, nextSamples + 1, gameTime);
    }

    public double probability(int bucket) {
        if (bucket < 0 || bucket >= BUCKET_COUNT) {
            return 0.0D;
        }
        return (dayBuckets.get(bucket) + 1.0D)
                / (samples + BUCKET_COUNT);
    }
}
