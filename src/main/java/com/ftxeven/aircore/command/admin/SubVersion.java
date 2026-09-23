package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import com.ftxeven.aircore.util.Version;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class SubVersion implements CommandHandler {

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;

    public SubVersion(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "version"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public String usage() { return "/aircore version"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String current = plugin.getPluginMeta().getVersion();
        messenger.send(sender, configs.lang().get("general.commands.version"), Map.of("version", current));

        if (Version.isOutdated()) {
            messenger.send(sender, configs.lang().get("general.commands.outdated"),
                    Map.of("current", current, "latest", Version.getLatest()));
        }
    }
}