package com.ftxeven.aircore.core;

import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class ShortcutCommand implements TabExecutor {

    private final String targetCommand;
    private final String baseCommand;
    private final String[] preArgs;

    public ShortcutCommand(String targetCommand) {
        this.targetCommand = targetCommand;
        String[] parts = targetCommand.split(" ", 2);
        this.baseCommand = parts[0].toLowerCase();
        this.preArgs = parts.length > 1 ? parts[1].split(" ") : new String[0];
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
        Command original = sender.getServer().getCommandMap().getCommand(baseCommand);
        if (!(original instanceof PluginCommand pluginCmd)) return Collections.emptyList();

        TabCompleter completer = pluginCmd.getTabCompleter();
        if (completer == null && pluginCmd.getExecutor() instanceof TabCompleter tc) completer = tc;
        if (completer == null) return Collections.emptyList();

        String[] fullArgs = new String[preArgs.length + args.length];
        System.arraycopy(preArgs, 0, fullArgs, 0, preArgs.length);
        System.arraycopy(args, 0, fullArgs, preArgs.length, args.length);

        List<String> completions = completer.onTabComplete(sender, pluginCmd, baseCommand, fullArgs);
        return completions != null ? completions : Collections.emptyList();
    }
}