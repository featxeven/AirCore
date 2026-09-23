package com.ftxeven.aircore.core.command;

import com.ftxeven.aircore.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CommandExecution {

    private CommandExecution() {}

    public static void asConsole(String command) {
        Scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    public static void asPlayer(Player player, String command) {
        Scheduler.runTargetAware(player, () -> Bukkit.dispatchCommand(player, command));
    }
}