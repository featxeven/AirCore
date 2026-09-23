package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.core.command.CommandRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final CommandRegistry registry;
    private final CommandDispatcher dispatcher;
    private final AirCore plugin;

    public AdminCommand(AirCore plugin) {
        this.plugin = plugin;

        this.registry = new CommandRegistry()
                .register(new SubReload(plugin))
                .register(new SubVersion(plugin))
                .register(new SubPlaceholder(plugin))
                .register(new SubAnnouncement(plugin))
                .register(new SubVariable(plugin))
                .register(new SubOpen(plugin))
                .register(new SubMigrate(plugin));
        this.dispatcher = new CommandDispatcher(plugin.messenger(), plugin.configs(), plugin.services());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        dispatcher.dispatch(registry, sender, label, args, () -> sendUsage(sender));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return dispatcher.tabComplete(registry, sender, args);
    }

    private void sendUsage(CommandSender sender) {
        plugin.messenger().send(sender, plugin.configs().lang().get("general.commands.usage"));
    }
}