package com.ftxeven.aircore.transfer.importer.model;

import com.ftxeven.aircore.model.Position;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ImportedServerData(List<NamedPosition> warps, List<NamedPosition> spawns, List<Kit> kits) {

    public static ImportedServerData empty() {
        return new ImportedServerData(List.of(), List.of(), List.of());
    }

    public record NamedPosition(String key, Position position) {}

    public record Kit(String name, List<ItemStack> items, boolean oneTime, @Nullable Integer cooldownSeconds) {}
}