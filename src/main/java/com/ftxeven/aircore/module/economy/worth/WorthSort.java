package com.ftxeven.aircore.module.economy.worth;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public enum WorthSort {

    DEFAULT(null),
    HIGHEST_VALUE(Comparator.comparingDouble(WorthCalculator.Entry::worth).reversed()),
    LOWEST_VALUE(Comparator.comparingDouble(WorthCalculator.Entry::worth));

    private final @Nullable Comparator<WorthCalculator.Entry> comparator;

    WorthSort(@Nullable Comparator<WorthCalculator.Entry> comparator) {
        this.comparator = comparator;
    }

    // List.sort is stable, so equal worths keep their items.yml order
    public void sort(List<WorthCalculator.Entry> entries) {
        if (comparator != null) {
            entries.sort(comparator);
        }
    }
}