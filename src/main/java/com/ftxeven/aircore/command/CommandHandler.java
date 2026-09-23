package com.ftxeven.aircore.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface CommandHandler {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    default boolean enabled() {
        return true;
    }

    default String permission() {
        return null;
    }

    default boolean hasPermission(CommandSender sender) {
        String permission = permission();
        return permission == null || sender.hasPermission(permission);
    }

    default boolean isVisibleTo(CommandSender sender) {
        return enabled() && hasPermission(sender);
    }

    default boolean playerOnly() {
        return false;
    }

    default int minArgs() {
        return 0;
    }

    default int minArgs(CommandSender sender) {
        return minArgs();
    }

    default int maxArgs() {
        return 0;
    }

    default int maxArgs(CommandSender sender) {
        return maxArgs();
    }

    default int minArgs(CommandSender sender, String[] args) {
        return minArgs(sender);
    }

    default int maxArgs(CommandSender sender, String[] args) {
        return maxArgs(sender);
    }

    String usage();

    default String usage(CommandSender sender) {
        return usage();
    }

    void execute(CommandSender sender, String label, String subLabel, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}