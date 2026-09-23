package com.ftxeven.aircore.gui.config;

import com.ftxeven.aircore.core.gui.config.ItemConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LayoutConfig(
        @Nullable Cycler filters,
        @Nullable Cycler sorts,
        @Nullable Set<Integer> itemSlots,
        @Nullable Set<Integer> storageSlots,
        @Nullable Set<Integer> hotbarSlots,
        @Nullable Set<Integer> armorSlots,
        @Nullable Set<Integer> offhandSlots,
        @Nullable Set<Integer> enderchestSlots,
        @Nullable Set<Integer> shulkerSlots,
        @Nullable Set<Integer> previewSlots,
        @Nullable Set<Integer> homeSlots,
        @Nullable Set<Integer> iconSlots,
        @Nullable Set<Integer> entrySlots,
        @Nullable Set<Integer> disposalSlots,
        @Nullable Set<Integer> sellSlots,
        @Nullable ItemConfig.Template home,
        @Nullable ItemConfig.Template icon,
        @Nullable ItemConfig.Template entry,
        @Nullable String leaderboard,
        @Nullable AvailableSlots availableSlots
) {
    public static final LayoutConfig EMPTY = new LayoutConfig(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null);

    public static final Set<String> TEMPLATE_KEYS = Set.of("home", "icon", "entry");

    public LayoutConfig {
        itemSlots = itemSlots != null ? orderedCopy(itemSlots) : null;
        storageSlots = storageSlots != null ? orderedCopy(storageSlots) : null;
        hotbarSlots = hotbarSlots != null ? orderedCopy(hotbarSlots) : null;
        armorSlots = armorSlots != null ? orderedCopy(armorSlots) : null;
        offhandSlots = offhandSlots != null ? orderedCopy(offhandSlots) : null;
        enderchestSlots = enderchestSlots != null ? orderedCopy(enderchestSlots) : null;
        shulkerSlots = shulkerSlots != null ? orderedCopy(shulkerSlots) : null;
        previewSlots = previewSlots != null ? orderedCopy(previewSlots) : null;
        homeSlots = homeSlots != null ? orderedCopy(homeSlots) : null;
        iconSlots = iconSlots != null ? orderedCopy(iconSlots) : null;
        entrySlots = entrySlots != null ? orderedCopy(entrySlots) : null;
        disposalSlots = disposalSlots != null ? orderedCopy(disposalSlots) : null;
        sellSlots = sellSlots != null ? orderedCopy(sellSlots) : null;
    }

    private static Set<Integer> orderedCopy(Set<Integer> slots) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(slots));
    }

    public Cycler filters() { return filters != null ? filters : Cycler.EMPTY; }
    public Cycler sorts() { return sorts != null ? sorts : Cycler.EMPTY; }

    public Set<Integer> itemSlots() { return itemSlots != null ? itemSlots : Set.of(); }
    public Set<Integer> storageSlots() { return storageSlots != null ? storageSlots : Set.of(); }
    public Set<Integer> hotbarSlots() { return hotbarSlots != null ? hotbarSlots : Set.of(); }
    public Set<Integer> armorSlots() { return armorSlots != null ? armorSlots : Set.of(); }
    public Set<Integer> offhandSlots() { return offhandSlots != null ? offhandSlots : Set.of(); }
    public Set<Integer> enderchestSlots() { return enderchestSlots != null ? enderchestSlots : Set.of(); }
    public Set<Integer> shulkerSlots() { return shulkerSlots != null ? shulkerSlots : Set.of(); }
    public Set<Integer> previewSlots() { return previewSlots != null ? previewSlots : Set.of(); }
    public Set<Integer> homeSlots() { return homeSlots != null ? homeSlots : Set.of(); }
    public Set<Integer> iconSlots() { return iconSlots != null ? iconSlots : Set.of(); }
    public Set<Integer> entrySlots() { return entrySlots != null ? entrySlots : Set.of(); }
    public Set<Integer> disposalSlots() { return disposalSlots != null ? disposalSlots : Set.of(); }
    public Set<Integer> sellSlots() { return sellSlots != null ? sellSlots : Set.of(); }

    public AvailableSlots availableSlots() { return availableSlots != null ? availableSlots : AvailableSlots.DISABLED; }

    public record Cycler(List<String> excluded, Map<String, Format> format) {

        public static final Cycler EMPTY = new Cycler(List.of(), Map.of());

        public Cycler {
            excluded = List.copyOf(excluded);
            format = Map.copyOf(format);
        }

        public boolean isExcluded(String key) {
            return excluded.stream().anyMatch(key::equalsIgnoreCase);
        }

        public Format format(String key, Format fallback) {
            return format.getOrDefault(key, fallback);
        }

        public record Format(String selected, String unselected) {
            public static final Format FILTER_DEFAULT = new Format(
                    "<aqua>> <white>%name% <gray>(%count%)",
                    "<dark_gray>  %name% <gray>(%count%)");

            public static final Format SORT_DEFAULT = new Format(
                    "<aqua>> <white>%name%",
                    "<dark_gray>  %name%");
        }
    }

    public record AvailableSlots(boolean enabled, ItemConfig.Template template) {
        public static final AvailableSlots DISABLED = new AvailableSlots(false, new ItemConfig.Template(ItemConfig.Fields.EMPTY, List.of()));
    }
}