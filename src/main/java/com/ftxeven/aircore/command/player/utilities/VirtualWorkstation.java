package com.ftxeven.aircore.command.player.utilities;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

public enum VirtualWorkstation {

    CRAFTING_TABLE("craftingtable", player -> player.openWorkbench(null, true)),
    ENCHANTING_TABLE("enchantingtable", player -> player.openEnchanting(null, true)),
    ANVIL("anvil", player -> player.openAnvil(null, true)),
    CARTOGRAPHY_TABLE("cartography", player -> player.openCartographyTable(null, true)),
    LOOM("loom", player -> player.openLoom(null, true)),
    SMITHING_TABLE("smithingtable", player -> player.openSmithingTable(null, true)),
    STONECUTTER("stonecutter", player -> player.openStonecutter(null, true)),
    GRINDSTONE("grindstone", player -> player.openGrindstone(null, true));

    private final String commandKey;
    private final Consumer<Player> opener;

    VirtualWorkstation(String commandKey, Consumer<Player> opener) {
        this.commandKey = commandKey;
        this.opener = opener;
    }

    public String commandKey() { return commandKey; }

    public void open(Player player) {
        opener.accept(player);
    }
}