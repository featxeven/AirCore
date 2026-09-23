package com.ftxeven.aircore.migration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MigrationReport {

    public enum Category {
        PLAYERS("players"),
        NICKNAMES("nicknames"),
        HOMES("homes"),
        BLOCKS("blocked players"),
        INVENTORIES("inventories"),
        KIT_CLAIMS("kit claims"),
        WARPS("warps"),
        SPAWNS("spawn points"),
        KITS("kits");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final int MAX_WARNINGS = 500;

    private final Map<Category, int[]> counters = new EnumMap<>(Category.class);
    private final List<String> warnings = new ArrayList<>();
    private int droppedWarnings;

    public void migrated(Category category) {
        counter(category)[0]++;
    }

    public void skipped(Category category) {
        counter(category)[1]++;
    }

    public void failed(Category category, String warning) {
        counter(category)[2]++;
        warn(warning);
    }

    public void warn(String warning) {
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(warning);
        } else {
            droppedWarnings++;
        }
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public int droppedWarnings() {
        return droppedWarnings;
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Category category : Category.values()) {
            int[] values = counters.get(category);
            if (values == null) {
                continue;
            }
            lines.add("  " + category.label() + ": " + values[0] + " migrated, "
                    + values[1] + " skipped, " + values[2] + " failed");
        }
        return lines;
    }

    private int[] counter(Category category) {
        return counters.computeIfAbsent(category, key -> new int[3]);
    }
}