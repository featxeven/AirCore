package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.core.command.CommandRegistry;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.List;

public final class AdminCommand implements BasicCommand {

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
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        dispatcher.dispatch(registry, commandSourceStack.getSender(), "aircore", args, () -> sendUsage(commandSourceStack.getSender()));
    }

    @Override
    public List<String> suggest(CommandSourceStack commandSourceStack, String[] args) {
        return dispatcher.tabComplete(registry, commandSourceStack.getSender(), args);
    }

    @Override
    public String permission() {
        return "aircore.admin";
    }

    private void sendUsage(org.bukkit.command.CommandSender sender) {
        plugin.messenger().send(sender, plugin.configs().lang().get("general.commands.usage"));
    }
}