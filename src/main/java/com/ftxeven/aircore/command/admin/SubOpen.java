package com.ftxeven.aircore.command.admin;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.command.CommandDispatcher;
import com.ftxeven.aircore.command.CommandHandler;
import com.ftxeven.aircore.config.ConfigManager;
import com.ftxeven.aircore.core.gui.GuiManager;
import com.ftxeven.aircore.core.gui.GuiSession;
import com.ftxeven.aircore.core.gui.OpenOptions;
import com.ftxeven.aircore.core.gui.action.ActionTokens;
import com.ftxeven.aircore.gui.action.GuiActions;
import com.ftxeven.aircore.permission.Permissions;
import com.ftxeven.aircore.util.Messenger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SubOpen implements CommandHandler {

    private final AirCore plugin;
    private final Messenger messenger;
    private final ConfigManager configs;

    public SubOpen(AirCore plugin) {
        this.plugin = plugin;
        this.messenger = plugin.messenger();
        this.configs = plugin.configs();
    }

    @Override
    public String name() { return "open"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 2; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/aircore open <gui-id> <player> [target] [args]"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        String guiId = args[0];
        GuiManager guis = plugin.guis().guis();

        if (!GuiActions.guiEnabled(guis, guiId)) {
            messenger.send(sender, configs.lang().get("errors.access.gui-not-found"), Map.of("gui", guiId));
            return;
        }

        Player viewer = Bukkit.getPlayerExact(args[1]);
        if (viewer == null) {
            messenger.send(sender, configs.lang().get("errors.access.player-not-found"), Map.of("player", args[1]));
            return;
        }

        Map<String, Object> attributes = new HashMap<>();
        int tokenStart = 2;

        if (args.length > 2 && !args[2].contains(":")) {
            String typedName = args[2];
            OfflinePlayer target = Bukkit.getOfflinePlayer(typedName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                messenger.send(sender, configs.lang().get("errors.access.player-never-joined"), Map.of("player", typedName));
                return;
            }
            attributes.put(GuiSession.ATTR_TARGET, target.getUniqueId());
            tokenStart = 3;
        }

        if (args.length > tokenStart) {
            String rawTokens = String.join(" ", Arrays.copyOfRange(args, tokenStart, args.length));
            attributes.putAll(ActionTokens.parse(rawTokens));
        }

        guis.open(viewer, guiId, new HashMap<>(), OpenOptions.entry(attributes, List.of()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandDispatcher.filterPrefix(List.copyOf(plugin.guis().guis().ids()), args[0]);
        }
        if (args.length == 2 || args.length == 3) {
            List<String> onlineNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return CommandDispatcher.filterPrefix(onlineNames, args[args.length - 1]);
        }
        return List.of();
    }
}