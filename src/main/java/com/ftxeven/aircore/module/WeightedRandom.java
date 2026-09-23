package com.ftxeven.aircore.module;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

public final class WeightedRandom {

    private WeightedRandom() {
    }

    public static <T> T pick(List<T> items, ToIntFunction<T> weight) {
        if (items.isEmpty()) {
            return null;
        }
        int total = 0;
        for (T item : items) {
            total += Math.max(0, weight.applyAsInt(item));
        }
        if (total <= 0) {
            return items.get(ThreadLocalRandom.current().nextInt(items.size()));
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (T item : items) {
            cumulative += Math.max(0, weight.applyAsInt(item));
            if (roll < cumulative) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }
}