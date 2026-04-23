package com.ftxeven.aircore.core.module.utility.command;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NickCommand implements TabExecutor {

    private final AirCore plugin;
    private static final String PERM = "aircore.command.nick";
    private static final String PERM_OTHERS = "aircore.command.nick.others";

    public NickCommand(AirCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p && !p.hasPermission(PERM)) {
            MessageUtil.send(p, "errors.no-permission", Map.of("permission", PERM));
            return true;
        }

        boolean hasOthers = sender.hasPermission(PERM_OTHERS) || !(sender instanceof Player);
        String usage = hasOthers
                ? plugin.commandConfig().getUsage("nick", "others", label)
                : plugin.commandConfig().getUsage("nick", label);

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Usage: " + usage);
                return true;
            }
            clearNick(p, p);
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Usage: " + usage);
                return true;
            }
            setNick(p, p, label, args[0]);
            return true;
        }

        if (args.length == 2) {
            if (!hasOthers) {
                MessageUtil.send((Player) sender, "errors.too-many-arguments", Map.of("usage", usage));
                return true;
            }

            OfflinePlayer target = resolve(sender, args[0]);
            if (target == null) return true;

            if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("off")) {
                clearNick(sender, target);
            } else {
                setNick(sender, target, label, args[1]);
            }
            return true;
        }

        if (sender instanceof Player p) {
            MessageUtil.send(p, "errors.too-many-arguments", Map.of("usage", usage));
        } else {
            sender.sendMessage("Too many arguments. Usage: " + usage);
        }
        return true;
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online;

        UUID uuid = plugin.database().records().uuidFromName(name);
        if (uuid != null) return Bukkit.getOfflinePlayer(uuid);

        if (sender instanceof Player p) MessageUtil.send(p, "errors.player-never-joined", Map.of());
        else sender.sendMessage("Player not found");
        return null;
    }

    private void clearNick(CommandSender sender, OfflinePlayer target) {
        plugin.utility().nicks().clear(target.getUniqueId());
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (sender instanceof Player p) {
            if (p.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(p, "utilities.nick.reset", Map.of());
            } else {
                MessageUtil.send(p, "utilities.nick.reset-for", Map.of("player", targetName));
                if (target.isOnline() && target.getPlayer() != null) {
                    MessageUtil.send(target.getPlayer(), "utilities.nick.reset-by", Map.of("player", senderName));
                }
            }
        } else {
            sender.sendMessage("Cleared nick for " + targetName);
            if (target.isOnline() && target.getPlayer() != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target.getPlayer(), "utilities.nick.reset-by", Map.of("player", senderName));
            }
        }
    }

    private void setNick(CommandSender sender, OfflinePlayer target, String label, String rawNick) {
        String sanitized = (sender instanceof Player p)
                ? plugin.chat().formats().sanitizeForChat(p, rawNick)
                : rawNick;

        if (sanitized == null || sanitized.isBlank()) {
            String usage = plugin.commandConfig().getUsage("nick", label);
            if (sender instanceof Player p) MessageUtil.send(p, "errors.incorrect-usage", Map.of("usage", usage));
            else sender.sendMessage("The nickname cannot be blank.");
            return;
        }

        int maxLength = plugin.config().nicknameMaxLength();
        if (sanitized.length() > maxLength) {
            if (sender instanceof Player p) {
                MessageUtil.send(p, "utilities.nick.error-too-long", Map.of(
                        "max", String.valueOf(maxLength),
                        "length", String.valueOf(sanitized.length())
                ));
            } else {
                sender.sendMessage("Nickname too long (" + sanitized.length() + "/" + maxLength + ")");
            }
            return;
        }

        plugin.utility().nicks().set(target.getUniqueId(), sanitized);
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String senderName = (sender instanceof Player p) ? p.getName() : String.valueOf(plugin.lang().get("general.console-name"));

        if (sender instanceof Player p) {
            if (p.getUniqueId().equals(target.getUniqueId())) {
                MessageUtil.send(p, "utilities.nick.set", Map.of("nick", sanitized));
            } else {
                MessageUtil.send(p, "utilities.nick.set-for", Map.of("player", targetName, "nick", sanitized));
                if (target.isOnline() && target.getPlayer() != null) {
                    MessageUtil.send(target.getPlayer(), "utilities.nick.set-by", Map.of("player", senderName, "nick", sanitized));
                }
            }
        } else {
            sender.sendMessage("Set nick for " + targetName + ": " + sanitized);
            if (target.isOnline() && target.getPlayer() != null && plugin.config().consoleToPlayerFeedback()) {
                MessageUtil.send(target.getPlayer(), "utilities.nick.set-by", Map.of("player", senderName, "nick", sanitized));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission(PERM)) return Collections.emptyList();

        if (sender.hasPermission(PERM_OTHERS) && args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(input))
                    .limit(20).toList();
        }

        return Collections.emptyList();
    }
}