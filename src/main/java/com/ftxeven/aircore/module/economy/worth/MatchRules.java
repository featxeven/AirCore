package com.ftxeven.aircore.module.economy.worth;

import java.util.List;
import java.util.Locale;

public record MatchRules(
        List<String> materials,
        List<String> names,
        List<String> lores,
        List<String> enchantments,
        List<String> nbtKeys,
        List<String> customModelData,
        List<String> itemModels,
        List<String> pluginItems
) {
    public static final MatchRules EMPTY = new MatchRules(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    public MatchRules {
        materials = List.copyOf(materials);
        names = lowercased(names);
        lores = lowercased(lores);
        enchantments = List.copyOf(enchantments);
        nbtKeys = List.copyOf(nbtKeys);
        customModelData = List.copyOf(customModelData);
        itemModels = List.copyOf(itemModels);
        pluginItems = List.copyOf(pluginItems);
    }

    public boolean isEmpty() {
        return materials.isEmpty() && names.isEmpty() && lores.isEmpty() && enchantments.isEmpty()
                && nbtKeys.isEmpty() && customModelData.isEmpty() && itemModels.isEmpty() && pluginItems.isEmpty();
    }

    private static List<String> lowercased(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }
}