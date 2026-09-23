package com.ftxeven.aircore.migration.model;

import com.ftxeven.aircore.model.Position;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record LegacyServerData(List<NamedPosition> warps, List<NamedPosition> spawns, List<Kit> kits) {

    public static LegacyServerData empty() {
        return new LegacyServerData(List.of(), List.of(), List.of());
    }

    public record NamedPosition(String key, Position position) {}

    public record Kit(String name, List<ItemStack> items, boolean oneTime, @Nullable Integer cooldownSeconds) {}
}