package com.ftxeven.aircore.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ShortcutCommand implements TabExecutor {

    private final String targetCommand;

    public ShortcutCommand(String targetCommand) {
        this.targetCommand = targetCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             String @NotNull [] args) {
        String full = args.length > 0 ? targetCommand + " " + String.join(" ", args) : targetCommand;
        sender.getServer().dispatchCommand(sender, full);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command cmd,
                                      @NotNull String label,
                                      String @NotNull [] args) {
        return Collections.emptyList();
    }
}