package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class SubReload implements CommandHandler {

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;

    public SubReload(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "reload"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public String usage() { return "/aircore reload"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        long start = System.currentTimeMillis();

        if (!configs.reload()) {
            messenger.send(sender, configs.lang().get("errors.reload-failed"));
            return;
        }

        plugin.modules().reload();
        plugin.services().reload();

        if (!plugin.guis().reload()) {
            messenger.send(sender, configs.lang().get("errors.reload-failed"));
            return;
        }

        long elapsed = System.currentTimeMillis() - start;
        messenger.send(sender, configs.lang().get("general.commands.reload"), Map.of("time", String.valueOf(elapsed)));
    }
}